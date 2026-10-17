package com.conversa.app.core.data.pastas

import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.entidades.PastasDaMensagem
import com.conversa.app.core.model.Pasta
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarPastaRequisicao
import com.conversa.app.core.network.dto.CriarPastaRequisicao
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.MensagensDaPastaRequisicao
import com.conversa.app.core.network.dto.PastaDto
import com.conversa.app.core.network.dto.PorNaPastaDto
import com.conversa.app.core.network.dto.TirarDaPastaDto
import com.conversa.app.core.network.realtime.EventoSocket
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Pastas de mensagens (7.14, contrato §10.17): a lista na memória e as marcas no Room. */
class PastasRepositorioTest {
    private val api = mockk<ConversaApi>()
    private val dao = mockk<MensagemDao>(relaxed = true)
    private val repo = PastasRepositorio(api, dao)

    private fun dto(id: Long, nome: String, dono: Long = 7, compartilhada: Boolean = false, mensagens: Int = 0) =
        PastaDto(id, 5, dono, nome, "", compartilhada, mensagens)

    @Test
    fun `carregar ordena pelo nome e tira das mensagens as pastas que sumiram`() = runTest {
        coEvery { api.pastas(5) } returns listOf(dto(2, "contratos"), dto(1, "Banco"))
        coEvery { dao.pastasDaConversa(5) } returns listOf(PastasDaMensagem(10, "1,9"), PastasDaMensagem(11, "2"))
        val lista = repo.carregar(5).getOrThrow()
        assertThat(lista.map { it.nome }).containsExactly("Banco", "contratos").inOrder()
        assertThat(repo.observar(5).first()).isEqualTo(lista)
        // A 9 sumiu (excluída ou deixou de ser compartilhada): sai da mensagem 10; a 11 fica como está.
        coVerify { dao.atualizarPastas(10, "1") }
        coVerify(exactly = 0) { dao.atualizarPastas(11, any()) }
    }

    @Test
    fun `criar, renomear e excluir mudam a lista`() = runTest {
        coEvery { api.pastas(5) } returns emptyList()
        coEvery { dao.pastasDaConversa(5) } returns emptyList()
        repo.carregar(5)
        coEvery { api.criarPasta(CriarPastaRequisicao(5, "Viagem", "", false)) } returns dto(3, "Viagem")
        val nova = repo.criar(5, "  Viagem ").getOrThrow()
        assertThat(repo.observar(5).first()).containsExactly(nova)

        coEvery { api.alterarPasta(AlterarPastaRequisicao(3, cor = "#22c55e")) } returns dto(3, "Viagem").copy(cor = "#22c55e")
        repo.alterar(nova, cor = "#22c55e").getOrThrow()
        assertThat(repo.observar(5).first().single().cor).isEqualTo("#22c55e")

        coEvery { api.excluirPasta(3) } returns IdDto(3)
        repo.excluir(nova).getOrThrow()
        assertThat(repo.observar(5).first()).isEmpty()
    }

    @Test
    fun `por so as mensagens salvas, de 500 em 500, marcando no Room`() = runTest {
        val pasta = Pasta(2, 5, 7, "Contratos", "", false, 0)
        coEvery { api.porNaPasta(any()) } returns PorNaPastaDto(2, 1)
        coEvery { api.pastas(5) } returns listOf(dto(2, "Contratos", mensagens = 2))
        coEvery { dao.pastasDas(listOf(10L, 11L)) } returns listOf(PastasDaMensagem(10, ""), PastasDaMensagem(11, "2"))
        coEvery { dao.pastasDaConversa(5) } returns emptyList()

        repo.por(pasta, listOf(-3, 10, 11, 10)).getOrThrow()

        coVerify(exactly = 1) { api.porNaPasta(MensagensDaPastaRequisicao(2, listOf(10, 11))) }
        coVerify { dao.atualizarPastas(10, "2") }
        coVerify(exactly = 0) { dao.atualizarPastas(11, any()) }
        assertThat(repo.observar(5).first().single().mensagens).isEqualTo(2)

        val muitas = (1L..1200L).toList()
        repo.por(pasta, muitas).getOrThrow()
        coVerify(exactly = 3) { api.porNaPasta(match { it.mensagens.size <= 500 && it.mensagens.first() in setOf(1L, 501L, 1001L) }) }
    }

    @Test
    fun `tirar desmarca e o aviso 67 marca e rele so a conversa aberta`() = runTest {
        val pasta = Pasta(2, 5, 7, "Contratos", "", true, 1)
        coEvery { api.tirarDaPasta(MensagensDaPastaRequisicao(2, listOf(10))) } returns TirarDaPastaDto(2, 1)
        coEvery { api.pastas(5) } returns listOf(dto(2, "Contratos", compartilhada = true))
        coEvery { dao.pastasDas(listOf(10L)) } returns listOf(PastasDaMensagem(10, "1,2"))
        coEvery { dao.pastasDaConversa(5) } returns emptyList()
        repo.tirar(pasta, listOf(10)).getOrThrow()
        coVerify { dao.atualizarPastas(10, "1") }

        // A outra pessoa pôs a 12 na compartilhada.
        coEvery { dao.pastasDas(listOf(12L)) } returns listOf(PastasDaMensagem(12, ""))
        repo.aoAviso(EventoSocket.PastasAtualizadas(5, 2, listOf(12), "incluir"))
        coVerify { dao.atualizarPastas(12, "2") }
        coVerify(exactly = 2) { api.pastas(5) }

        // Conversa que nunca foi aberta: só as marcas, sem reler a lista.
        coEvery { dao.pastasDas(listOf(30L)) } returns listOf(PastasDaMensagem(30, "4"))
        repo.aoAviso(EventoSocket.PastasAtualizadas(9, 4, listOf(30), "tirar"))
        coVerify { dao.atualizarPastas(30, "") }
        coVerify(exactly = 0) { api.pastas(9) }
    }
}
