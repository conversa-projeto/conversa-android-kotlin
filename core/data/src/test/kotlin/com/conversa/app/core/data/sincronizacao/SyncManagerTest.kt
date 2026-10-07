package com.conversa.app.core.data.sincronizacao

import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.dao.SyncEstadoDao
import com.conversa.app.core.database.entidades.SyncEstadoEntidade
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ChamadaPendenteDto
import com.conversa.app.core.network.dto.ConversaDto
import com.conversa.app.core.network.dto.NovaMensagemDto
import com.conversa.app.core.network.dto.QuantidadeDto
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, escopoDoTeste())

        sync.ressincronizar()

        coVerify { api.mensagensNovas("2026-10-05T00:00:00.000Z") }
        // Conversa com mensagens salvas: busca a partir da última; sem: as 80 últimas
        coVerify { api.mensagens(42, mensagemReferencia = 100, mensagensPrevias = 0, mensagensSeguintes = 100) }
        coVerify { api.mensagens(50, mensagemReferencia = 0, mensagensPrevias = 80, mensagensSeguintes = 0) }
        coVerify { syncEstadoDao.gravar(SyncEstadoEntidade(SyncManager.CURSOR_MENSAGENS, "2026-10-06T12:05:00.000Z")) }
        coVerify { conversaDao.substituirTodas(any()) }
        assertThat(sync.online.value).containsExactly(8L, 9L)
        assertThat(sync.atividadesNovas.value).isEqualTo(3)
        assertThat(sync.chamadasPendentes.first().single().id).isEqualTo(10)
    }

    @Test
    fun `falha de rede numa parte nao derruba as outras`() = runTest {
        prepararApi()
        coEvery { api.mensagensNovas(any()) } throws IOException("sem rede")
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, escopoDoTeste())

        sync.ressincronizar()

        coVerify(exactly = 0) { syncEstadoDao.gravar(any()) }
        assertThat(sync.atividadesNovas.value).isEqualTo(3)
    }

    @Test
    fun `conectar dispara a sincronizacao e eventos atualizam presenca e contador`() = runTest {
        prepararApi()
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        estado.value = EstadoConexao.CONECTADO
        advanceUntilIdle()
        coVerify { api.contatosOnline() }

        eventos.emit(EventoSocket.StatusUsuario(9, online = false))
        eventos.emit(EventoSocket.StatusUsuario(11, online = true))
        advanceUntilIdle()
        assertThat(sync.online.value).containsExactly(8L, 11L)

        coEvery { api.atividadesNovas() } returns QuantidadeDto(5)
        eventos.emit(EventoSocket.NovaAtividade)
        advanceUntilIdle()
        assertThat(sync.atividadesNovas.value).isEqualTo(5)
    }

    @Test
    fun `varias mensagens novas seguidas viram uma sincronizacao so`() = runTest {
        prepararApi()
        val sync = SyncManager(api, tempoReal, conversaDao, mensagemDao, syncEstadoDao, escopoDoTeste())
        sync.iniciar()
        advanceUntilIdle()

        repeat(5) { eventos.emit(EventoSocket.NovaMensagem("Ana", "oi")) }
        advanceUntilIdle()

        coVerify(exactly = 1) { api.mensagensNovas(any()) }
    }
}

/** Escopo no mesmo relógio do teste (o backgroundScope não avança com advanceUntilIdle). */
private fun TestScope.escopoDoTeste(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
