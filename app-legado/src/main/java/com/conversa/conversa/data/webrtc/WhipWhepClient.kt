package com.conversa.conversa.data.webrtc

import android.util.Log
import com.conversa.conversa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Cliente HTTP dedicado para WHIP/WHEP.
 *
 * - Envia SDP offer no body com Content-Type: application/sdp
 * - Recebe SDP answer no body
 * - Aceita cert self-signed em debug (dev) para MediaMTX local com mkcert
 * - Retry WHEP: 404 significa "stream ainda nao publicado" → tenta de novo
 */
class WhipWhepClient {

    companion object { private const val TAG = "WhipWhepClient" }

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // TLS permissivo APENAS em debug (cert mkcert self-signed)
        if (BuildConfig.DEBUG) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAll, java.security.SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.build()
    }

    /** Erro nao-retriavel (HTTP != 2xx && != 404) — faz com que postWhep/postWhep propague imediato */
    private class WhepNaoRetryavel(msg: String) : RuntimeException(msg)

    private val sdpType = "application/sdp".toMediaType()

    /** POST /whip — publica stream. Retorna SDP answer. Sem retry (404 aqui eh erro). */
    suspend fun postWhip(url: String, offerSdp: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .post(offerSdp.toRequestBody(sdpType))
            .header("Content-Type", "application/sdp")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                error("WHIP ${resp.code} $url: $body")
            }
            resp.body?.string() ?: error("WHIP resposta vazia")
        }
    }

    /**
     * POST /whep — subscribe stream com retry em 404 (peer ainda nao publicou).
     * @param maxAttempts tentativas totais (vídeo: 40 @ 1s; áudio: 12 @ 800ms no web)
     * @param delayMs delay entre tentativas
     */
    suspend fun postWhepWithRetry(
        url: String,
        offerSdp: String,
        maxAttempts: Int = 40,
        delayMs: Long = 1000L,
    ): String = withContext(Dispatchers.IO) {
        var lastError: String = "sem tentativas"
        repeat(maxAttempts) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(offerSdp.toRequestBody(sdpType))
                    .header("Content-Type", "application/sdp")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext resp.body?.string() ?: error("WHEP resposta vazia")
                    }
                    lastError = "HTTP ${resp.code}"
                    if (resp.code != 404) {
                        // Nao-retriavel: propaga imediato via exception que escapa do try/catch
                        val body = resp.body?.string() ?: ""
                        throw WhepNaoRetryavel("WHEP ${resp.code} $url: $body")
                    }
                    Log.d(TAG, "WHEP 404 em $url (tentativa ${attempt + 1}/$maxAttempts), retry em ${delayMs}ms")
                }
            } catch (e: WhepNaoRetryavel) {
                // Propaga para o caller
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "WHEP tentativa ${attempt + 1} falhou: $lastError")
            }
            delay(delayMs)
        }
        error("WHEP esgotou $maxAttempts tentativas em $url: $lastError")
    }
}
