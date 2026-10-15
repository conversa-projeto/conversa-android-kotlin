package com.conversa.app.feature.config

import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.Privacidade
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Privacidade (FC-810): carrega do servidor, muda só a chave tocada e volta se o servidor recusar. */
class PrivacidadeViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val perfil = mockk<PerfilRepositorio> {
        coEvery { privacidade() } returns Result.success(Privacidade(mostrarVistoEm = false))
    }

    @Test
    fun `carrega e muda so a chave tocada`() = runTest {
        coEvery { perfil.alterarPrivacidade(any(), any(), any()) } returns Result.success(Unit)
        val vm = PrivacidadeViewModel(perfil)
        advanceUntilIdle()
        assertThat(vm.estado.value.carregando).isFalse()
        assertThat(vm.estado.value.privacidade).isEqualTo(Privacidade(mostrarVistoEm = false))

        vm.alterar(OpcaoPrivacidade.APARECER_OFFLINE, true)
        assertThat(vm.estado.value.privacidade.aparecerOffline).isTrue()
        advanceUntilIdle()

        coVerify { perfil.alterarPrivacidade(aparecerOffline = true, mostrarVistoEm = null, mostrarNaConversa = null) }
        assertThat(vm.estado.value.salvando).isFalse()
    }

    @Test
    fun `recusado pelo servidor volta e mostra o erro`() = runTest {
        coEvery { perfil.alterarPrivacidade(any(), any(), any()) } returns Result.failure(java.io.IOException("sem rede"))
        val vm = PrivacidadeViewModel(perfil)
        advanceUntilIdle()

        vm.alterar(OpcaoPrivacidade.MOSTRAR_NA_CONVERSA, false)
        advanceUntilIdle()

        assertThat(vm.estado.value.privacidade.mostrarNaConversa).isTrue()
        assertThat(vm.estado.value.erro).isNotNull()
    }
}
