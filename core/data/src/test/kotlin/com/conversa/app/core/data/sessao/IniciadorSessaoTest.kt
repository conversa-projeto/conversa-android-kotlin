package com.conversa.app.core.data.sessao

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.RealtimeClient
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
    private val naFila = MutableStateFlow(0)
    private val envio = mockk<EnvioMensagens>(relaxed = true) { every { quantidadeNaFila } returns naFila }
    private val autenticacao = mockk<AutenticacaoRepositorio> { coEvery { registrarDispositivo() } returns Result.success(Unit) }
    private val estadoTempoReal = MutableStateFlow(EstadoConexao.DESCONECTADO)
    private val tempoReal = mockk<RealtimeClient> { every { estado } returns estadoTempoReal }

    @Test
    fun `falha que nao e 401 aparece e tenta de novo ate dar certo`() = runTest {
        var tentativas = 0
        coEvery { conversas.atualizar() } answers {
            tentativas++
            if (tentativas < 3) Result.failure(ErroApi.ServidorIndisponivel(502)) else Result.success(Unit)
        }
        coEvery { contatos.atualizar() } returns Result.success(Unit)
        val escopo = escopoDoTeste()
        val iniciador = IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, tempoReal, escopo)
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
        val iniciador = IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, tempoReal, escopo)
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
        IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, tempoReal, escopo).iniciar()
        runCurrent()
        coVerify(exactly = 0) { conversas.atualizar() }
        escopo.cancel()
    }

    @Test
    fun `com o tempo real conectado, a fila sai a cada reconexao e a cada mensagem nova`() = runTest {
        coEvery { conversas.atualizar() } returns Result.success(Unit)
        coEvery { contatos.atualizar() } returns Result.success(Unit)
        val escopo = escopoDoTeste()
        IniciadorSessao(sessao, conversas, contatos, autenticacao, envio, tempoReal, escopo).iniciar()

        sessaoAtual.value = Sessao(token = "t", usuarioId = 7, nome = "Ana")
        runCurrent()
        coVerify(exactly = 1) { envio.retomar() }
        coVerify(exactly = 0) { envio.enviarAoConectar() }

        // Conectado com a fila vazia: nada.
        estadoTempoReal.value = EstadoConexao.CONECTADO
        runCurrent()
        coVerify(exactly = 0) { envio.enviarAoConectar() }

        // Mensagem nova com o tempo real conectado: sai já.
        naFila.value = 1
        runCurrent()
        coVerify(exactly = 1) { envio.enviarAoConectar() }

        // Não saiu (servidor com erro): espera a reconexão, ou outra mensagem.
        estadoTempoReal.value = EstadoConexao.CONECTANDO
        runCurrent()
        estadoTempoReal.value = EstadoConexao.CONECTADO
        runCurrent()
        coVerify(exactly = 2) { envio.enviarAoConectar() }

        // Fila na mesma, sem tempo real: nada.
        estadoTempoReal.value = EstadoConexao.DESCONECTADO
        naFila.value = 2
        runCurrent()
        coVerify(exactly = 2) { envio.enviarAoConectar() }
        escopo.cancel()
    }
}
