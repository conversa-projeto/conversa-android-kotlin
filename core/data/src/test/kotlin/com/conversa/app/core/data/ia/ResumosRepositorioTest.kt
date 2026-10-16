package com.conversa.app.core.data.ia

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.model.PeriodoResumo
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.StatusResumo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AssuntoResumoDto
import com.conversa.app.core.network.dto.PedirResumoRequisicao
import com.conversa.app.core.network.dto.ResumoDto
import com.conversa.app.core.network.http.ErroApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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

/** Resumo da conversa por IA (CON-14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResumosRepositorioTest {
    private lateinit var banco: ConversaBanco
    private val sessao = MutableStateFlow<Sessao?>(Sessao("tok", 7, "Ana"))
    private val sessoes = mockk<SessaoRepositorio> { every { this@mockk.sessao } returns this@ResumosRepositorioTest.sessao }
    private val api = mockk<ConversaApi>()
    private val pedido = Instant.parse("2026-10-16T14:30:00Z")

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun repo() = ResumosRepositorio(api, banco.resumoDao(), sessoes, Clock.fixed(pedido, ZoneOffset.UTC))

    @Test
    fun `pedir guarda com a hora, consultar atualiza e o resumo fica preso ao usuario`() = runTest {
        val repo = repo()
        coEvery { api.pedirResumo(PedirResumoRequisicao(42, "7d")) } returns
            ResumoDto(id = "abc", conversaId = 42, periodo = "7d", status = "processando", mensagens = 37)

        repo.pedir(42, PeriodoResumo.DIAS_7).getOrThrow()
        val guardado = repo.observar(42).first()!!
        assertThat(guardado.resumo.status).isEqualTo(StatusResumo.PROCESSANDO)
        assertThat(guardado.resumo.periodo).isEqualTo(PeriodoResumo.DIAS_7)
        assertThat(guardado.geradoEm).isEqualTo(pedido)

        coEvery { api.consultarResumo("abc") } returns ResumoDto(
            id = "abc",
            conversaId = 42,
            periodo = "7d",
            status = "concluido",
            mensagens = 37,
            assuntos = listOf(AssuntoResumoDto("Orçamento", "Bruno fecha até quinta.", listOf("Fechar orçamento"), listOf(101, 105))),
        )
        repo.consultar(guardado).getOrThrow()
        val pronto = repo.observar(42).first()!!
        assertThat(pronto.resumo.status).isEqualTo(StatusResumo.CONCLUIDO)
        assertThat(pronto.resumo.assuntos.single().mensagens).containsExactly(101L, 105L).inOrder()
        assertThat(pronto.geradoEm).isEqualTo(pedido)

        // Outra conta não vê.
        sessao.value = Sessao("tok2", 8, "Bruno")
        assertThat(repo.observar(42).first()).isNull()
    }

    @Test
    fun `consulta sem rede continua, e o pedido perdido vira erro com o motivo`() = runTest {
        val repo = repo()
        coEvery { api.pedirResumo(any()) } returns ResumoDto(id = "abc", conversaId = 42, status = "processando", mensagens = 5)
        repo.pedir(42, PeriodoResumo.HORAS_24).getOrThrow()

        coEvery { api.consultarResumo("abc") } throws IOException("caiu")
        repo.consultar(repo.observar(42).first()!!)
        assertThat(repo.observar(42).first()!!.resumo.status).isEqualTo(StatusResumo.PROCESSANDO)

        val motivo = "Resumo não encontrado (pode ter expirado). Peça de novo."
        coEvery { api.consultarResumo("abc") } throws ErroApi.Servidor(404, motivo)
        repo.consultar(repo.observar(42).first()!!)
        val perdido = repo.observar(42).first()!!.resumo
        assertThat(perdido.status).isEqualTo(StatusResumo.ERRO)
        assertThat(perdido.erro).isEqualTo(motivo)
        coVerify(exactly = 2) { api.consultarResumo("abc") }
    }
}
