package com.conversa.app.core.data.conversas

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ArquivadaResposta
import com.conversa.app.core.network.dto.ArquivarRequisicao
import com.conversa.app.core.network.dto.ConversaCriadaDto
import com.conversa.app.core.network.dto.ConversaDto
import com.conversa.app.core.network.dto.CriarConversaRequisicao
import com.conversa.app.core.network.dto.FixadasDto
import com.conversa.app.core.network.dto.IncluirMembroRequisicao
import com.conversa.app.core.network.dto.VinculoConversaDto
import com.conversa.app.core.network.http.ErroApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Repositório de conversas com o Room de verdade (em memória) e a API simulada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversasRepositorioTest {
    private lateinit var banco: ConversaBanco
    private lateinit var repositorio: ConversasRepositorio
    private val api = mockk<ConversaApi>()
    private val sessao = mockk<SessaoRepositorio> {
        every { sessao } returns MutableStateFlow(Sessao(token = "t", usuarioId = 7, nome = "Ana"))
    }
    private val relogio = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC)

    private val doServidor = listOf(
        ConversaDto(id = 1, tipo = 1, nome = "Bruno", destinatarioId = 8, mensagemId = 50, fixadaOrdem = 1),
        ConversaDto(id = 2, tipo = 2, descricao = "Projeto", mensagemId = 90, fixadaOrdem = 2),
        ConversaDto(id = 3, tipo = 1, nome = "Carla", destinatarioId = 9, mensagemId = 70),
    )

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
        repositorio = ConversasRepositorio(api, banco.conversaDao(), sessao, relogio)
        coEvery { api.conversas() } returns doServidor
    }

    @After
    fun fechar() = banco.close()

    private suspend fun fixadas() = repositorio.observarTodas().first().filter { it.fixada }.sortedBy { it.fixadaOrdem }.map { it.id }

    @Test
    fun `atualizar grava a lista do servidor e remove as que sairam`() = runTest {
        repositorio.atualizar()
        coEvery { api.conversas() } returns doServidor.take(2)
        repositorio.atualizar()
        assertThat(repositorio.observarTodas().first().map { it.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `fixar entra no fim e manda a lista completa`() = runTest {
        repositorio.atualizar()
        coEvery { api.ordenarFixadas(any()) } answers { firstArg() }

        repositorio.fixar(3).getOrThrow()

        coVerify { api.ordenarFixadas(FixadasDto(listOf(1, 2, 3))) }
        assertThat(fixadas()).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `mover fixada para cima troca a ordem e no topo nao chama a API`() = runTest {
        repositorio.atualizar()
        coEvery { api.ordenarFixadas(any()) } answers { firstArg() }

        repositorio.moverFixada(2, -1).getOrThrow()
        assertThat(fixadas()).containsExactly(2L, 1L).inOrder()

        repositorio.moverFixada(2, -1).getOrThrow()
        coVerify(exactly = 1) { api.ordenarFixadas(any()) }
    }

    @Test
    fun `erro ao fixar desfaz recarregando do servidor`() = runTest {
        repositorio.atualizar()
        coEvery { api.ordenarFixadas(any()) } throws IOException("sem rede")

        val resultado = repositorio.desafixar(1)

        assertThat(resultado.exceptionOrNull()).isInstanceOf(ErroApi.SemConexao::class.java)
        assertThat(fixadas()).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `arquivar marca a data, tira das fixadas e reordena as outras`() = runTest {
        repositorio.atualizar()
        coEvery { api.arquivarConversa(any()) } returns ArquivadaResposta(1, true)

        repositorio.arquivar(1, arquivada = true).getOrThrow()

        coVerify { api.arquivarConversa(ArquivarRequisicao(1, true)) }
        val conversa = repositorio.observar(1).first()!!
        assertThat(conversa.arquivadaEm).isEqualTo(Instant.parse("2026-10-07T12:00:00Z"))
        assertThat(conversa.fixada).isFalse()
        assertThat(fixadas()).containsExactly(2L)
        assertThat(repositorio.observar(2).first()!!.fixadaOrdem).isEqualTo(1)
    }

    @Test
    fun `direta existente e reaproveitada`() = runTest {
        repositorio.atualizar()
        assertThat(repositorio.obterOuCriarDireta(8).getOrThrow()).isEqualTo(1)
        coVerify(exactly = 0) { api.criarConversa(any()) }
    }

    @Test
    fun `direta nova cria, inclui eu e o contato, nessa ordem, e recarrega`() = runTest {
        repositorio.atualizar()
        coEvery { api.criarConversa(any()) } returns ConversaCriadaDto(id = 99)
        coEvery { api.incluirMembro(any()) } answers { VinculoConversaDto(id = 1) }

        assertThat(repositorio.obterOuCriarDireta(20).getOrThrow()).isEqualTo(99)

        coVerifyOrder {
            api.criarConversa(CriarConversaRequisicao(descricao = "", tipo = 1))
            api.incluirMembro(IncluirMembroRequisicao(99, 7))
            api.incluirMembro(IncluirMembroRequisicao(99, 20))
            api.conversas()
        }
    }

    @Test
    fun `grupo inclui todos os membros e eu, sem duplicar`() = runTest {
        coEvery { api.criarConversa(any()) } returns ConversaCriadaDto(id = 77, tipo = 2)
        coEvery { api.incluirMembro(any()) } answers { VinculoConversaDto(id = 1) }

        assertThat(repositorio.criarGrupo("  Projeto Alpha ", listOf(8L, 9L, 7L)).getOrThrow()).isEqualTo(77)

        coVerify { api.criarConversa(CriarConversaRequisicao(descricao = "Projeto Alpha", tipo = 2)) }
        coVerify(exactly = 3) { api.incluirMembro(any()) }
        coVerify(exactly = 1) { api.incluirMembro(IncluirMembroRequisicao(77, 7)) }
    }

    @Test
    fun `falha ao incluir membro devolve o erro`() = runTest {
        coEvery { api.criarConversa(any()) } returns ConversaCriadaDto(id = 77, tipo = 2)
        coEvery { api.incluirMembro(any()) } throws IOException("caiu")

        assertThat(repositorio.criarGrupo("G", listOf(8L)).isFailure).isTrue()
    }
}
