package com.conversa.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.conversa.app.core.data.ia.ResumosRepositorio
import com.conversa.app.core.data.sistema.RecursosRepositorio
import com.conversa.app.core.model.PeriodoResumo
import com.conversa.app.core.model.Recursos
import com.conversa.app.core.model.ResumoConversa
import com.conversa.app.core.model.ResumoSalvo
import com.conversa.app.core.model.StatusResumo
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** "Resumir conversa" (CON-14). */
class ResumoViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val guardado = MutableStateFlow<ResumoSalvo?>(null)
    private val resumos = mockk<ResumosRepositorio> { every { observar(42) } returns guardado }
    private val recursos = mockk<RecursosRepositorio> {
        every { this@mockk.recursos } returns
            MutableStateFlow(Recursos(transcricao = false, ia = true))
    }

    private fun criar() = ResumoViewModel(SavedStateHandle(mapOf("conversaId" to 42L)), resumos, recursos)

    private fun resumo(status: StatusResumo, periodo: PeriodoResumo = PeriodoResumo.DIAS_7) =
        ResumoSalvo(ResumoConversa("abc", 42, periodo, status, 37, emptyList(), ""), Instant.EPOCH)

    @Test
    fun `reabrir volta com o periodo usado e continua consultando so com a folha aberta`() = runTest {
        guardado.value = resumo(StatusResumo.PROCESSANDO)
        coEvery { resumos.consultar(any()) } returns Result.success(resumo(StatusResumo.PROCESSANDO).resumo)
        val vm = criar()
        runCurrent()
        assertThat(vm.disponivel.value).isTrue()
        assertThat(vm.estado.value.periodo).isEqualTo(PeriodoResumo.DIAS_7)
        assertThat(vm.estado.value.processando).isTrue()

        // Fechada, não consulta.
        advanceTimeBy(5_000)
        coVerify(exactly = 0) { resumos.consultar(any()) }

        vm.abrir()
        advanceTimeBy(ResumoViewModel.INTERVALO_CONSULTA_MS + 1)
        coVerify(exactly = 1) { resumos.consultar(any()) }

        // Pronto: para de consultar.
        guardado.value = resumo(StatusResumo.CONCLUIDO)
        advanceTimeBy(10_000)
        coVerify(exactly = 1) { resumos.consultar(any()) }
        vm.fechar()
    }

    @Test
    fun `pedir com o periodo escolhido e mostrar o motivo quando o servidor recusa`() = runTest {
        val motivo = "IA não configurada: defina o endereço e o modelo nas configurações do sistema."
        coEvery { resumos.pedir(42, PeriodoResumo.DIAS_30) } returns Result.failure(ErroApi.Servidor(400, motivo))
        val vm = criar()
        vm.abrir()
        vm.escolher(PeriodoResumo.DIAS_30)
        vm.resumir()
        advanceUntilIdle()

        coVerify { resumos.pedir(42, PeriodoResumo.DIAS_30) }
        assertThat(vm.estado.value.erroAoPedir).isEqualTo(motivo)
        assertThat(vm.estado.value.processando).isFalse()
        vm.fechar()
    }
}
