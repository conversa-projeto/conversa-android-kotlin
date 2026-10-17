package com.conversa.app.core.network.tls

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedTrustManager
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfiancaServidorTest {
    private val certificado = mockk<X509Certificate> { every { encoded } returns byteArrayOf(1, 2, 3) }
    private val outro = mockk<X509Certificate> { every { encoded } returns byteArrayOf(9, 9, 9) }

    /** O "Android" recusa tudo (certificado próprio, sem CA instalada). */
    private val recusaTudo = mockk<X509ExtendedTrustManager> {
        every { checkServerTrusted(any(), any(), any<Socket>()) } throws CertificateException("não confiável")
        every { checkServerTrusted(any(), any()) } throws CertificateException("não confiável")
    }

    @Test
    fun `impressao no formato dos navegadores`() {
        assertThat(impressaoSha256(certificado))
            .isEqualTo("03:90:58:C6:F2:C0:CB:49:2C:53:3B:0A:4D:14:EF:77:CC:0F:78:AB:CC:CE:D5:28:7D:84:A1:A2:01:1C:FB:81")
    }

    @Test
    fun `sem certificado aceito continua recusando`() {
        val confianca = ConfiancaServidor({ null }, recusaTudo)
        assertThrows(CertificateException::class.java) { confianca.checkServerTrusted(arrayOf(certificado), "RSA", null as Socket?) }
        assertThat(confianca.ultimoVisto).isSameInstanceAs(certificado)
    }

    @Test
    fun `aceita so o certificado exato que a pessoa aceitou`() {
        val confianca = ConfiancaServidor({ impressaoSha256(certificado).lowercase() }, recusaTudo)
        confianca.checkServerTrusted(arrayOf(certificado), "RSA", null as Socket?)
        confianca.checkServerTrusted(arrayOf(certificado), "RSA")
        assertThrows(CertificateException::class.java) { confianca.checkServerTrusted(arrayOf(outro), "RSA", null as Socket?) }
    }

    @Test
    fun `o que o Android confia continua valendo`() {
        val aceitaTudo = mockk<X509ExtendedTrustManager>(relaxed = true)
        ConfiancaServidor({ null }, aceitaTudo).checkServerTrusted(arrayOf(outro), "RSA", null as Socket?)
    }
}
