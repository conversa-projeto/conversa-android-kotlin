package com.conversa.app.core.data.tempoReal

import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Quando o socket liga e desliga, a faixa de 5 s (GER-02) e a atualização periódica
 * de 8 s. O dispatcher de E/S é o do teste, então tudo anda no relógio virtual.
 */
class ConexaoTempoRealTest {
    private val estado = MutableStateFlow(EstadoConexao.DESCONECTADO)
    private val cliente = mockk<RealtimeClient>(relaxed = true) { every { estado } returns this@ConexaoTempoRealTest.estado }
    private val sessaoAtual = MutableStateFlow<Sessao?>(null)
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns sessaoAtual }
    private val servidor = mockk<ServidorRepositorio> { every { atual } returns MutableStateFlow(ServerConfig.aPartirDe("https://x")) }
    private val visivel = MutableStateFlow(true)
    private val primeiroPlano = mockk<MonitorPrimeiroPlano> { every { emPrimeiroPlano } returns visivel }
    private val sincronizacao = mockk<SyncManager>(relaxed = true)

    private fun TestScope.criar(escopo: kotlinx.coroutines.CoroutineScope) =
        ConexaoTempoReal(cliente, sessao, servidor, primeiroPlano, sincronizacao, escopo, es = StandardTestDispatcher(testScheduler)).also {
            it.iniciar()
        }

    @Test
    fun `conectou antes de 5 s - a faixa nao aparece nem depois`() = runTest {
        val escopo = escopoDoTeste()
        val conexao = criar(escopo)
        sessaoAtual.value = Sessao("t", 7, "Ana")
        runCurrent()
        assertThat(conexao.ligada.value).isTrue()

        estado.value = EstadoConexao.CONECTANDO
        advanceTimeBy(2_000)
        estado.value = EstadoConexao.CONECTADO
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()

        assertThat(conexao.semTempoReal.value).isFalse()
        coVerify(exactly = 0) { sincronizacao.atualizacaoPeriodica() }
        escopo.cancel()
    }

    @Test
    fun `fora do ar - faixa depois de 5 s, atualizacao a cada 8 s, some ao conectar`() = runTest {
        val escopo = escopoDoTeste()
        val conexao = criar(escopo)
        sessaoAtual.value = Sessao("t", 7, "Ana")
        estado.value = EstadoConexao.AGUARDANDO
        runCurrent()

        advanceTimeBy(4_000)
        runCurrent()
        assertThat(conexao.semTempoReal.value).isFalse()
        advanceTimeBy(1_100)
        runCurrent()
        assertThat(conexao.semTempoReal.value).isTrue()

        advanceTimeBy(11_000) // 16 s no total: duas atualizações
        runCurrent()
        coVerify(exactly = 2) { sincronizacao.atualizacaoPeriodica() }

        estado.value = EstadoConexao.CONECTADO
        runCurrent()
        assertThat(conexao.semTempoReal.value).isFalse()
        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 2) { sincronizacao.atualizacaoPeriodica() }
        escopo.cancel()
    }

    @Test
    fun `sem sessao desliga na hora e em segundo plano so depois da tolerancia`() = runTest {
        val escopo = escopoDoTeste()
        criar(escopo)
        sessaoAtual.value = Sessao("t", 7, "Ana")
        runCurrent()
        verify { cliente.conectar("t") }

        visivel.value = false
        runCurrent()
        advanceTimeBy(ConexaoTempoReal.TOLERANCIA_MS - 1_000)
        runCurrent()
        verify(exactly = 0) { cliente.desconectar() }
        advanceTimeBy(2_000)
        runCurrent()
        verify(exactly = 1) { cliente.desconectar() }

        visivel.value = true
        runCurrent()
        sessaoAtual.value = null
        runCurrent()
        verify(exactly = 2) { cliente.desconectar() }
        escopo.cancel()
    }
}
