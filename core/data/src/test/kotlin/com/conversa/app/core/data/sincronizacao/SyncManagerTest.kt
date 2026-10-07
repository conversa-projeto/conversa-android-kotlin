package com.conversa.app.core.data.sincronizacao

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.dao.SyncEstadoDao
import com.conversa.app.core.database.entidades.SyncEstadoEntidade
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ChamadaPendenteDto
import com.conversa.app.core.network.dto.ConversaDto
import com.conversa.app.core.network.dto.NovaMensagemDto
import com.conversa.app.core.network.dto.QuantidadeDto
import com.conversa.app.core.network.dto.StatusMensagemDto
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncManagerTest {
    private val api = mockk<ConversaApi>()
    private val estado = MutableStateFlow(EstadoConexao.DESCONECTADO)
    private val eventos = MutableSharedFlow<EventoSocket>(extraBufferCapacity = 16)
    private val tempoReal = mockk<RealtimeClient> {
        every { estado } returns this@SyncManagerTest.estado
        every { eventos } returns this@SyncManagerTest.eventos
    }
    private val conversaDao = mockk<ConversaDao>(relaxed = true)
    private val mensagemDao = mockk<MensagemDao>(relaxed = true)
    private val syncEstadoDao = mockk<SyncEstadoDao>(relaxed = true)
    private val presenca = mockk<PresencaRepositorio>(relaxed = true)
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana")) }

    private fun prepararApi() {
        coEvery { api.mensagensNovas(any()) } returns listOf(
            NovaMensagemDto(42, 105, "2026-10-06T12:00:00.123Z"),
            NovaMensagemDto(50, 200, "2026-10-06T12:05:00.000Z"),
        )
        coEvery { api.mensagens(any(), any(), any(), any()) } returns emptyList()
        coEvery { api.conversas() } returns listOf(ConversaDto(id = 42))
        coEvery { api.contatosOnline() } returns listOf(8L, 9L)
        coEvery { api.atividadesNovas() } returns QuantidadeDto(3)
        coEvery { api.chamadasPendentes() } returns listOf(ChamadaPendenteDto(id = 10, tipo = 1, status = 1))
        coEvery { mensagemDao.ultimaSalva(42) } returns 100L
        coEvery { mensagemDao.ultimaSalva(50) } returns null
        coEvery { syncEstadoDao.ler(any()) } returns "2026-10-05T00:00:00.000Z"
    }

    @Test
    fun `ressincronizar busca tudo e avanca o cursor`() = runTest {
        prepararApi()
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())

        sync.ressincronizar()

        coVerify { api.mensagensNovas("2026-10-05T00:00:00.000Z") }
        // Conversa com mensagens salvas: busca a partir da última; sem: as 80 últimas
        coVerify { api.mensagens(42, mensagemReferencia = 100, mensagensPrevias = 0, mensagensSeguintes = 100) }
        coVerify { api.mensagens(50, mensagemReferencia = 0, mensagensPrevias = 80, mensagensSeguintes = 0) }
        coVerify { syncEstadoDao.gravar(SyncEstadoEntidade(SyncManager.CURSOR_MENSAGENS, "2026-10-06T12:05:00.000Z")) }
        coVerify { conversaDao.substituirTodas(any()) }
        coVerify { presenca.recarregarOnline() }
        coVerify { presenca.limparDigitando(42) }
        assertThat(sync.atividadesNovas.value).isEqualTo(3)
        assertThat(sync.chamadasPendentes.first().single().id).isEqualTo(10)
    }

    @Test
    fun `falha de rede numa parte nao derruba as outras`() = runTest {
        prepararApi()
        coEvery { api.mensagensNovas(any()) } throws IOException("sem rede")
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())

        sync.ressincronizar()

        coVerify(exactly = 0) { syncEstadoDao.gravar(any()) }
        assertThat(sync.atividadesNovas.value).isEqualTo(3)
    }

    @Test
    fun `conectar dispara a sincronizacao e eventos atualizam o contador`() = runTest {
        prepararApi()
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        estado.value = EstadoConexao.CONECTADO
        advanceUntilIdle()
        coVerify { presenca.recarregarOnline() }
        coVerify { api.conversas() }

        coEvery { api.atividadesNovas() } returns QuantidadeDto(5)
        eventos.emit(EventoSocket.NovaAtividade)
        advanceUntilIdle()
        assertThat(sync.atividadesNovas.value).isEqualTo(5)
    }

    @Test
    fun `varias mensagens novas seguidas viram uma sincronizacao so`() = runTest {
        prepararApi()
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        repeat(5) { eventos.emit(EventoSocket.NovaMensagem("Ana", "oi")) }
        advanceUntilIdle()

        coVerify(exactly = 1) { api.mensagensNovas(any()) }
    }

    @Test
    fun `WS 3 atualiza o status so nas minhas mensagens e o oculto em todas`() = runTest {
        prepararApi()
        coEvery { api.statusMensagens(42, "101,102") } returns listOf(
            StatusMensagemDto(42, 101, recebida = true, visualizada = true, reproduzida = false, excluidaEm = null),
            StatusMensagemDto(
                42,
                102,
                recebida = true,
                visualizada = false,
                reproduzida = false,
                excluidaEm = Instant.parse("2026-10-06T12:00:00Z"),
            ),
        )
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        eventos.emit(EventoSocket.StatusMensagens(42, listOf(101, 102)))
        advanceUntilIdle()

        coVerify { mensagemDao.atualizarStatusDaMinha(101, 7, true, true, false) }
        coVerify { mensagemDao.atualizarOculta(102, Instant.parse("2026-10-06T12:00:00Z").toEpochMilli()) }
        coVerify(exactly = 0) { mensagemDao.atualizarStatusDaMinha(any(), neq(7L), any(), any(), any()) }
    }

    @Test
    fun `WS 3 com id que nao esta no cache busca as mensagens que faltam`() = runTest {
        prepararApi()
        coEvery { api.statusMensagens(any(), any()) } returns emptyList()
        coEvery { mensagemDao.buscar(105) } returns null
        coEvery { mensagemDao.ultimaSalva(42) } returns 100L
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, presenca, sessao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        // Resumo de chamada: só chega por WS 3 (contrato §9.8).
        eventos.emit(EventoSocket.StatusMensagens(42, listOf(105)))
        advanceUntilIdle()

        coVerify { api.mensagens(42, mensagemReferencia = 100, mensagensPrevias = 0, mensagensSeguintes = 100) }
    }
}
