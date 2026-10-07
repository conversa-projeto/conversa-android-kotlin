package com.conversa.app.core.data.mensagens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.database.entidades.ConversaEntidade
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ConteudoDto
import com.conversa.app.core.network.dto.ConteudoEnvioDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.MensagemCriadaDto
import com.conversa.app.core.network.dto.MensagemDto
import com.conversa.app.core.network.dto.SucessoDto
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/** Carregar/paginar, marcar como lida e a fila de envio, com o Room de verdade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MensagensTest {
    private lateinit var banco: ConversaBanco
    private val api = mockk<ConversaApi>()
    private val agora = Instant.parse("2026-10-07T12:00:00Z")
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana Souza")) }
    private val conversas = mockk<ConversasRepositorio>(relaxed = true)
    private val agendador = object : AgendadorEnvio {
        var agendados = 0
        var cancelados = 0

        override fun agendar() {
            agendados++
        }

        override fun cancelar() {
            cancelados++
        }
    }

    private fun msg(id: Long, texto: String = "m$id", remetente: Long = 8) = MensagemDto(
        id = id,
        remetenteId = remetente,
        remetente = "Bruno",
        conversaId = 42,
        inserida = agora.plusSeconds(id),
        conteudos = listOf(ConteudoDto(id = id, ordem = 1, tipo = 1, conteudo = texto)),
    )

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun envio() = EnvioMensagens(
        api,
        banco.mensagemDao(),
        banco.envioPendenteDao(),
        sessao,
        conversas,
        agendador,
        Clock.fixed(agora, ZoneOffset.UTC),
    )

    // --- Carregar e paginar ---

    @Test
    fun `abrir traz as 80 recentes e rolar para cima pede 60 antes da mais antiga, sem repetir`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo)
        coEvery { api.mensagens(42, 0, 80, 0) } returns (100L..179L).map { msg(it) }
        assertThat(repo.carregarRecentes(42).getOrThrow()).isEqualTo(80)

        coEvery { api.mensagens(42, 100, 60, 0) } returns (41L..100L).map { msg(it) }
        assertThat(repo.carregarAnteriores(42).getOrThrow()).isEqualTo(59)

        coEvery { api.mensagens(42, 41, 60, 0) } returns listOf(msg(41))
        assertThat(repo.carregarAnteriores(42).getOrThrow()).isEqualTo(0)

        val todas = repo.observar(42).first()
        assertThat(todas).hasSize(139)
        assertThat(todas.first().id).isEqualTo(41)
        escopo.cancel()
    }

    @Test
    fun `marcar como lida e otimista, desconta o contador e manda uma vez so`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo)
        banco.conversaDao().salvar(listOf(ConversaEntidade(42, 1, null, "Bruno", 8, 105, agora, "oi", 2, null, null, null)))
        coEvery { api.mensagens(42, 0, 80, 0) } returns listOf(msg(104), msg(105))
        coEvery { api.visualizar(any()) } returns SucessoDto(true)
        repo.carregarRecentes(42)

        repo.marcarLida(42, 104)
        repo.marcarLida(42, 104)
        advanceUntilIdle()

        assertThat(banco.mensagemDao().buscar(104)!!.mensagem.visualizada).isTrue()
        assertThat(banco.conversaDao().observar(42).first()!!.naoLidas).isEqualTo(1)
        coVerify(exactly = 1) { api.visualizar(MarcarStatusRequisicao(42, 104)) }
        escopo.cancel()
    }

    // --- Envio ---

    @Test
    fun `enviar cria a otimista, agenda e troca pela real`() = runTest {
        val envio = envio()
        val idLocal = envio.enviarTexto(42, "olá")

        assertThat(idLocal).isLessThan(0)
        val otimista = banco.mensagemDao().buscar(idLocal)!!
        assertThat(otimista.mensagem.enviando).isTrue()
        assertThat(otimista.mensagem.remetente).isEqualTo("Ana")
        assertThat(agendador.agendados).isEqualTo(1)

        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 500, conversaId = 42)
        coEvery { api.mensagens(42, 500, 0, 0) } returns listOf(msg(500, "olá", remetente = 7))

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        coVerify { api.enviarMensagem(EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "olá")))) }
        assertThat(banco.mensagemDao().buscar(idLocal)).isNull()
        assertThat(banco.mensagemDao().buscar(500)!!.conteudos.single().conteudo).isEqualTo("olá")
        assertThat(banco.envioPendenteDao().todos()).isEmpty()
        coVerify { conversas.atualizar() }
    }

    @Test
    fun `sem rede fica na fila sem contar tentativa e na ordem`() = runTest {
        val envio = envio()
        val primeira = envio.enviarTexto(42, "1")
        val segunda = envio.enviarTexto(42, "2")
        assertThat(segunda).isLessThan(primeira)

        coEvery { api.enviarMensagem(any()) } throws IOException("sem rede")
        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.TENTAR_DEPOIS)
        coVerify(exactly = 1) { api.enviarMensagem(any()) }
        assertThat(banco.envioPendenteDao().todos().map { it.tentativas }).containsExactly(0, 0)
        assertThat(banco.mensagemDao().buscar(primeira)!!.mensagem.falhou).isFalse()
    }

    @Test
    fun `recusa do servidor marca falhou e segue com as outras, reenviar e apagar`() = runTest {
        val envio = envio()
        val ruim = envio.enviarTexto(42, "ruim")
        val boa = envio.enviarTexto(42, "boa")
        coEvery { api.enviarMensagem(match { it.conteudos.single().conteudo == "ruim" }) } throws
            HttpException(Response.error<Any>(403, """{"error":"Acesso negado!"}""".toResponseBody(null)))
        coEvery { api.enviarMensagem(match { it.conteudos.single().conteudo == "boa" }) } returns
            MensagemCriadaDto(id = 600, conversaId = 42)
        coEvery { api.mensagens(42, 600, 0, 0) } throws IOException("caiu")

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        assertThat(banco.mensagemDao().buscar(ruim)!!.mensagem.falhou).isTrue()
        // A real não veio: a otimista vira a real com o id do servidor.
        assertThat(banco.mensagemDao().buscar(boa)).isNull()
        assertThat(banco.mensagemDao().buscar(600)!!.conteudos.single().conteudo).isEqualTo("boa")

        envio.reenviar(ruim)
        assertThat(banco.mensagemDao().buscar(ruim)!!.mensagem.falhou).isFalse()
        envio.descartar(ruim)
        assertThat(banco.mensagemDao().buscar(ruim)).isNull()
        assertThat(banco.envioPendenteDao().todos()).isEmpty()
    }

    @Test
    fun `erro do servidor conta tentativas e desiste na quinta`() = runTest {
        val envio = envio()
        val id = envio.enviarTexto(42, "x")
        coEvery { api.enviarMensagem(any()) } throws ErroApi.ServidorIndisponivel(502)

        repeat(EnvioMensagens.MAX_TENTATIVAS - 1) {
            assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.TENTAR_DEPOIS)
        }
        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isFalse()
        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isTrue()
    }
}
