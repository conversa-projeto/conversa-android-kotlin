package com.conversa.app.core.webrtc

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Quando a oferta sai sem trickle ICE (FC-702): coleta completa, 1 s depois do TURN, ou 5 s. */
@OptIn(ExperimentalCoroutinesApi::class)
class EsperaColetaTest {
    private val host = "candidate:1 1 udp 2122260223 10.0.2.16 41000 typ host generation 0"
    private val relayTcp = "candidate:2 1 udp 41885439 127.0.0.1 52000 typ relay raddr 0.0.0.0 rport 0 generation 0"

    @Test
    fun `coleta completa - manda na hora`() = runTest {
        val espera = EsperaColeta()
        launch {
            delay(150)
            espera.aoCandidato(host)
            espera.aoMudarColeta(completa = true)
        }
        espera.esperar()
        assertThat(currentTime).isEqualTo(150)
    }

    @Test
    fun `sem o UDP - manda 1 s depois do primeiro candidato do TURN, sem esperar os 5 s`() = runTest {
        val espera = EsperaColeta()
        launch {
            espera.aoCandidato(host)
            delay(100)
            espera.aoCandidato(relayTcp)
        }
        espera.esperar()
        assertThat(currentTime).isEqualTo(100 + EsperaColeta.FOLGA_DEPOIS_DO_RELAY_MS)
    }

    @Test
    fun `com o UDP - a coleta completa antes da folga acabar manda na hora`() = runTest {
        val espera = EsperaColeta()
        launch {
            delay(80)
            espera.aoCandidato(relayTcp)
            delay(200)
            espera.aoMudarColeta(completa = true)
        }
        espera.esperar()
        assertThat(currentTime).isEqualTo(280)
    }

    @Test
    fun `nada chega - desiste em 5 s, como antes`() = runTest {
        val espera = EsperaColeta()
        launch {
            espera.aoCandidato(host)
            delay(4_500)
            espera.aoCandidato(relayTcp)
        }
        espera.esperar()
        assertThat(currentTime).isEqualTo(EsperaColeta.ESPERA_ICE_MS)
    }

    @Test
    fun `candidato do TURN pelo tipo`() {
        assertThat(ehCandidatoRelay(relayTcp)).isTrue()
        assertThat(ehCandidatoRelay("candidate:3 1 udp 1 1.2.3.4 5 typ relay")).isTrue()
        assertThat(ehCandidatoRelay(host)).isFalse()
        assertThat(ehCandidatoRelay("candidate:4 1 udp 1686052607 200.1.2.3 41000 typ srflx raddr 10.0.2.16 rport 41000")).isFalse()
        assertThat(ehCandidatoRelay("candidate:5 1 tcp 1518280447 10.0.2.16 9 typ host tcptype active")).isFalse()
    }
}
