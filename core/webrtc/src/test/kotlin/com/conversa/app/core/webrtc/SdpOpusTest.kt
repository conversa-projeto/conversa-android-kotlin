package com.conversa.app.core.webrtc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Opus estéreo na oferta (qualidade "Música", TODO 8.5). */
class SdpOpusTest {
    private val oferta = listOf(
        "v=0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111 63",
        "a=rtpmap:111 opus/48000/2",
        "a=fmtp:111 minptime=10;useinbandfec=1",
        "a=rtpmap:63 red/48000/2",
        "a=fmtp:63 111/111",
        "",
    ).joinToString("\r\n")

    @Test
    fun `pede estereo so no fmtp do opus, e uma vez so`() {
        val estereo = comOpusEstereo(oferta)

        assertThat(estereo).contains("a=fmtp:111 minptime=10;useinbandfec=1;stereo=1;sprop-stereo=1\r\n")
        assertThat(estereo).contains("a=fmtp:63 111/111\r\n")
        assertThat(comOpusEstereo(estereo)).isEqualTo(estereo)
    }

    @Test
    fun `sem opus a oferta fica igual`() {
        val semOpus = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 VP8/90000\r\na=fmtp:96 x=1\r\n"

        assertThat(comOpusEstereo(semOpus)).isEqualTo(semOpus)
    }
}
