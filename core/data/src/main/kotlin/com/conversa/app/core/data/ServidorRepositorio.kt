package com.conversa.app.core.data

import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.json.ConversaJson
import com.conversa.app.core.network.tls.CertificadoConfiadoProvider
import com.conversa.app.core.network.tls.ConfiancaServidor
import com.conversa.app.core.network.tls.impressaoSha256
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resultado do botão "Testar conexão". */
sealed interface ResultadoTesteServidor {
    data object Ok : ResultadoTesteServidor

    /** Respondeu, mas não como o servidor Conversa (ex.: outro site, página do nginx). */
    data object NaoEhConversa : ResultadoTesteServidor

    /**
     * Certificado não confiável. [impressao]: a SHA-256 do certificado recebido, quando a pessoa
     * pode escolher confiar nele (só no debug); `null` no release ou se nem chegou a recebê-lo.
     */
    data class CertificadoInvalido(val impressao: String? = null) : ResultadoTesteServidor

    data object HostNaoEncontrado : ResultadoTesteServidor

    /** nginx no ar mas a API fora (502/503/504). */
    data object Indisponivel : ResultadoTesteServidor

    data class SemConexao(val detalhe: String?) : ResultadoTesteServidor
}

/**
 * Endereço do servidor configurado no app. Fonte da [ServerConfig] usada pelo
 * Retrofit (troca de host em tempo de execução) e pelo WebSocket, e do certificado
 * próprio aceito para ele ([CertificadoConfiadoProvider], só no debug).
 */
@Singleton
class ServidorRepositorio @Inject constructor(
    private val preferencias: PreferenciasStore,
    private val okHttp: dagger.Lazy<OkHttpClient>,
    @EscopoAplicacao escopo: CoroutineScope,
) : ServerConfigProvider,
    CertificadoConfiadoProvider {
    private val _atual = MutableStateFlow<ServerConfig?>(null)
    override val atual: StateFlow<ServerConfig?> = _atual.asStateFlow()

    private val _impressao = MutableStateFlow<String?>(null)
    override val impressao: StateFlow<String?> = _impressao.asStateFlow()

    private val _carregado = MutableStateFlow(false)

    /** `true` depois da primeira leitura do DataStore (a tela inicial espera por isso). */
    val carregado: StateFlow<Boolean> = _carregado.asStateFlow()

    init {
        escopo.launch {
            combine(preferencias.enderecoServidor, preferencias.certificadoServidor) { endereco, certificado -> endereco to certificado }
                .collect { (endereco, certificado) ->
                    _impressao.value = certificado
                    _atual.value = endereco?.let(ServerConfig::aPartirDe)
                    _carregado.value = true
                }
        }
    }

    /** [certificado]: a impressão do certificado próprio aceito para este servidor (ou nenhum). */
    suspend fun salvar(config: ServerConfig, certificado: String? = null) {
        preferencias.salvarEnderecoServidor(config.base.toString(), certificado)
        _impressao.value = certificado
        _atual.value = config
    }

    /** O certificado já aceito para [config], se é o servidor salvo. */
    fun certificadoAceito(config: ServerConfig): String? = _impressao.value?.takeIf { config == _atual.value }

    /**
     * Confere se o endereço é um servidor Conversa: uma rota protegida sem token
     * precisa responder 401 com `{ "error": ... }` em JSON (contrato §2.4).
     * [certificado]: a impressão de um certificado próprio que a pessoa aceitou (só no debug).
     */
    suspend fun testar(config: ServerConfig, certificado: String? = null): ResultadoTesteServidor = withContext(Dispatchers.IO) {
        val confianca = if (ConfiancaServidor.PERMITIDA) ConfiancaServidor({ certificado }) else null
        val cliente = okHttp.get().newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .apply { confianca?.let { sslSocketFactory(it.fabricaDeSockets(), it).hostnameVerifier(it.verificadorDeNome()) } }
            .build()
        val requisicao = Request.Builder().url(config.api.resolve("usuario/permissoes")!!).get().build()
        try {
            cliente.newCall(requisicao).execute().use { resposta ->
                val corpo = resposta.body.string()
                when {
                    resposta.code == 401 && ehErroConversa(corpo) -> ResultadoTesteServidor.Ok
                    resposta.code in listOf(502, 503, 504) -> ResultadoTesteServidor.Indisponivel
                    else -> ResultadoTesteServidor.NaoEhConversa
                }
            }
        } catch (e: SSLException) {
            ResultadoTesteServidor.CertificadoInvalido(confianca?.ultimoVisto?.let(::impressaoSha256))
        } catch (e: UnknownHostException) {
            ResultadoTesteServidor.HostNaoEncontrado
        } catch (e: IOException) {
            ResultadoTesteServidor.SemConexao(e.message)
        }
    }

    private fun ehErroConversa(corpo: String): Boolean = try {
        ConversaJson.parseToJsonElement(corpo).jsonObject.containsKey("error")
    } catch (_: Exception) {
        false
    }
}
