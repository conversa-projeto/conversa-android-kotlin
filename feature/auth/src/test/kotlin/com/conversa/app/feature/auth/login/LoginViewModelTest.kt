package com.conversa.app.feature.auth.login

import app.cash.turbine.test
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.autenticacao.RespostaLoginInvalidaException
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.feature.auth.R
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val autenticacao = mockk<AutenticacaoRepositorio> { every { ultimoLogin } returns flowOf("ana") }
    private val servidor = mockk<ServidorRepositorio> {
        every { atual } returns MutableStateFlow(ServerConfig.aPartirDe("https://192.168.2.5"))
    }

    private fun criar() = LoginViewModel(autenticacao, servidor)

    @Test
    fun `preenche o ultimo login e o servidor`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        assertThat(vm.estado.value.usuario).isEqualTo("ana")
        assertThat(vm.estado.value.servidor).isEqualTo("192.168.2.5")
    }

    @Test
    fun `usuario vindo do cadastro tem prioridade`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        vm.preencherUsuario("novo")
        assertThat(vm.estado.value.usuario).isEqualTo("novo")
    }

    @Test
    fun `campos vazios nao chamam a API`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        vm.entrar()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.login_preencha))
        coVerify(exactly = 0) { autenticacao.entrar(any(), any()) }
    }

    @Test
    fun `sucesso limpa a senha e avisa a tela`() = runTest {
        coEvery { autenticacao.entrar("ana", "segredo") } returns Result.success(Sessao("t", 7, "Ana"))
        val vm = criar()
        advanceUntilIdle()
        vm.alterarSenha("segredo")
        vm.eventos.fluxo.test {
            vm.entrar()
            assertThat(awaitItem()).isEqualTo(EventoLogin.Entrou)
        }
        assertThat(vm.estado.value.senha).isEmpty()
        assertThat(vm.estado.value.entrando).isFalse()
    }

    @Test
    fun `erro do servidor aparece como veio`() = runTest {
        coEvery { autenticacao.entrar(any(), any()) } returns Result.failure(ErroApi.Servidor(401, "Senha incorreta!"))
        val vm = criar()
        advanceUntilIdle()
        vm.alterarSenha("x")
        vm.entrar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Literal("Senha incorreta!"))
    }

    @Test
    fun `limite de tentativas e resposta sem token`() = runTest {
        coEvery { autenticacao.entrar(any(), any()) } returns Result.failure(ErroApi.MuitasTentativas())
        val vm = criar()
        advanceUntilIdle()
        vm.alterarSenha("x")
        vm.entrar()
        advanceUntilIdle()
        assertThat((vm.estado.value.erro as TextoUi.Literal).texto).contains("Muitas tentativas")

        coEvery { autenticacao.entrar(any(), any()) } returns Result.failure(RespostaLoginInvalidaException())
        vm.entrar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.login_resposta_invalida))
    }
}
