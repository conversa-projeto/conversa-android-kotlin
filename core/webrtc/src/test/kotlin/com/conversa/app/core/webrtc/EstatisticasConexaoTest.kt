package com.conversa.app.core.webrtc

import com.google.common.truth.Truth.assertThat
import java.math.BigInteger
import org.junit.Test
import org.webrtc.RTCStats

/** O registro da conexão da chamada: o prazo para conectar e os resumos das estatísticas, sem endereços. */
class EstatisticasConexaoTest {
    private fun stat(id: String, tipo: String, vararg membros: Pair<String, Any>) = RTCStats(0L, tipo, id, mapOf(*membros))

    @Test
    fun `prazo para conectar - 3 s, depois 6 s e 10 s, e fica em 10 s`() {
        assertThat(prazoParaConectar(0)).isEqualTo(3_000)
        assertThat(prazoParaConectar(1)).isEqualTo(6_000)
        assertThat(prazoParaConectar(2)).isEqualTo(10_000)
        assertThat(prazoParaConectar(7)).isEqualTo(10_000)
        assertThat(prazoParaConectar(-1)).isEqualTo(3_000)
    }

    @Test
    fun `candidatos da resposta do MediaMTX, sem o endereco`() {
        val sdp = """
            v=0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=candidate:1 1 udp 2130706431 172.30.0.20 8189 typ host
            a=candidate:2 1 udp 2130706431 fd00::14 8189 typ host
            a=candidate:3 1 tcp 1671430143 172.30.0.20 8189 typ host tcptype passive
            a=end-of-candidates
        """.trimIndent()
        val resumo = candidatosDaResposta(sdp)
        assertThat(resumo).isEqualTo("2 host udp, 1 host tcp")
        assertThat(resumo).doesNotContain("172.30")
        assertThat(candidatosDaResposta("v=0\nm=audio 9 UDP/TLS/RTP/SAVPF 111")).isEqualTo("nenhum candidato")
    }

    @Test
    fun `conexao presa - ICE de pe e o DTLS sem fechar, o par escolhido primeiro`() {
        val estatisticas = listOf(
            stat(
                "T01",
                "transport",
                "iceState" to "connected",
                "dtlsState" to "connecting",
                "bytesSent" to BigInteger.valueOf(2_355),
                "bytesReceived" to BigInteger.valueOf(512),
                "selectedCandidatePairId" to "CP2",
            ),
            stat(
                "L1",
                "local-candidate",
                "candidateType" to "relay",
                "protocol" to "udp",
                "relayProtocol" to "udp",
                "address" to "10.0.5.78",
            ),
            stat(
                "L2",
                "local-candidate",
                "candidateType" to "relay",
                "protocol" to "udp",
                "relayProtocol" to "tcp",
                "address" to "10.0.5.78",
            ),
            stat("R1", "remote-candidate", "candidateType" to "host", "protocol" to "udp", "address" to "172.30.0.20"),
            stat(
                "CP1",
                "candidate-pair",
                "localCandidateId" to "L2",
                "remoteCandidateId" to "R1",
                "state" to "failed",
                "requestsSent" to BigInteger.TEN,
            ),
            stat(
                "CP2",
                "candidate-pair",
                "localCandidateId" to "L1",
                "remoteCandidateId" to "R1",
                "state" to "succeeded",
                "nominated" to true,
                "requestsSent" to BigInteger.valueOf(4),
                "responsesReceived" to BigInteger.valueOf(4),
                "requestsReceived" to BigInteger.valueOf(3),
                "currentRoundTripTime" to 0.012,
            ),
            stat("O1", "outbound-rtp", "kind" to "audio", "packetsSent" to 0L),
        )
        val resumo = resumoDaConexao(estatisticas)
        assertThat(resumo).isEqualTo(
            "ICE connected, DTLS connecting, enviou 2,3 kB, recebeu 512 B; " +
                "pares: relay udp (TURN por udp) → host udp: succeeded, escolhido, indicado, pedidos 4, respostas 4, " +
                "pedidos do outro lado 3, RTT 12 ms; " +
                "relay udp (TURN por tcp) → host udp: failed, pedidos 10, respostas 0, pedidos do outro lado 0; " +
                "áudio: 0 pacote(s) enviado(s), 0 recebido(s)",
        )
        assertThat(resumo).doesNotContain("10.0.5.78")
        assertThat(resumo).doesNotContain("172.30")
    }

    @Test
    fun `sem par e sem transporte, e sem estatisticas`() {
        assertThat(resumoDaConexao(emptyList())).isEqualTo("sem estatísticas")
        assertThat(resumoDaConexao(listOf(stat("I1", "inbound-rtp", "kind" to "audio", "packetsReceived" to 7L))))
            .isEqualTo("sem transporte; nenhum par de candidatos; áudio: 0 pacote(s) enviado(s), 7 recebido(s)")
    }

    @Test
    fun `mais de 3 pares - mostra 3 e conta o resto`() {
        val pares = (1..5).map { stat("CP$it", "candidate-pair", "state" to "waiting") }
        assertThat(resumoDaConexao(pares)).contains("; +2; áudio:")
    }

    @Test
    fun `qualidade da assinatura - RTT, perda, jitter, buffer e o audio ocultado`() {
        val estatisticas = listOf(
            stat("T01", "transport", "selectedCandidatePairId" to "CP1"),
            stat("CP1", "candidate-pair", "currentRoundTripTime" to 0.0084),
            stat(
                "I1",
                "inbound-rtp",
                "kind" to "audio",
                "packetsReceived" to 750L,
                "packetsLost" to 3,
                "jitter" to 0.0031,
                // A soma, em segundos, da espera de cada amostra: 720 mil amostras a 63 ms.
                "jitterBufferDelay" to 45_360.0,
                "jitterBufferEmittedCount" to BigInteger.valueOf(720_000),
                "concealedSamples" to BigInteger.valueOf(960),
                "totalSamplesReceived" to BigInteger.valueOf(720_000),
            ),
        )
        assertThat(resumoDaQualidade(estatisticas)).isEqualTo(
            "RTT 8 ms; áudio: recebeu 750 pacotes, perdidos 3, jitter 3 ms, buffer 63 ms, ocultado 0,1 %",
        )
    }

    @Test
    fun `qualidade da publicacao - o que o MediaMTX diz que recebeu e o video`() {
        val estatisticas = listOf(
            stat("OA", "outbound-rtp", "kind" to "audio", "packetsSent" to 1_500L),
            stat(
                "OV",
                "outbound-rtp",
                "kind" to "video",
                "packetsSent" to 900L,
                "frameWidth" to 640L,
                "frameHeight" to 360L,
                "framesPerSecond" to 14.6,
                "qualityLimitationReason" to "bandwidth",
            ),
            stat(
                "RA",
                "remote-inbound-rtp",
                "kind" to "audio",
                "localId" to "OA",
                "packetsLost" to 0,
                "jitter" to 0.002,
                "roundTripTime" to 0.011,
            ),
        )
        assertThat(resumoDaQualidade(estatisticas)).isEqualTo(
            "áudio: enviou 1500 pacotes, perdidos lá 0, jitter lá 2 ms, RTT pelo RTCP 11 ms; " +
                "vídeo: enviou 900 pacotes, 640x360 a 15 fps, limitado por bandwidth",
        )
    }

    @Test
    fun `qualidade - nada medido e sem estatisticas`() {
        assertThat(resumoDaQualidade(emptyList())).isEqualTo("sem estatísticas")
        assertThat(resumoDaQualidade(listOf(stat("X", "codec")))).isEqualTo("nada medido")
    }

    @Test
    fun `qualidade - conectada e sem pacote de midia (visto no emulador, com o outro lado ainda republicando)`() {
        val estatisticas = listOf(
            stat("T01", "transport", "selectedCandidatePairId" to "CP1"),
            stat("CP1", "candidate-pair", "currentRoundTripTime" to 0.003),
        )
        assertThat(resumoDaQualidade(estatisticas)).isEqualTo("RTT 3 ms; nenhum pacote de mídia ainda")
    }
}
