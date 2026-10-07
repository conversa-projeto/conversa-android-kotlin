package com.conversa.app.core.data.sessao

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** AUT-03: erro que não é 401 mantém a sessão e tenta de novo a cada 5 s. */
class IniciadorSessaoTest {
    private val sessaoAtual = MutableStateFlow<Sessao?>(null)
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns sessaoAtual }
    private val conversas = mockk<ConversasRepositorio>()
    private val contatos = mockk<ContatosRepositorio>()
    private val envio = mockk<EnvioMensagens>(relaxed = true)
    private val autenticacao = mockk<AutenticacaoRepositorio> { coEvery { registrarDispositivo() } returns Result.success(Unit) }

    @Test
    fun `falha que nao e 401 aparece e tenta de novo ate dar certo`() = runTest {
        var tentativas = 0
        coEvery { conversas.atualizar() } answers {
            tentativas++
            if (tentativas < 3) Result.failure(ErroApi.ServidorIndisponivel(502)) else Result.success(Unit)
        }
        coEvery { contatos.atualizar() } returns Result.success(Unit)
        val escopo = escopoDoTeste()
        val iniciador = IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, escopo)
        iniciador.iniciar()

        sessaoAtual.value = Sessao(token = "t", usuarioId = 7, nome = "Ana")
        runCurrent()
        assertThat(iniciador.falha.value).isInstanceOf(ErroApi.ServidorIndisponivel::class.java)
        coVerify(exactly = 1) { autenticacao.registrarDispositivo() }

        advanceTimeBy(IniciadorSessao.INTERVALO_MS)
        runCurrent()
        assertThat(tentativas).isEqualTo(2)

        advanceTimeBy(IniciadorSessao.INTERVALO_MS)
        runCurrent()
        assertThat(tentativas).isEqualTo(3)
        assertThat(iniciador.carregada.value).isTrue()
        assertThat(iniciador.falha.value).isNull()

        advanceTimeBy(IniciadorSessao.INTERVALO_MS * 3)
        runCurrent()
        assertThat(tentativas).isEqualTo(3)
        escopo.cancel()
    }

    @Test
    fun `401 para de tentar`() = runTest {
        coEvery { conversas.atualizar() } returns Result.failure(ErroApi.SessaoExpirada("Token inválido"))
        coEvery { contatos.atualizar() } returns Result.success(Unit)
        val escopo = escopoDoTeste()
        val iniciador = IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, escopo)
        iniciador.iniciar()

        sessaoAtual.value = Sessao(token = "t", usuarioId = 7, nome = "Ana")
        runCurrent()
        advanceTimeBy(IniciadorSessao.INTERVALO_MS * 3)
        runCurrent()

        coVerify(exactly = 1) { conversas.atualizar() }
        assertThat(iniciador.falha.value).isNull()
        escopo.cancel()
    }

    @Test
    fun `sem sessao nao carrega nada`() = runTest {
        val escopo = escopoDoTeste()
        IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, escopo).iniciar()
        runCurrent()
        coVerify(exactly = 0) { conversas.atualizar() }
        escopo.cancel()
    }
}
