package com.conversa.app.feature.atividades

import com.conversa.app.core.data.atividades.AtividadesRepositorio
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.model.TipoAtividade
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Aba Atividades (TODO 8.1): abrir, paginar até o fim e fechar. */
class AtividadesViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val lista = MutableStateFlow<List<Atividade>>(emptyList())
    private val repo = mockk<AtividadesRepositorio>(relaxed = true) { every { observar() } returns lista }

    private fun atividade(id: Long) = Atividade(
        id, TipoAtividade.RESPOSTA, null, true, 2, "Bia", null, 9, null, null, 5, null, "oi", null, null, null,
    )

    @Test
    fun `abrir carrega, e perto do fim busca as anteriores ate acabar`() = runTest {
        coEvery { repo.abrir() } returns Result.success(false)
        coEvery { repo.carregarMais(71) } returns Result.success(true)
        val vm = AtividadesViewModel(repo)
        backgroundScope.launch { vm.estado.collect {} }

        vm.abrir()
        lista.value = (100L downTo 71L).map(::atividade)
        advanceUntilIdle()
        assertThat(vm.estado.value.carregando).isFalse()
        assertThat(vm.estado.value.atividades).hasSize(30)

        vm.carregarMais()
        advanceUntilIdle()
        vm.carregarMais()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.carregarMais(71) }
        assertThat(vm.estado.value.fim).isTrue()
        vm.fechar()
        verify { repo.fechar() }
    }

    @Test
    fun `erro sem nada no aparelho mostra a mensagem`() = runTest {
        coEvery { repo.abrir() } returns Result.failure(java.io.IOException("sem rede"))
        val vm = AtividadesViewModel(repo)
        backgroundScope.launch { vm.estado.collect {} }

        vm.abrir()
        advanceUntilIdle()

        assertThat(vm.estado.value.erro).isNotNull()
        assertThat(vm.estado.value.carregando).isFalse()
    }
}
