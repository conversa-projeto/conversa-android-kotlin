package com.conversa.app.core.data.presenca

import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PresencaRepositorioTest {
    private val api = mockk<ConversaApi>()
    private val eventos = MutableSharedFlow<EventoSocket>(extraBufferCapacity = 16)
    private val tempoReal = mockk<RealtimeClient> { every { eventos } returns this@PresencaRepositorioTest.eventos }

    @Test
    fun `online vem da API e muda com o evento 60`() = runTest {
        coEvery { api.contatosOnline() } returns listOf(8L, 9L)
        val escopo = escopoDoTeste()
        val presenca = PresencaRepositorio(api, tempoReal, escopo)
        presenca.iniciar()
        runCurrent()

        presenca.recarregarOnline()
        assertThat(presenca.online.value).containsExactly(8L, 9L)

        eventos.emit(EventoSocket.StatusUsuario(9, online = false))
        eventos.emit(EventoSocket.StatusUsuario(11, online = true))
        runCurrent()
        assertThat(presenca.online.value).containsExactly(8L, 11L)
        escopo.cancel()
    }

    @Test
    fun `digitando expira em 4 s e renova a cada aviso`() = runTest {
        val escopo = escopoDoTeste()
        val presenca = PresencaRepositorio(api, tempoReal, escopo)
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
        val presenca = PresencaRepositorio(api, tempoReal, escopo)
        presenca.iniciar()
        runCurrent()

        eventos.emit(EventoSocket.Digitando(conversaId = 42, usuarioId = 8))
        eventos.emit(EventoSocket.GravandoAudio(conversaId = 42, usuarioId = 9))
        eventos.emit(EventoSocket.Digitando(conversaId = 50, usuarioId = 9))
        runCurrent()

        presenca.limparDigitando(42)
        assertThat(presenca.digitando.value.keys).containsExactly(50L)
        assertThat(presenca.gravando.value).isEmpty()

        presenca.limpar()
        assertThat(presenca.digitando.value).isEmpty()
        assertThat(presenca.online.value).isEmpty()
        escopo.cancel()
    }
}
