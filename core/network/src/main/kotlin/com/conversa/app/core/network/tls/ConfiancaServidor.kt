package com.conversa.app.core.network.tls

import com.conversa.app.core.network.BuildConfig
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import kotlinx.coroutines.flow.StateFlow
import okhttp3.internal.tls.OkHostnameVerifier

/** O certificado do servidor que a pessoa aceitou na tela Servidor (impressão SHA-256), ou `null`. */
interface CertificadoConfiadoProvider {
    val impressao: StateFlow<String?>
}

/** Impressão digital SHA-256 do certificado, como `AB:CD:…` (o formato que navegadores e o `openssl` mostram). */
fun impressaoSha256(certificado: Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(certificado.encoded).joinToString(":") { "%02X".format(it) }

/**
 * Servidor interno com certificado próprio (mkcert, autoassinado, CA da empresa), só no debug:
 * confia no que o Android já confia (o `network_security_config`) **ou** no certificado exato que a
 * pessoa aceitou na tela Servidor depois de ver a impressão digital (confiança no primeiro uso).
 * Não é um "aceita tudo": qualquer outro certificado continua recusado.
 *
 * [confiado] é lido a cada conexão: aceitar ou trocar o servidor vale sem recriar o cliente.
 */
class ConfiancaServidor(private val confiado: () -> String?, private val padrao: X509ExtendedTrustManager = gerenciadorPadrao()) :
    X509ExtendedTrustManager() {
    /** Certificado do último servidor visto (a tela Servidor mostra a impressão quando ele é recusado). */
    @Volatile
    var ultimoVisto: X509Certificate? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
        verificar(chain) { padrao.checkServerTrusted(chain, authType, socket) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
        verificar(chain) { padrao.checkServerTrusted(chain, authType, engine) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        verificar(chain) { padrao.checkServerTrusted(chain, authType) }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) =
        padrao.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) =
        padrao.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = padrao.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = padrao.acceptedIssuers

    private inline fun verificar(chain: Array<out X509Certificate>?, doAndroid: () -> Unit) {
        chain?.firstOrNull()?.let { ultimoVisto = it }
        try {
            doAndroid()
        } catch (e: CertificateException) {
            if (!ehOAceito(chain?.firstOrNull())) throw e
        }
    }

    private fun ehOAceito(certificado: Certificate?): Boolean {
        val alvo = confiado() ?: return false
        return certificado != null && impressaoSha256(certificado).equals(alvo, ignoreCase = true)
    }

    fun fabricaDeSockets(): SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(this@ConfiancaServidor), null)
    }.socketFactory

    /** O certificado aceito vale mesmo sem o nome ou IP usado nele (comum em certificado autoassinado). */
    fun verificadorDeNome(doAndroid: HostnameVerifier = OkHostnameVerifier): HostnameVerifier = HostnameVerifier { nome, sessao ->
        doAndroid.verify(nome, sessao) || ehOAceito(runCatching { sessao.peerCertificates.firstOrNull() }.getOrNull())
    }

    companion object {
        /** Só o build de debug aceita certificado escolhido pela pessoa; o release só confia nas CAs do sistema (FC-106). */
        val PERMITIDA: Boolean get() = BuildConfig.DEBUG

        fun gerenciadorPadrao(): X509ExtendedTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509ExtendedTrustManager>()
            .first()
    }
}
