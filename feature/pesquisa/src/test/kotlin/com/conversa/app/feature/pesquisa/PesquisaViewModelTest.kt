package com.conversa.app.feature.pesquisa

import androidx.lifecycle.SavedStateHandle
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Pesquisa em todos os chats (TODO 8.2). */
class PesquisaViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val conversas = mockk<ConversasRepositorio> {
        every { observarTodas() } returns
            MutableStateFlow(listOf(Conversa(42, TipoConversa.GRUPO, "Família", null, 0, 0, null, null, 0, null, null, null)))
    }
    private val mensagens = mockk<MensagensRepositorio>()

    private fun mensagem(id: Long) = Mensagem(
        id, 1, "Ana", 42, Instant.EPOCH, null, null, null, true, true, false, listOf(Conteudo(id, 1, TipoConteudo.TEXTO, "bolo")),
    )

    @Test
    fun `abre ja pesquisando o termo da lista e guarda os nomes das conversas`() = runTest {
        coEvery { mensagens.pesquisar("bolo") } returns Result.success(listOf(mensagem(1)))
        val vm = PesquisaViewModel(SavedStateHandle(mapOf("termo" to "bolo")), conversas, mensagens)
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertThat(vm.estado.value.resultados!!.map { it.id }).containsExactly(1L)
        assertThat(vm.estado.value.termo).isEqualTo("bolo")
        assertThat(vm.estado.value.titulos[42]).isEqualTo("Família")
    }

    @Test
    fun `termo vazio nao pesquisa e erro vira lista vazia com a mensagem`() = runTest {
        coEvery { mensagens.pesquisar("x") } returns Result.failure(java.io.IOException("sem rede"))
        val vm = PesquisaViewModel(SavedStateHandle(), conversas, mensagens)
        backgroundScope.launch { vm.estado.collect {} }

        vm.pesquisar("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { mensagens.pesquisar(any(), any()) }
        assertThat(vm.estado.value.resultados).isNull()

        vm.pesquisar("x")
        advanceUntilIdle()
        assertThat(vm.estado.value.resultados).isEmpty()
        assertThat(vm.estado.value.erro).isNotNull()
    }
}
