package com.conversa.app.core.data.presenca

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.notificacoes.ConversaEmTela
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.model.EstadoPresenca
import com.conversa.app.core.model.PresencaContato
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.PresencaContatoDto
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PresencaRepositorioTest {
    private val api = mockk<ConversaApi>()
    private val eventos = MutableSharedFlow<EventoSocket>(extraBufferCapacity = 16)
    private val conexao = MutableStateFlow(EstadoConexao.CONECTADO)
    private val tempoReal = mockk<RealtimeClient> {
        every { eventos } returns this@PresencaRepositorioTest.eventos
        every { estado } returns conexao
        every { enviarPresenca(any(), any()) } returns true
    }
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana")) }
    private val visivel = MutableStateFlow(true)
    private val primeiroPlano = mockk<MonitorPrimeiroPlano> { every { emPrimeiroPlano } returns visivel }
    private val emTela = ConversaEmTela()

    private fun criar(escopo: CoroutineScope) = PresencaRepositorio(api, tempoReal, sessao, primeiroPlano, emTela, escopo)

    @Test
    fun `estado e visto por ultimo vem da API e mudam com o evento 60, sem perder o meu`() = runTest {
        val visto = Instant.parse("2026-10-15T10:00:00Z")
        coEvery { api.contatosPresenca() } returns listOf(
            PresencaContatoDto(8, "ativo"),
            PresencaContatoDto(9, "offline", visto),
            PresencaContatoDto(10, "ausente"),
        )
        val escopo = escopoDoTeste()
        val presenca = criar(escopo)
        presenca.iniciar()
        runCurrent()
        // O meu estado chega pelo WS 60 logo depois do login, às vezes antes da lista.
        eventos.emit(EventoSocket.StatusUsuario(7, online = true, estado = "ativo"))
        runCurrent()

        presenca.recarregarOnline()
        assertThat(presenca.presencas.value).containsExactly(
            8L,
            PresencaContato(EstadoPresenca.ATIVO),
            9L,
            PresencaContato(EstadoPresenca.OFFLINE, visto),
            10L,
            PresencaContato(EstadoPresenca.AUSENTE),
            7L,
            PresencaContato(EstadoPresenca.ATIVO),
        )

        // O 9 fica ativo (sem visto_em: guarda o anterior); o 8 sai (vem o visto novo).
        eventos.emit(EventoSocket.StatusUsuario(9, online = true, estado = "ativo"))
        eventos.emit(EventoSocket.StatusUsuario(8, online = false, estado = "offline", vistoEm = "2026-10-15T11:30:00.000Z"))
        // Servidor antigo, sem estado: online vira ativo.
        eventos.emit(EventoSocket.StatusUsuario(11, online = true))
        runCurrent()
        assertThat(presenca.presencas.value[9]).isEqualTo(PresencaContato(EstadoPresenca.ATIVO, visto))
        assertThat(presenca.presencas.value[8]).isEqualTo(PresencaContato(EstadoPresenca.OFFLINE, Instant.parse("2026-10-15T11:30:00Z")))
        assertThat(presenca.presencas.value[11]?.estado).isEqualTo(EstadoPresenca.ATIVO)
        escopo.cancel()
    }

    @Test
    fun `quem esta na conversa vem ao abrir e muda com o evento 63`() = runTest {
        coEvery { api.presentesConversa(42) } returns listOf(8L)
        val escopo = escopoDoTeste()
        val presenca = criar(escopo)
        presenca.iniciar()
        runCurrent()

        presenca.carregarPresentes(42)
        assertThat(presenca.presentes.value[42]).containsExactly(8L)

        eventos.emit(EventoSocket.PresencaConversa(42, 9, aberta = true))
        eventos.emit(EventoSocket.PresencaConversa(42, 8, aberta = false))
        runCurrent()
        assertThat(presenca.presentes.value[42]).containsExactly(9L)
        escopo.cancel()
    }

    @Test
    fun `a minha presenca vai depois do login, so quando muda, e de novo a cada login`() = runTest {
        val escopo = escopoDoTeste()
        val presenca = criar(escopo)
        presenca.iniciar()
        emTela.apareceu(42)
        runCurrent()
        // Antes do meu WS 60 o servidor ainda pode não ter aceitado o login: nada vai.
        verify(exactly = 0) { tempoReal.enviarPresenca(any(), any()) }

        eventos.emit(EventoSocket.StatusUsuario(7, online = true, estado = "ativo"))
        runCurrent()
        verify(exactly = 1) { tempoReal.enviarPresenca(true, 42) }

        // A mesma coisa de novo: não reenvia. Em segundo plano: inativo, sem conversa.
        emTela.apareceu(42)
        visivel.value = false
        runCurrent()
        verify(exactly = 1) { tempoReal.enviarPresenca(true, 42) }
        verify(exactly = 1) { tempoReal.enviarPresenca(false, null) }

        // Reconectou: o login novo reenvia o estado atual.
        conexao.value = EstadoConexao.AGUARDANDO
        runCurrent()
        conexao.value = EstadoConexao.CONECTADO
        eventos.emit(EventoSocket.StatusUsuario(7, online = true, estado = "ausente"))
        runCurrent()
        verify(exactly = 2) { tempoReal.enviarPresenca(false, null) }
        escopo.cancel()
    }

    @Test
    fun `digitando expira em 4 s e renova a cada aviso`() = runTest {
        val escopo = escopoDoTeste()
        val presenca = criar(escopo)
        presenca.iniciar()
        runCurrent()

        eventos.emit(EventoSocket.Digitando(conversaId = 42, usuarioId = 8))
        runCurrent()
        assertThat(presenca.digitando.value).containsExactly(42L, setOf(8L))

        advanceTimeBy(3_000)
        eventos.emit(EventoSocket.Digitando(conversaId = 42, usuarioId = 8))
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        // Renovou: ainda digitando 6 s depois do primeiro aviso.
        assertThat(presenca.digitando.value[42]).containsExactly(8L)

        advanceTimeBy(1_100)
        runCurrent()
        assertThat(presenca.digitando.value).isEmpty()
        escopo.cancel()
    }

    @Test
    fun `mensagem nova limpa quem digitava e gravava naquela conversa`() = runTest {
        val escopo = escopoDoTeste()
        val presenca = criar(escopo)
        presenca.iniciar()
        runCurrent()

        eventos.emit(EventoSocket.Digitando(conversaId = 42, usuarioId = 8))
        eventos.emit(EventoSocket.GravandoAudio(conversaId = 42, usuarioId = 9))
        eventos.emit(EventoSocket.Digitando(conversaId = 50, usuarioId = 9))
        eventos.emit(EventoSocket.StatusUsuario(8, online = true, estado = "ativo"))
        runCurrent()

        presenca.limparDigitando(42)
        assertThat(presenca.digitando.value.keys).containsExactly(50L)
        assertThat(presenca.gravando.value).isEmpty()

        presenca.limpar()
        assertThat(presenca.digitando.value).isEmpty()
        assertThat(presenca.presencas.value).isEmpty()
        escopo.cancel()
    }
}
