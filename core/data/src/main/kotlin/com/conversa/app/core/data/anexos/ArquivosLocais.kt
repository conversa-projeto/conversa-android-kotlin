package com.conversa.app.core.data.anexos

import android.content.Context
import com.conversa.app.core.network.di.DespachanteEs
import com.conversa.app.core.network.http.ErroApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

/**
 * Arquivos de anexo no aparelho (ANX-09):
 * - `cache/anexos/<identificador>/<nome>`: baixados para abrir (o conteúdo de um
 *   identificador nunca muda, então baixa uma vez só);
 * - `cache/camera/`: fotos tiradas pelo app antes de enviar.
 * Os dois ficam no cache do app (o Android pode limpar) e são compartilhados pelo
 * `FileProvider` (`res/xml/caminhos_arquivos.xml`).
 */
@Singleton
class ArquivosLocais @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val anexos: AnexosRepositorio,
    okHttp: OkHttpClient,
    @DespachanteEs private val es: CoroutineDispatcher,
) {
    private val cliente = okHttp

    val pastaCamera: File get() = File(contexto.cacheDir, "camera").apply { mkdirs() }

    /**
     * Baixa o anexo (se ainda não está no cache) e devolve o arquivo. Grava num `.part`
     * e só renomeia no fim: um download interrompido nunca vira arquivo "pronto".
     * URL vencida (403 do MinIO): pede outra e tenta mais uma vez (ANX-14).
     */
    suspend fun baixar(identificador: String, nome: String): Result<File> = withContext(es) {
        val pasta = File(contexto.cacheDir, "anexos/${identificadorSeguro(identificador)}").apply { mkdirs() }
        val destino = File(pasta, nomeSeguro(nome))
        if (destino.exists() && destino.length() > 0) return@withContext Result.success(destino)
        val parcial = File(pasta, destino.name + ".part")
        var resultado = tentarBaixar(identificador, parcial, destino)
        if ((resultado.exceptionOrNull() as? ErroApi.Servidor)?.status == HTTP_PROIBIDO) {
            anexos.esquecerUrl(identificador)
            resultado = tentarBaixar(identificador, parcial, destino)
        }
        resultado
    }

    private suspend fun tentarBaixar(identificador: String, parcial: File, destino: File): Result<File> {
        val url = anexos.url(identificador).getOrElse { return Result.failure(it) }
        return try {
            cliente.newCall(Request.Builder().url(url).build()).execute().use { resposta ->
                if (!resposta.isSuccessful) return Result.failure(ErroApi.Servidor(resposta.code, "Falha ao baixar (${resposta.code})"))
                parcial.sink().buffer().use { it.writeAll(resposta.body.source()) }
            }
            if (!parcial.renameTo(destino)) throw IOException("Não foi possível salvar o arquivo")
            Result.success(destino)
        } catch (e: IOException) {
            parcial.delete()
            Result.failure(ErroApi.SemConexao(e))
        }
    }

    /** Fim da sessão: apaga os anexos baixados (podem ser de outra conta). */
    fun limpar() {
        File(contexto.cacheDir, "anexos").deleteRecursively()
        File(contexto.cacheDir, "camera").deleteRecursively()
    }

    private fun identificadorSeguro(identificador: String) = identificador.filter { it.isLetterOrDigit() }.take(64).ifBlank { "x" }

    private companion object {
        const val HTTP_PROIBIDO = 403
    }
}
