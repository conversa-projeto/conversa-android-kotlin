package com.conversa.app.core.webrtc

import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.config.ServerConfigProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class WhipWhepTest {
    private val mediamtx = MockWebServer()
    private lateinit var cliente: ClienteWhipWhep

    @Before
    fun subir() {
        mediamtx.start()
        val config = object : ServerConfigProvider {
            override val atual: StateFlow<ServerConfig?> = MutableStateFlow(ServerConfig.aPartirDe(mediamtx.url("/").toString()))
        }
        cliente = ClienteWhipWhep(OkHttpClient(), config)
    }

    @After
    fun parar() = mediamtx.close()

    @Test
    fun `publicar manda a oferta como application-sdp no caminho do stream e devolve a resposta e a sessao`() = runTest {
        mediamtx.enqueue(MockResponse.Builder().code(201).addHeader("Location", "/call-10-u-7/whip/abc").body("v=0 resposta").build())

        val sessao = cliente.publicar(caminhoStream(10, 7), "v=0 oferta")

        val pedido = mediamtx.takeRequest()
        assertThat(pedido.method).isEqualTo("POST")
        assertThat(pedido.url.encodedPath).isEqualTo("/webrtc/call-10-u-7/whip")
        assertThat(pedido.headers["Content-Type"]).startsWith("application/sdp")
        assertThat(pedido.body?.utf8()).isEqualTo("v=0 oferta")
        assertThat(sessao.resposta).isEqualTo("v=0 resposta")
        // O MediaMTX responde a partir da raiz dele; atrás do nginx a raiz é /webrtc/.
        assertThat(sessao.recurso?.encodedPath).isEqualTo("/webrtc/call-10-u-7/whip/abc")
    }

    @Test
    fun `assinar com 404 vira StreamAusente e outras recusas vira RecusaMediaMtx`() = runTest {
        mediamtx.enqueue(MockResponse.Builder().code(404).build())
        mediamtx.enqueue(MockResponse.Builder().code(406).build())

        val ausente = runCatching { cliente.assinar(caminhoStream(10, 8), "oferta") }.exceptionOrNull()
        val recusa = runCatching { cliente.assinar(caminhoStream(10, 8), "oferta") }.exceptionOrNull()

        assertThat(ausente).isInstanceOf(StreamAusente::class.java)
        assertThat((recusa as RecusaMediaMtx).codigo).isEqualTo(406)
        assertThat(mediamtx.takeRequest().url.encodedPath).isEqualTo("/webrtc/call-10-u-8/whep")
    }

    @Test
    fun `encerrar faz DELETE na sessao e ignora falha`() = runTest {
        mediamtx.enqueue(MockResponse.Builder().code(200).build())

        cliente.encerrar(mediamtx.url("/webrtc/call-10-u-7/whip/abc"))
        mediamtx.close()
        cliente.encerrar(mediamtx.url("/webrtc/call-10-u-7/whip/abc"))

        val pedido = mediamtx.takeRequest()
        assertThat(pedido.method).isEqualTo("DELETE")
        assertThat(pedido.url.encodedPath).isEqualTo("/webrtc/call-10-u-7/whip/abc")
    }

    @Test
    fun `recurso da sessao aceita os formatos de Location`() {
        val base = "https://conversa.exemplo/webrtc/".toHttpUrl()
        val pedido = base.resolve("call-1-u-7/whip")!!

        assertThat(
            recursoDaSessao(base, pedido, "/call-1-u-7/whip/x").toString(),
        ).isEqualTo("https://conversa.exemplo/webrtc/call-1-u-7/whip/x")
        assertThat(
            recursoDaSessao(base, pedido, "/webrtc/call-1-u-7/whip/x").toString(),
        ).isEqualTo("https://conversa.exemplo/webrtc/call-1-u-7/whip/x")
        assertThat(recursoDaSessao(base, pedido, "https://outro/whip/x").toString()).isEqualTo("https://outro/whip/x")
        assertThat(recursoDaSessao(base, pedido, "whip/x").toString()).isEqualTo("https://conversa.exemplo/webrtc/call-1-u-7/whip/x")
    }

    @Test
    fun `codecs gravaveis primeiro - H264, VP9 e o resto na ordem`() {
        val codecs = listOf("video/VP8", "video/rtx", "video/VP9", "video/H264", "video/AV1", "video/H264")

        assertThat(ordenarGravaveis(codecs) { it })
            .containsExactly("video/H264", "video/H264", "video/VP9", "video/VP8", "video/rtx", "video/AV1").inOrder()
    }

    @Test
    fun `sdp com video so conta se o MediaMTX manda video`() {
        val audio = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=sendonly\r\n"
        val comVideo = audio + "m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=sendonly\r\n"
        val videoRecusado = audio + "m=video 0 UDP/TLS/RTP/SAVPF 96\r\na=inactive\r\n"
        val videoInativo = audio + "m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=inactive\r\n"
        val videoNoMeio = "v=0\r\nm=video 9 UDP 96\r\na=sendonly\r\nm=audio 9 UDP 111\r\na=sendonly\r\n"

        assertThat(sdpEnviaVideo(audio)).isFalse()
        assertThat(sdpEnviaVideo(comVideo)).isTrue()
        assertThat(sdpEnviaVideo(videoRecusado)).isFalse()
        assertThat(sdpEnviaVideo(videoInativo)).isFalse()
        assertThat(sdpEnviaVideo(videoNoMeio)).isTrue()
    }

    @Test
    fun `nome do stream segue a convencao do web`() {
        assertThat(caminhoStream(42, 7)).isEqualTo("call-42-u-7")
    }
}
