package com.conversa.app.core.data.anexos

import com.conversa.app.core.model.AnexoDaConversa
import com.conversa.app.core.model.DirecaoAnexos
import com.conversa.app.core.model.FiltroAnexos
import com.conversa.app.core.model.LIMITE_ANEXOS
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.DespachanteEs
import com.conversa.app.core.network.dto.AnexoItemDto
import com.conversa.app.core.network.dto.IncluirAnexoRequisicao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Arquivo a enviar, lido em fluxo (nunca inteiro na memória). */
interface FonteArquivo {
    val nome: String
    val tamanho: Long
    val mime: String?

    /** Abre de novo a cada chamada (o hash e o envio leem o arquivo duas vezes). */
    fun abrir(): InputStream
}

/** Anexo já no servidor: o `identificador` vai no `conteudo` da mensagem (contrato §8.2, passo 6). */
data class AnexoEnviado(
    val id: Long,
    val identificador: String,
    val tipo: TipoConteudo,
    val nome: String,
    val extensao: String,
    val tamanho: Long,
)

/** O arquivo passa de 1 GiB (limite do servidor e do nginx). */
class ArquivoGrandeDemaisException : IOException("Arquivo maior que 1 GiB")

/** SHA-256 em hexadecimal, lendo aos pedaços (o web faz o mesmo: é o `identificador`, §8.2). */
fun calcularSha256(entrada: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(TAMANHO_BLOCO)
    while (true) {
        val lidos = entrada.read(buffer)
        if (lidos < 0) break
        digest.update(buffer, 0, lidos)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Extensão do nome, minúscula e com no máximo 10 caracteres (coluna `extensao`, §8.1). */
fun extensaoDe(nome: String): String = nome.substringAfterLast('.', "").lowercase().take(LIMITE_EXTENSAO)

private const val TAMANHO_BLOCO = 64 * 1024
private const val LIMITE_EXTENSAO = 10

/**
 * Anexos (ANX-02, ANX-14): envio com deduplicação e URLs assinadas de leitura.
 *
 * Envio (§8.2): SHA-256 → `PUT /anexo` → se já existe, pronto; senão `PUT` dos bytes na
 * URL assinada (300 s) → `POST /anexo/confirmar?identificador=`. Se a URL vencer no
 * meio (403 do MinIO), pede outra uma vez.
 *
 * Leitura (§8.3): `GET /anexo?identificador=` → URL que vale 600 s, guardada pelo
 * identificador (o conteúdo nunca muda) até um pouco antes de vencer.
 */
@Singleton
class AnexosRepositorio @Inject constructor(
    private val api: ConversaApi,
    okHttp: OkHttpClient,
    private val relogio: Clock,
    @DespachanteEs private val es: CoroutineDispatcher,
) {
    // Mesmo OkHttp do app (TLS, log), com tempo para arquivos grandes. O token não vai para
    // a URL assinada (o AutenticacaoInterceptor só manda para /api/).
    private val cliente = okHttp.newBuilder()
        .writeTimeout(10, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun enviar(
        fonte: FonteArquivo,
        tipo: TipoConteudo,
        aoProgresso: (enviados: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<AnexoEnviado> = withContext(es) {
        if (fonte.tamanho > LIMITE_BYTES) return@withContext Result.failure(ArquivoGrandeDemaisException())
        val identificador = try {
            fonte.abrir().use(::calcularSha256)
        } catch (e: IOException) {
            return@withContext Result.failure(ErroApi.SemConexao(e))
        }
        val extensao = extensaoDe(fonte.nome)
        val pedido = IncluirAnexoRequisicao(
            identificador = identificador,
            tipo = tipo.codigo,
            nome = fonte.nome.take(LIMITE_NOME).ifBlank { null },
            extensao = extensao.ifBlank { null },
            tamanho = fonte.tamanho,
        )
        var tentativa = 0
        while (true) {
            val resposta = chamarApi { api.incluirAnexo(pedido) }.getOrElse { return@withContext Result.failure(it) }
            val enviado = AnexoEnviado(resposta.id, identificador, tipo, fonte.nome, extensao, fonte.tamanho)
            if (resposta.existe) {
                // Já está no servidor (mesmo conteúdo): nada a subir; a URL devolvida é de leitura.
                guardarUrl(identificador, resposta.uploadUrl)
                aoProgresso(fonte.tamanho, fonte.tamanho)
                return@withContext Result.success(enviado)
            }
            val codigo = try {
                subir(resposta.uploadUrl, fonte, aoProgresso)
            } catch (e: IOException) {
                return@withContext Result.failure(ErroApi.SemConexao(e))
            }
            when {
                codigo in 200..299 -> {
                    return@withContext chamarApi { api.confirmarAnexo(identificador) }.map { enviado }
                }
                // URL vencida (vale 300 s): pede outra, uma vez.
                codigo == 403 && tentativa == 0 -> tentativa++
                else -> return@withContext Result.failure(ErroApi.Servidor(codigo, "Falha no envio do arquivo ($codigo)"))
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("inalcançável")
    }

    private fun subir(url: String, fonte: FonteArquivo, aoProgresso: (Long, Long) -> Unit): Int {
        val corpo = object : RequestBody() {
            override fun contentType(): MediaType? = (fonte.mime ?: "application/octet-stream").toMediaTypeOrNull()

            override fun contentLength(): Long = fonte.tamanho

            override fun writeTo(sink: BufferedSink) {
                fonte.abrir().source().use { origem ->
                    var enviados = 0L
                    while (true) {
                        val lidos = origem.read(sink.buffer, TAMANHO_BLOCO.toLong())
                        if (lidos < 0) break
                        sink.emitCompleteSegments()
                        enviados += lidos
                        aoProgresso(enviados, fonte.tamanho)
                    }
                }
            }
        }
        return cliente.newCall(Request.Builder().url(url).put(corpo).build()).execute().use { it.code }
    }

    // --- URLs de leitura (ANX-14) ---

    private val urls = ConcurrentHashMap<String, Pair<String, Instant>>()

    /** URL assinada para baixar/mostrar, do cache enquanto vale. */
    suspend fun url(identificador: String): Result<String> {
        val guardada = urls[identificador]
        if (guardada != null && relogio.instant().isBefore(guardada.second)) return Result.success(guardada.first)
        return chamarApi { api.urlAnexo(identificador) }.map { it.url.also { url -> guardarUrl(identificador, url) } }
    }

    /** A imagem falhou (403/expirada): a próxima chamada a [url] busca outra. */
    fun esquecerUrl(identificador: String) {
        urls.remove(identificador)
    }

    // --- Anexos da conversa (8.6, ANX-13) ---

    /**
     * `GET /anexos` de uma conversa, do mais novo para o mais antigo; [antes] = o último
     * `anexoId` da página anterior. As URLs assinadas que vêm junto já entram no cache.
     */
    suspend fun daConversa(conversaId: Long, direcao: DirecaoAnexos, filtro: FiltroAnexos, antes: Long = 0): Result<List<AnexoDaConversa>> =
        chamarApi {
            api.anexos(conversaId = conversaId, direcao = direcao.chave, tipos = filtro.parametro, antes = antes, limite = LIMITE_ANEXOS)
        }.map { lista ->
            lista.map { item ->
                item.url?.let { guardarUrl(item.identificador, it) }
                item.paraModelo()
            }
        }

    private fun guardarUrl(identificador: String, url: String) {
        urls[identificador] = url to relogio.instant().plusSeconds(VALIDADE_URL_S - MARGEM_URL_S)
    }

    /** Fim da sessão. */
    fun limpar() = urls.clear()

    companion object {
        /** 1 GiB (contrato §8.2). */
        const val LIMITE_BYTES = 1024L * 1024 * 1024
        private const val LIMITE_NOME = 255
        private const val VALIDADE_URL_S = 600L
        private const val MARGEM_URL_S = 60L
    }
}

internal fun AnexoItemDto.paraModelo() = AnexoDaConversa(
    anexoId = anexoId,
    identificador = identificador,
    nome = nome.orEmpty(),
    extensao = extensao.orEmpty(),
    tamanho = tamanho,
    criadoEm = criadoEm,
    tipo = TipoConteudo.de(tipo),
    mensagemId = mensagemId ?: 0,
    conversaId = conversaId ?: 0,
    autorNome = autorNome.orEmpty(),
)
