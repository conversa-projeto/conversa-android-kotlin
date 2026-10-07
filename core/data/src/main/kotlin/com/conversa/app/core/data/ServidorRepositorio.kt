package com.conversa.app.core.data

import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.json.ConversaJson
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

    /** Certificado não confiável (no debug, instale a CA do mkcert no aparelho). */
    data object CertificadoInvalido : ResultadoTesteServidor

    data object HostNaoEncontrado : ResultadoTesteServidor

    /** nginx no ar mas a API fora (502/503/504). */
    data object Indisponivel : ResultadoTesteServidor

    data class SemConexao(val detalhe: String?) : ResultadoTesteServidor
}

/**
 * Endereço do servidor configurado no app. Fonte da [ServerConfig] usada pelo
 * Retrofit (troca de host em tempo de execução) e pelo WebSocket.
 */
@Singleton
class ServidorRepositorio @Inject constructor(
    private val preferencias: PreferenciasStore,
    private val okHttp: dagger.Lazy<OkHttpClient>,
    @EscopoAplicacao escopo: CoroutineScope,
) : ServerConfigProvider {
    private val _atual = MutableStateFlow<ServerConfig?>(null)
    override val atual: StateFlow<ServerConfig?> = _atual.asStateFlow()

    private val _carregado = MutableStateFlow(false)

    /** `true` depois da primeira leitura do DataStore (a tela inicial espera por isso). */
    val carregado: StateFlow<Boolean> = _carregado.asStateFlow()

    init {
        escopo.launch {
            preferencias.enderecoServidor.collect { endereco ->
                _atual.value = endereco?.let(ServerConfig::aPartirDe)
                _carregado.value = true
            }
        }
    }

    suspend fun salvar(config: ServerConfig) {
        preferencias.salvarEnderecoServidor(config.base.toString())
        _atual.value = config
    }

    /**
     * Confere se o endereço é um servidor Conversa: uma rota protegida sem token
     * precisa responder 401 com `{ "error": ... }` em JSON (contrato §2.4).
     */
    suspend fun testar(config: ServerConfig): ResultadoTesteServidor = withContext(Dispatchers.IO) {
        val cliente = okHttp.get().newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
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
            ResultadoTesteServidor.CertificadoInvalido
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
