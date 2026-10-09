package com.conversa.app.feature.config

import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.model.BandaVideo
import com.conversa.app.core.model.ConfigChamada
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Qualidade das chamadas (TODO 8.5). */
class QualidadeChamadasViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val guardada = MutableStateFlow(ConfigChamada.PADRAO)
    private val preferencias = mockk<PreferenciasRepositorio> {
        every { chamada } returns guardada
        coEvery { alterarChamada(any()) } answers { guardada.value = firstArg() }
    }

    @Test
    fun `cada mudanca guarda a configuracao inteira, e restaurar volta ao padrao`() = runTest {
        val vm = QualidadeChamadasViewModel(preferencias)
        backgroundScope.launch { vm.config.collect {} }

        vm.alterar { it.copy(cancelamentoEco = false) }
        advanceUntilIdle()
        vm.alterar { it.copy(banda = BandaVideo.ECONOMICA) }
        advanceUntilIdle()
        assertThat(vm.config.value).isEqualTo(ConfigChamada(cancelamentoEco = false, banda = BandaVideo.ECONOMICA))

        vm.restaurarPadrao()
        advanceUntilIdle()
        assertThat(vm.config.value).isEqualTo(ConfigChamada.PADRAO)
    }
}
