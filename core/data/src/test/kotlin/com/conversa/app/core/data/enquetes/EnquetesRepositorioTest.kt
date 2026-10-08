package com.conversa.app.core.data.enquetes

import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.EnqueteDto
import com.conversa.app.core.network.dto.PrazoEnqueteRequisicao
import com.conversa.app.core.network.dto.VotarEnqueteRequisicao
import com.conversa.app.core.network.json.nuloExplicito
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Cache e deduplicação das enquetes (TODO 7.12). */
class EnquetesRepositorioTest {
    private val api = mockk<ConversaApi>()

    private fun dto(votantes: Int = 0, meus: List<Long> = emptyList()) =
        EnqueteDto(id = 42, conversaId = 50, pergunta = "Onde?", totalVotantes = votantes, meusVotos = meus)

    @Test
    fun `leituras simultaneas da mesma enquete viram uma so`() = runTest {
        coEvery { api.enquete(42) } coAnswers {
            delay(100)
            dto()
        }
        val repo = EnquetesRepositorio(api, backgroundScope)

        val a = async { repo.carregar(42) }
        val b = async { repo.carregar(42) }

        assertThat(a.await().getOrNull()!!.id).isEqualTo(42)
        assertThat(b.await().getOrNull()!!.id).isEqualTo(42)
        coVerify(exactly = 1) { api.enquete(42) }
        assertThat(repo.observar(42).first()).isNotNull()
    }

    @Test
    fun `votar substitui o cache e o WS 62 so rele o que esta em cache`() = runTest {
        coEvery { api.votarEnquete(VotarEnqueteRequisicao(42, listOf(1))) } returns dto(votantes = 1, meus = listOf(1))
        coEvery { api.enquete(any()) } returns dto(votantes = 2)
        val repo = EnquetesRepositorio(api, backgroundScope)

        repo.aoAtualizar(42)
        coVerify(exactly = 0) { api.enquete(any()) }

        repo.votar(42, listOf(1))
        assertThat(repo.observar(42).first()!!.meusVotos).containsExactly(1L)

        repo.aoAtualizar(42)
        assertThat(repo.observar(42).first()!!.totalVotantes).isEqualTo(2)
    }

    @Test
    fun `tirar a data final manda null explicito`() = runTest {
        coEvery { api.alterarPrazoEnquete(any()) } returns dto()
        val repo = EnquetesRepositorio(api, backgroundScope)

        repo.alterarPrazo(42, null)

        coVerify { api.alterarPrazoEnquete(PrazoEnqueteRequisicao(42, nuloExplicito)) }
    }
}
