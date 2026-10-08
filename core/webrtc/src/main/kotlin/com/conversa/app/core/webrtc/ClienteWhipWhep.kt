package com.conversa.app.core.webrtc

import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.network.http.ServidorNaoConfiguradoException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** WHEP 404: o outro ainda não publicou (ou entrou só para assistir). */
class StreamAusente : IOException("Transmissão ainda não publicada")

/** Qualquer outra recusa do MediaMTX (ex.: 406 com duas trilhas do mesmo tipo). */
class RecusaMediaMtx(val codigo: Int) : IOException("MediaMTX recusou: $codigo")

/** Resposta do MediaMTX: o SDP answer e o endereço da sessão, para o `DELETE` ao encerrar. */
data class SessaoWhip(val resposta: String, val recurso: HttpUrl?)

/** Nome do stream de cada participante (contrato §9.9; os ids são números, já no formato permitido). */
fun caminhoStream(chamadaId: Long, usuarioId: Long) = "call-$chamadaId-u-$usuarioId"

/**
 * WHIP (publicar) e WHEP (assistir) no MediaMTX, em `<base>/webrtc/` (contrato §9.9).
 * Usa o OkHttp do app: o token não vai junto (só vai para `/api`), e não há
 * `TrustManager` próprio (o legado aceitava qualquer certificado).
 * Sem trickle ICE, como o web: a oferta já vai com os candidatos.
 */
@Singleton
class ClienteWhipWhep @Inject constructor(private val http: OkHttpClient, private val config: ServerConfigProvider) {
    suspend fun publicar(caminho: String, oferta: String): SessaoWhip = enviar(caminho, "whip", oferta)

    suspend fun assinar(caminho: String, oferta: String): SessaoWhip = enviar(caminho, "whep", oferta)

    /** `DELETE` da sessão. Falha não importa: o MediaMTX também derruba a sessão quando a conexão cai. */
    suspend fun encerrar(recurso: HttpUrl) {
        try {
            withContext(Dispatchers.IO) { http.newCall(Request.Builder().url(recurso).delete().build()).aguardar().close() }
        } catch (_: IOException) {
        }
    }

    private suspend fun enviar(caminho: String, tipo: String, oferta: String): SessaoWhip = withContext(Dispatchers.IO) {
        val base = config.atual.value?.webrtc ?: throw ServidorNaoConfiguradoException()
        val url = base.resolve("$caminho/$tipo") ?: throw IOException("Caminho inválido")
        val requisicao = Request.Builder().url(url).post(oferta.toRequestBody(SDP)).build()
        http.newCall(requisicao).aguardar().use { resposta ->
            when {
                resposta.isSuccessful -> SessaoWhip(
                    resposta.body.string(),
                    resposta.header("Location")?.let {
                        recursoDaSessao(base, url, it)
                    },
                )
                resposta.code == 404 -> throw StreamAusente()
                else -> throw RecusaMediaMtx(resposta.code)
            }
        }
    }

    private companion object {
        val SDP = "application/sdp".toMediaType()
    }
}

/**
 * O MediaMTX responde `Location` com o caminho a partir da raiz **dele**
 * (`/call-1-u-7/whip/<id>`); atrás do nginx essa raiz é `<base>/webrtc/`.
 * Também aceita endereço completo, caminho já com o prefixo e caminho relativo.
 */
internal fun recursoDaSessao(base: HttpUrl, pedido: HttpUrl, location: String): HttpUrl? = when {
    location.startsWith("http://") || location.startsWith("https://") -> location.toHttpUrlOrNull()
    location.startsWith(base.encodedPath) -> base.resolve(location)
    location.startsWith("/") -> base.resolve(location.removePrefix("/"))
    else -> pedido.resolve(location)
}

/** Executa a chamada sem prender a thread; cancelar a coroutine cancela a chamada. */
internal suspend fun Call.aguardar(): Response = suspendCancellableCoroutine { continuacao ->
    continuacao.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuacao.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuacao.resume(response) { _, resposta, _ -> resposta.close() }
            }
        },
    )
}
