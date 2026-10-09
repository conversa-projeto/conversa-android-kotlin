package com.conversa.app.core.data.atividades

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AtividadeDto
import com.conversa.app.core.network.dto.QuantidadeDto
import com.conversa.app.core.network.dto.VistasDto
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

    private fun pagina(de: Long, quantas: Int) = (0 until quantas).map { AtividadeDto(id = de - it, tipo = 1, nova = true) }

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
    fun `abrir marca como vistas e zera o badge, e o aviso com a aba aberta rele, fechada so conta`() = runTest {
        val repo = AtividadesRepositorio(api, banco.atividadeDao())
        coEvery { api.atividadesNovas() } returns QuantidadeDto(4)
        coEvery { api.atividades(0, 30) } returns pagina(10, 3)
        coEvery { api.marcarAtividadesVistas() } returns VistasDto()

        repo.aoReceberAviso()
        assertThat(repo.novas.value).isEqualTo(4)
        coVerify(exactly = 0) { api.atividades(any(), any()) }

        repo.abrir()
        assertThat(repo.novas.value).isEqualTo(0)
        repo.aoReceberAviso()
        coVerify(exactly = 2) { api.atividades(0, 30) }
        coVerify(exactly = 2) { api.marcarAtividadesVistas() }

        repo.fechar()
        repo.aoReceberAviso()
        assertThat(repo.novas.value).isEqualTo(4)
    }
}
