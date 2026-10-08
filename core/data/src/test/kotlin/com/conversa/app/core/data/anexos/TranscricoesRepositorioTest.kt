package com.conversa.app.core.data.anexos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.IdentificadorDto
import com.conversa.app.core.network.dto.TranscricaoDto
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/** Transcrição (4.8): pedir, acompanhar a cada 3 s, gravar em todas as mensagens com o áudio, servidor sem transcritor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranscricoesRepositorioTest {
    private lateinit var banco: ConversaBanco
    private val api = mockk<ConversaApi>()
    private val agora = Instant.parse("2026-10-08T12:00:00Z")

    @Before
    fun abrir() {
        // Room na própria thread do teste: esperando uma consulta em outra thread, o runTest
        // avança o relógio virtual e a consulta de 3 s dispararia antes da hora.
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun TestScope.repositorio() = TranscricoesRepositorio(api, banco.mensagemDao(), backgroundScope)

    /** Duas mensagens com o mesmo áudio (encaminhado) e uma com outro. */
    private suspend fun salvarAudios() {
        fun mensagem(id: Long, identificador: String) = MensagemCompleta(
            mensagem = MensagemEntidade(
                id = id,
                conversaId = 42,
                remetenteId = 8,
                remetente = "Bruno",
                inserida = agora,
                visivelEm = null,
                excluidaEm = null,
                dataEfetiva = agora,
                referenciaJson = null,
                recebida = true,
                visualizada = true,
                reproduzida = false,
            ),
            conteudos = listOf(ConteudoEntidade(id, 1, id, 5, identificador, "audio.m4a", "m4a", 0, "")),
            reacoes = emptyList(),
        )
        banco.mensagemDao().salvarCompletas(listOf(mensagem(1, "h1"), mensagem(2, "h1"), mensagem(3, "h2")))
    }

    private suspend fun conteudo(id: Long) = banco.mensagemDao().buscar(id)!!.conteudos.single()

    @Test
    fun `pedir, acompanhar a cada 3 s e gravar o texto em todas as mensagens com o audio`() = runTest {
        salvarAudios()
        coEvery { api.transcrever(IdentificadorDto("h1")) } returns TranscricaoDto(status = 1)
        coEvery { api.transcricao("h1") } returnsMany listOf(TranscricaoDto(status = 1), TranscricaoDto(status = 2, texto = "bom dia"))
        val repo = repositorio()

        assertThat(repo.transcrever("h1").isSuccess).isTrue()
        assertThat(conteudo(1).transcricaoStatus).isEqualTo(1)

        advanceTimeBy(TranscricoesRepositorio.INTERVALO_CONSULTA_MS + 1)
        runCurrent()
        assertThat(conteudo(1).transcricaoStatus).isEqualTo(1)
        advanceTimeBy(TranscricoesRepositorio.INTERVALO_CONSULTA_MS)
        runCurrent()

        assertThat(conteudo(1).transcricao).isEqualTo("bom dia")
        assertThat(conteudo(2).transcricaoStatus).isEqualTo(2)
        assertThat(conteudo(2).transcricao).isEqualTo("bom dia")
        assertThat(conteudo(3).transcricaoStatus).isEqualTo(0)

        // Terminou: não consulta mais.
        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 2) { api.transcricao("h1") }
    }

    @Test
    fun `servidor sem transcritor desliga os botoes`() = runTest {
        salvarAudios()
        coEvery { api.transcrever(any()) } throws HttpException(
            Response.error<Any>(400, """{"error":"Transcrição não configurada"}""".toResponseBody(null)),
        )
        val repo = repositorio()

        assertThat(repo.transcrever("h2").isFailure).isTrue()
        assertThat(repo.desligada.value).isTrue()
        assertThat(repo.pedindo.value).isEmpty()

        repo.limpar()
        assertThat(repo.desligada.value).isFalse()
    }

    @Test
    fun `erro do transcritor guarda o motivo e permite tentar de novo`() = runTest {
        salvarAudios()
        coEvery { api.transcrever(IdentificadorDto("h2")) } returnsMany listOf(
            TranscricaoDto(status = 3, erro = "Arquivo sem áudio"),
            TranscricaoDto(status = 2, texto = "ok"),
        )
        val repo = repositorio()

        repo.transcrever("h2")
        assertThat(conteudo(3).transcricaoStatus).isEqualTo(3)
        assertThat(repo.erros.value).containsEntry("h2", "Arquivo sem áudio")

        repo.transcrever("h2")
        assertThat(conteudo(3).transcricao).isEqualTo("ok")
        assertThat(repo.erros.value).doesNotContainKey("h2")
    }
}
