package com.conversa.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.AnexoDaConversa
import com.conversa.app.core.model.DirecaoAnexos
import com.conversa.app.core.model.FiltroAnexos
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Anexos da conversa (TODO 8.6): filtros, páginas de 60 e o fim da lista. */
class AnexosDaConversaViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val conversas = mockk<ConversasRepositorio> { every { observar(3) } returns flowOf(null) }
    private val anexos = mockk<AnexosRepositorio>()

    private fun anexo(id: Long) = AnexoDaConversa(id, "id$id", "a$id.pdf", "pdf", 10, null, TipoConteudo.ARQUIVO, id * 10, 3, "Ana")

    private fun pagina(de: Long, quantos: Int) = (0 until quantos).map { anexo(de - it) }

    private fun criar() = AnexosDaConversaViewModel(SavedStateHandle(mapOf("conversaId" to 3L)), conversas, anexos, mockk(), mockk())

    @Test
    fun `primeira pagina, a seguinte pelo ultimo anexo, e o fim com menos de 60`() = runTest {
        coEvery { anexos.daConversa(3, DirecaoAnexos.TODOS, FiltroAnexos.TODOS, 0) } returns Result.success(pagina(200, 60))
        coEvery { anexos.daConversa(3, DirecaoAnexos.TODOS, FiltroAnexos.TODOS, 141) } returns Result.success(pagina(140, 5))
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()
        assertThat(vm.estado.value.itens).hasSize(60)
        assertThat(vm.estado.value.fim).isFalse()

        vm.carregarMais()
        advanceUntilIdle()
        assertThat(vm.estado.value.itens).hasSize(65)
        assertThat(vm.estado.value.fim).isTrue()

        vm.carregarMais()
        advanceUntilIdle()
        coVerify(exactly = 2) { anexos.daConversa(any(), any(), any(), any()) }
    }

    @Test
    fun `filtro novo recomeca a lista, e o erro aparece com tentar de novo`() = runTest {
        coEvery { anexos.daConversa(3, DirecaoAnexos.TODOS, FiltroAnexos.TODOS, 0) } returns Result.success(pagina(200, 3))
        coEvery { anexos.daConversa(3, DirecaoAnexos.RECEBIDOS, FiltroAnexos.TODOS, 0) } returns
            Result.failure(ErroApi.SemConexao(java.io.IOException("sem rede")))
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        vm.mudarDirecao(DirecaoAnexos.RECEBIDOS)
        advanceUntilIdle()
        assertThat(vm.estado.value.itens).isEmpty()
        assertThat(vm.estado.value.erro).isNotNull()

        coEvery { anexos.daConversa(3, DirecaoAnexos.RECEBIDOS, FiltroAnexos.TODOS, 0) } returns Result.success(pagina(90, 2))
        vm.tentarDeNovo()
        advanceUntilIdle()
        assertThat(vm.estado.value.itens.map { it.anexoId }).containsExactly(90L, 89L).inOrder()
        assertThat(vm.estado.value.erro).isNull()
    }
}
