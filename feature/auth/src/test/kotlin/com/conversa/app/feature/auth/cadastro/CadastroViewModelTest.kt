package com.conversa.app.feature.auth.cadastro

import app.cash.turbine.test
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.autenticacao.EmailJaCadastradoException
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.feature.auth.R
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CadastroViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val autenticacao = mockk<AutenticacaoRepositorio>()

    private fun preenchido() = CadastroViewModel(autenticacao).apply {
        alterarNome("Ana Souza")
        alterarUsuario("ana")
        alterarEmail("ana@x.com")
        alterarSenha("1234")
    }

    @Test
    fun `limites das colunas sao respeitados ao digitar`() {
        val vm = CadastroViewModel(autenticacao)
        vm.alterarNome("n".repeat(150))
        vm.alterarUsuario("u".repeat(80))
        vm.alterarEmail("e".repeat(150))
        assertThat(vm.estado.value.nome).hasLength(100)
        assertThat(vm.estado.value.usuario).hasLength(50)
        assertThat(vm.estado.value.email).hasLength(100)
    }

    @Test
    fun `validacoes antes de chamar a API`() = runTest {
        val vm = preenchido()
        vm.alterarNome(" ")
        vm.cadastrar()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.cadastro_preencha))

        vm.alterarNome("Ana")
        vm.alterarEmail("sem-arroba")
        vm.cadastrar()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.cadastro_email_invalido))

        vm.alterarEmail("ana@x.com")
        vm.alterarSenha("123")
        vm.cadastrar()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.cadastro_senha_curta))
        coVerify(exactly = 0) { autenticacao.cadastrar(any(), any(), any(), any()) }
    }

    @Test
    fun `sucesso volta ao login com o usuario`() = runTest {
        coEvery { autenticacao.cadastrar(any(), any(), any(), any()) } returns Result.success(Unit)
        val vm = preenchido()
        vm.alterarUsuario("  ana  ")
        vm.eventos.fluxo.test {
            vm.cadastrar()
            assertThat(awaitItem()).isEqualTo(EventoCadastro.Criada("ana"))
        }
    }

    @Test
    fun `email repetido e login repetido`() = runTest {
        coEvery { autenticacao.cadastrar(any(), any(), any(), any()) } returns Result.failure(EmailJaCadastradoException())
        val vm = preenchido()
        vm.cadastrar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.cadastro_email_repetido))

        coEvery { autenticacao.cadastrar(any(), any(), any(), any()) } returns
            Result.failure(ErroApi.Servidor(400, "Login já cadastrado!"))
        vm.cadastrar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Literal("Login já cadastrado!"))
    }
}
