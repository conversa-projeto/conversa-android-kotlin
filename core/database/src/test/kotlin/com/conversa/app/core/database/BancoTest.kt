package com.conversa.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.database.entidades.AtividadeEntidade
import com.conversa.app.core.database.entidades.ChamadaHistoricoEntidade
import com.conversa.app.core.database.entidades.ContatoEntidade
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.ConversaEntidade
import com.conversa.app.core.database.entidades.EnvioPendenteEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.database.entidades.ReacaoEntidade
import com.conversa.app.core.database.entidades.SyncEstadoEntidade
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inserir e ler cada entidade (FC-110), com o Room em memória. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BancoTest {
    private lateinit var banco: ConversaBanco
    private val agora = Instant.parse("2026-10-06T12:00:00.123Z")

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun conversa(id: Long) = ConversaEntidade(id, 1, null, "Bruno", 8, 0, agora, "oi", 2, null, null, null)

    private fun mensagem(id: Long, conversaId: Long = 42, data: Instant = agora) = MensagemCompleta(
        mensagem = MensagemEntidade(id, conversaId, 7, "Ana", data, null, null, data, null, true, false, false),
        conteudos = listOf(ConteudoEntidade(id, 1, 500 + id, 1, "texto $id", "", "", 0, "")),
        reacoes = listOf(ReacaoEntidade(id, "👍", 1, true, "[]")),
    )

    @Test
    fun `conversas - substituir remove as que sairam`() = runTest {
        val dao = banco.conversaDao()
        dao.substituirTodas(listOf(conversa(1), conversa(2)))
        dao.substituirTodas(listOf(conversa(2), conversa(3)))
        assertThat(dao.observarTodas().first().map { it.id }).containsExactly(2L, 3L)
        assertThat(dao.observarTodas().first().first().ultimaMensagemEm).isEqualTo(agora)
        dao.substituirTodas(emptyList())
        assertThat(dao.observarTodas().first()).isEmpty()
    }

    @Test
    fun `mensagens com conteudos e reacoes, em ordem de data`() = runTest {
        val dao = banco.mensagemDao()
        dao.salvarCompletas(listOf(mensagem(2, data = agora.plusSeconds(10)), mensagem(1)))
        val lista = dao.observarDaConversa(42).first()
        assertThat(lista.map { it.mensagem.id }).containsExactly(1L, 2L).inOrder()
        assertThat(lista[0].conteudos.single().conteudo).isEqualTo("texto 1")
        assertThat(lista[0].reacoes.single().emoji).isEqualTo("👍")
        assertThat(dao.ultimaSalva(42)).isEqualTo(2)

        // Regravar troca os conteúdos por completo
        dao.salvarCompletas(listOf(mensagem(1).copy(conteudos = emptyList(), reacoes = emptyList())))
        assertThat(dao.buscar(1)!!.conteudos).isEmpty()

        dao.atualizarStatus(2, recebida = true, visualizada = true, reproduzida = false, excluidaEmMs = agora.toEpochMilli())
        assertThat(dao.buscar(2)!!.mensagem.excluidaEm).isEqualTo(agora)

        dao.remover(2)
        assertThat(dao.buscar(2)).isNull()
    }

    @Test
    fun `contatos, atividades, historico, fila e estado`() = runTest {
        banco.contatoDao().substituirTodos(
            listOf(ContatoEntidade(8, "bruno", "bruno", null, null, null), ContatoEntidade(9, "Ana", "ana", null, null, null)),
        )
        assertThat(banco.contatoDao().observarTodos().first().map { it.nome }).containsExactly("Ana", "bruno").inOrder()

        banco.atividadeDao().salvar(
            listOf(AtividadeEntidade(33, 1, agora, true, 8, "Bruno", null, 42, 1, null, 101, 1, "oi", null, null, "😂")),
        )
        assertThat(banco.atividadeDao().observarTodas().first().single().emoji).isEqualTo("😂")

        banco.chamadaHistoricoDao().salvar(listOf(ChamadaHistoricoEntidade(10, 1, 4, agora, 7, 42, agora, agora, 125, "[]")))
        assertThat(banco.chamadaHistoricoDao().observarTodas().first().single().duracaoSegundos).isEqualTo(125)

        banco.envioPendenteDao().inserir(EnvioPendenteEntidade(conversaId = 42, mensagemIdLocal = -1, payloadJson = "{}", criadoEm = agora))
        banco.envioPendenteDao().contarTentativa(-1)
        assertThat(banco.envioPendenteDao().todos().single().tentativas).isEqualTo(1)
        banco.envioPendenteDao().remover(-1)
        assertThat(banco.envioPendenteDao().todos()).isEmpty()

        banco.syncEstadoDao().gravar(SyncEstadoEntidade("cursor", "2026-10-06T12:00:00.123Z"))
        assertThat(banco.syncEstadoDao().ler("cursor")).isEqualTo("2026-10-06T12:00:00.123Z")
        assertThat(banco.syncEstadoDao().ler("outro")).isNull()
    }
}
