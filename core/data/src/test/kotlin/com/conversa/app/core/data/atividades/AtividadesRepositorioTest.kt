package com.conversa.app.core.data.atividades

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AtividadeDto
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.QuantidadeDto
import com.conversa.app.core.network.dto.SucessoDto
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lista, paginação e contador das atividades (TODO 8.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AtividadesRepositorioTest {
    private lateinit var banco: ConversaBanco
    private val api = mockk<ConversaApi>()

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun pagina(de: Long, quantas: Int) = (0 until quantas).map { AtividadeDto(id = de - it, tipo = 1, pendente = true) }

    @Test
    fun `primeira pagina troca tudo e as seguintes somam, ate vir menos de 30`() = runTest {
        val repo = AtividadesRepositorio(api, banco.atividadeDao())
        coEvery { api.atividades(0, 30) } returns pagina(100, 30)
        coEvery { api.atividades(71, 30) } returns pagina(70, 5)

        assertThat(repo.carregar().getOrThrow()).isFalse()
        assertThat(repo.carregarMais(71).getOrThrow()).isTrue()

        val lista = repo.observar().first()
        assertThat(lista).hasSize(35)
        assertThat(lista.first().id).isEqualTo(100)
        // Relida a primeira página, o que sumiu do servidor some daqui.
        coEvery { api.atividades(0, 30) } returns pagina(100, 2)
        repo.carregar()
        assertThat(repo.observar().first().map { it.id }).containsExactly(100L, 99L).inOrder()
    }

    @Test
    fun `abrir rele a lista e o contador de pendentes, sem zerar nem marcar como vistas`() = runTest {
        val repo = AtividadesRepositorio(api, banco.atividadeDao())
        coEvery { api.atividadesNovas() } returns QuantidadeDto(4)
        coEvery { api.atividades(0, 30) } returns pagina(10, 3)

        // Sem lista guardada e com a aba fechada, o aviso só conta.
        repo.aoReceberAviso()
        assertThat(repo.novas.value).isEqualTo(4)
        coVerify(exactly = 0) { api.atividades(any(), any()) }

        repo.abrir()
        assertThat(repo.novas.value).isEqualTo(4)
        repo.aoReceberAviso()
        coVerify(exactly = 2) { api.atividades(0, 30) }
        coVerify(exactly = 0) { api.marcarAtividadesVistas() }
    }

    @Test
    fun `concluir tira a marca de pendente e desconta o contador, uma vez`() = runTest {
        val repo = AtividadesRepositorio(api, banco.atividadeDao())
        coEvery { api.atividadesNovas() } returns QuantidadeDto(3)
        coEvery { api.atividades(0, 30) } returns pagina(10, 3)
        coEvery { api.concluirAtividade(IdDto(9)) } returns SucessoDto(true)
        repo.abrir()
        val nona = repo.observar().first().first { it.id == 9L }

        repo.concluir(nona).getOrThrow()
        repo.concluir(repo.observar().first().first { it.id == 9L }).getOrThrow()

        assertThat(repo.observar().first().first { it.id == 9L }.pendente).isFalse()
        assertThat(repo.novas.value).isEqualTo(2)
        coVerify(exactly = 1) { api.concluirAtividade(IdDto(9)) }
    }

    @Test
    fun `aviso com a aba fechada rele a lista guardada e a resposta lida deixa de estar pendente`() = runTest {
        val repo = AtividadesRepositorio(api, banco.atividadeDao())
        coEvery { api.atividades(0, 30) } returns pagina(10, 3)
        repo.carregar()
        // A resposta 10 foi lida no chat: o servidor manda o WS 61 e ela não está mais pendente.
        coEvery { api.atividadesNovas() } returns QuantidadeDto(2)
        coEvery { api.atividades(0, 30) } returns pagina(10, 3).map { if (it.id == 10L) it.copy(pendente = false) else it }

        repo.aoReceberAviso()

        assertThat(repo.novas.value).isEqualTo(2)
        assertThat(repo.observar().first().first { it.id == 10L }.pendente).isFalse()
        coVerify(exactly = 0) { api.marcarAtividadesVistas() }
    }
}
