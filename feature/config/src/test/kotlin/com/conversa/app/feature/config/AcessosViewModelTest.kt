package com.conversa.app.feature.config

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.sistema.SistemaRepositorio
import com.conversa.app.core.model.Acessos
import com.conversa.app.core.model.PermissaoSistema
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.UsuarioAcessos
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Acessos (TODO 8.5): a caixa só muda quando o servidor aceita. */
class AcessosViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val acessos = Acessos(
        permissoes = listOf(PermissaoSistema("permissoes", "Conceder e retirar")),
        usuarios = listOf(UsuarioAcessos(7, "Ana", "ana", setOf("permissoes")), UsuarioAcessos(8, "Bia", "bia", emptySet())),
        modoAberto = false,
    )
    private val sistema = mockk<SistemaRepositorio> { coEvery { acessos() } returns Result.success(acessos) }
    private val sessoes = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("tok", 7, "Ana")) }

    @Test
    fun `aceito marca a caixa, recusado mostra o motivo e a caixa fica como estava`() = runTest {
        val vm = AcessosViewModel(sistema, sessoes)
        advanceUntilIdle()
        assertThat(vm.estado.value.eu).isEqualTo(7)

        coEvery { sistema.conceder(8, "permissoes") } returns Result.success(Unit)
        vm.alternar(8, "permissoes", conceder = true)
        advanceUntilIdle()
        assertThat(vm.estado.value.acessos!!.usuarios[1].permissoes).containsExactly("permissoes")

        val motivo = "Ao menos uma pessoa precisa poder gerenciar as permissões."
        coEvery { sistema.retirar(7, "permissoes") } returns Result.failure(ErroApi.Servidor(400, motivo))
        vm.alternar(7, "permissoes", conceder = false)
        advanceUntilIdle()
        assertThat(vm.estado.value.acessos!!.usuarios[0].permissoes).containsExactly("permissoes")
        assertThat(vm.estado.value.aviso).isEqualTo(Aviso(ok = false, texto = motivo))
        assertThat(vm.estado.value.alterando).isNull()
    }
}
