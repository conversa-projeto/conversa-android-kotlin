package com.conversa.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.pastas.PastasRepositorio
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Pasta
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Pastas de mensagens na conversa (7.14): seleção, pôr, a pasta aberta e as falhas. */
class PastasViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val pasta = Pasta(2, 5, 7, "Contratos", "", false, 0)
    private val lista = MutableStateFlow(listOf(pasta))
    private val mudaram = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    private val repositorio = mockk<PastasRepositorio>(relaxed = true) {
        every { observar(5) } returns lista
        every { mensagensMudaram } returns mudaram
        coEvery { carregar(5) } returns Result.success(listOf(pasta))
    }
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana")) }

    private fun criar() = PastasViewModel(SavedStateHandle(mapOf("conversaId" to 5L)), repositorio, sessao)

    private fun mensagem(id: Long) = Mensagem(
        id, 7, "Ana", 5,
        Instant.parse(
            "2026-10-17T12:00:00Z",
        ).plusSeconds(id),
        null, null, null, true, true, false, emptyList(),
    )

    @Test
    fun `so a direta le as pastas`() = runTest {
        val vm = criar()
        vm.usarConversa(false)
        advanceUntilIdle()
        assertThat(vm.pastas.value).isEmpty()
        coVerify(exactly = 0) { repositorio.carregar(any()) }
        vm.usarConversa(true)
        advanceUntilIdle()
        assertThat(vm.pastas.value).containsExactly(pasta)
        coVerify(exactly = 1) { repositorio.carregar(5) }
    }

    @Test
    fun `selecionar so as salvas e por na pasta limpa a selecao`() = runTest {
        coEvery { repositorio.por(pasta, any()) } returns Result.success(Unit)
        val vm = criar()
        vm.selecionar(-1)
        assertThat(vm.selecionadas.value).isEmpty()
        vm.selecionar(10)
        vm.alternarSelecao(11)
        vm.alternarSelecao(10)
        vm.alternarSelecao(12)
        assertThat(vm.selecionadas.value).containsExactly(11L, 12L)
        vm.porSelecionadas(pasta)
        advanceUntilIdle()
        coVerify { repositorio.por(pasta, match { it.toSet() == setOf(11L, 12L) }) }
        assertThat(vm.selecionadas.value).isEmpty()
    }

    @Test
    fun `falha ao por mantem a selecao e avisa com o motivo do servidor`() = runTest {
        coEvery { repositorio.por(pasta, any()) } returns
            Result.failure(ErroApi.Servidor(400, "Alguma das mensagens não é desta conversa."))
        val vm = criar()
        vm.selecionar(10)
        vm.porSelecionadas(pasta)
        advanceUntilIdle()
        assertThat(vm.selecionadas.value).containsExactly(10L)
        val falha = vm.falhas.first()
        assertThat(falha.acao).isEqualTo(AcaoPasta.POR)
        assertThat(falha.motivo).contains("não é desta conversa")
    }

    @Test
    fun `pasta aberta - paginas, tirar, reler no aviso e fechar quando some`() = runTest {
        val primeiraPagina = (51L..100L).map(::mensagem)
        coEvery { repositorio.mensagens(2, 0) } returns Result.success(primeiraPagina)
        coEvery { repositorio.mensagens(2, 51) } returns Result.success((1L..5L).map(::mensagem))
        coEvery { repositorio.tirar(pasta, listOf(100L)) } returns Result.success(Unit)
        val vm = criar()
        vm.usarConversa(true)
        vm.selecionar(9)
        vm.abrir(pasta)
        advanceUntilIdle()
        // Abrir a pasta limpa a seleção do chat.
        assertThat(vm.selecionadas.value).isEmpty()
        assertThat(vm.aberta.value!!.mensagens).hasSize(50)
        assertThat(vm.aberta.value!!.fim).isFalse()

        vm.carregarAnteriores()
        advanceUntilIdle()
        assertThat(vm.aberta.value!!.mensagens.first().id).isEqualTo(1L)
        assertThat(vm.aberta.value!!.mensagens).hasSize(55)
        assertThat(vm.aberta.value!!.fim).isTrue()

        vm.alternarSelecaoNaPasta(100)
        advanceUntilIdle()
        vm.tirar(vm.aberta.value!!.selecionadas)
        advanceUntilIdle()
        assertThat(vm.aberta.value!!.mensagens.map { it.id }).doesNotContain(100L)
        assertThat(vm.aberta.value!!.selecionadas).isEmpty()

        // A outra pessoa mexeu na pasta: relê do começo.
        mudaram.emit(2)
        advanceUntilIdle()
        coVerify(exactly = 2) { repositorio.mensagens(2, 0) }

        // A pasta foi excluída em outro aparelho: fecha.
        lista.value = emptyList()
        advanceUntilIdle()
        assertThat(vm.aberta.value).isNull()
    }
}
