package com.conversa.app.feature.conversas

import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.AvatarDoGrupo
import com.conversa.app.core.data.conversas.AvatarGrupo
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.conversas.ImagemIlegivelException
import com.conversa.app.core.model.Contato
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.feature.conversas.grupo.CriarGrupoViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CriarGrupoViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val conversas = mockk<ConversasRepositorio> { every { observarTodas() } returns MutableStateFlow(emptyList()) }
    private val contatos = mockk<ContatosRepositorio> {
        every { observarOutros() } returns MutableStateFlow(
            listOf(Contato(8, "Bruno", "bruno", null, null), Contato(9, "Carla", "carla", null, null)),
        )
    }

    private val avatar = mockk<AvatarDoGrupo>()

    @Test
    fun `validacoes do web antes de criar`() = runTest {
        val vm = CriarGrupoViewModel(conversas, contatos, avatar)
        backgroundScope.launch { vm.estado.collect {} }
        vm.criar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.criar_grupo_sem_nome))

        vm.alterarNome("Projeto")
        vm.criar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.criar_grupo_sem_membros))
        coVerify(exactly = 0) { conversas.criarGrupo(any(), any()) }
    }

    @Test
    fun `filtro mantem os marcados visiveis e criar abre o grupo`() = runTest {
        coEvery { conversas.criarGrupo("Projeto", setOf(8L)) } returns Result.success(77)
        val vm = CriarGrupoViewModel(conversas, contatos, avatar)
        backgroundScope.launch { vm.estado.collect {} }
        vm.alterarNome("Projeto")
        vm.alternar(8)
        vm.alterarTermo("carla")
        advanceUntilIdle()
        assertThat(vm.estado.value.contatos.map { it.id }).containsExactly(8L, 9L)

        vm.criar()
        advanceUntilIdle()
        assertThat(vm.aoCriar.fluxo.first()).isEqualTo(77)
    }

    @Test
    fun `erro do servidor aparece e libera o botao`() = runTest {
        coEvery { conversas.criarGrupo(any(), any()) } returns Result.failure(ErroApi.Servidor(403, "Acesso negado!"))
        val vm = CriarGrupoViewModel(conversas, contatos, avatar)
        backgroundScope.launch { vm.estado.collect {} }
        vm.alterarNome("Projeto")
        vm.alternar(9)
        vm.criar()
        advanceUntilIdle()
        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Literal("Acesso negado!"))
        assertThat(vm.estado.value.criando).isFalse()
    }

    @Test
    fun `emoji escolhido vai no grupo depois de criar, e imagem tira o emoji`() = runTest {
        coEvery { conversas.criarGrupo("Festa", setOf(8L)) } returns Result.success(78)
        coEvery { avatar.alterar(78, AvatarGrupo.Emoji("🎉")) } returns Result.success(Unit)
        val vm = CriarGrupoViewModel(conversas, contatos, avatar)
        backgroundScope.launch { vm.estado.collect {} }
        val uri = mockk<android.net.Uri>()
        vm.escolherImagem(uri)
        vm.escolherEmoji("🎉")
        advanceUntilIdle()
        assertThat(vm.estado.value.imagem).isNull()
        assertThat(vm.estado.value.emoji).isEqualTo("🎉")

        vm.alterarNome("Festa")
        vm.alternar(8)
        vm.criar()
        advanceUntilIdle()

        coVerify { avatar.alterar(78, AvatarGrupo.Emoji("🎉")) }
        assertThat(vm.aoCriar.fluxo.first()).isEqualTo(78)
    }

    @Test
    fun `imagem que nao abre nao cria o grupo e avisa`() = runTest {
        val uri = mockk<android.net.Uri>()
        coEvery { avatar.enviarImagem(uri) } returns Result.failure(ImagemIlegivelException())
        val vm = CriarGrupoViewModel(conversas, contatos, avatar)
        backgroundScope.launch { vm.estado.collect {} }
        vm.escolherImagem(uri)
        vm.alterarNome("Fotos")
        vm.alternar(9)
        vm.criar()
        advanceUntilIdle()

        assertThat(vm.estado.value.erro).isEqualTo(TextoUi.Recurso(R.string.grupo_imagem_ilegivel))
        assertThat(vm.estado.value.criando).isFalse()
        coVerify(exactly = 0) { conversas.criarGrupo(any(), any()) }
    }
}
