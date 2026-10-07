package com.conversa.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversaDao {
    @Query("SELECT * FROM conversa")
    fun observarTodas(): Flow<List<ConversaEntidade>>

    @Query("SELECT * FROM conversa WHERE id = :id")
    fun observar(id: Long): Flow<ConversaEntidade?>

    @Upsert
    suspend fun salvar(conversas: List<ConversaEntidade>)

    @Query("DELETE FROM conversa WHERE id NOT IN (:ids)")
    suspend fun removerForaDe(ids: List<Long>)

    @Query("DELETE FROM conversa")
    suspend fun limpar()

    /** A lista do servidor é completa: o que não veio saiu da conversa. */
    @Transaction
    suspend fun substituirTodas(conversas: List<ConversaEntidade>) {
        if (conversas.isEmpty()) limpar() else removerForaDe(conversas.map { it.id })
        salvar(conversas)
    }
}

@Dao
interface MensagemDao {
    @Transaction
    @Query("SELECT * FROM mensagem WHERE conversaId = :conversaId ORDER BY dataEfetiva, id")
    fun observarDaConversa(conversaId: Long): Flow<List<MensagemCompleta>>

    @Transaction
    @Query("SELECT * FROM mensagem WHERE id = :id")
    suspend fun buscar(id: Long): MensagemCompleta?

    @Upsert
    suspend fun salvarMensagens(mensagens: List<MensagemEntidade>)

    @Query("DELETE FROM conteudo WHERE mensagemId IN (:ids)")
    suspend fun removerConteudos(ids: List<Long>)

    @Query("DELETE FROM reacao WHERE mensagemId IN (:ids)")
    suspend fun removerReacoes(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarConteudos(conteudos: List<ConteudoEntidade>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarReacoes(reacoes: List<ReacaoEntidade>)

    /** Grava mensagens vindas do servidor, trocando conteúdos e reações por completo. */
    @Transaction
    suspend fun salvarCompletas(mensagens: List<MensagemCompleta>) {
        if (mensagens.isEmpty()) return
        val ids = mensagens.map { it.mensagem.id }
        salvarMensagens(mensagens.map { it.mensagem })
        removerConteudos(ids)
        removerReacoes(ids)
        salvarConteudos(mensagens.flatMap { it.conteudos })
        salvarReacoes(mensagens.flatMap { it.reacoes })
    }

    @Query(
        "UPDATE mensagem SET recebida = :recebida, visualizada = :visualizada, reproduzida = :reproduzida, " +
            "excluidaEm = :excluidaEmMs WHERE id = :id",
    )
    suspend fun atualizarStatus(id: Long, recebida: Boolean, visualizada: Boolean, reproduzida: Boolean, excluidaEmMs: Long?)

    @Query("DELETE FROM mensagem WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("SELECT MAX(id) FROM mensagem WHERE conversaId = :conversaId AND id > 0")
    suspend fun ultimaSalva(conversaId: Long): Long?

    @Query("SELECT MIN(id) FROM mensagem")
    suspend fun menorId(): Long?
}

@Dao
interface ContatoDao {
    @Query("SELECT * FROM contato ORDER BY nome COLLATE NOCASE")
    fun observarTodos(): Flow<List<ContatoEntidade>>

    @Upsert
    suspend fun salvar(contatos: List<ContatoEntidade>)

    @Query("DELETE FROM contato WHERE id NOT IN (:ids)")
    suspend fun removerForaDe(ids: List<Long>)

    @Transaction
    suspend fun substituirTodos(contatos: List<ContatoEntidade>) {
        removerForaDe(contatos.map { it.id })
        salvar(contatos)
    }
}

@Dao
interface AtividadeDao {
    @Query("SELECT * FROM atividade ORDER BY id DESC")
    fun observarTodas(): Flow<List<AtividadeEntidade>>

    @Upsert
    suspend fun salvar(atividades: List<AtividadeEntidade>)

    @Query("DELETE FROM atividade")
    suspend fun limpar()
}

@Dao
interface ChamadaHistoricoDao {
    @Query("SELECT * FROM chamada_historico ORDER BY criadoEm DESC")
    fun observarTodas(): Flow<List<ChamadaHistoricoEntidade>>

    @Upsert
    suspend fun salvar(chamadas: List<ChamadaHistoricoEntidade>)
}

@Dao
interface EnvioPendenteDao {
    @Query("SELECT * FROM envio_pendente ORDER BY idFila")
    suspend fun todos(): List<EnvioPendenteEntidade>

    @Insert
    suspend fun inserir(envio: EnvioPendenteEntidade): Long

    @Query("DELETE FROM envio_pendente WHERE mensagemIdLocal = :mensagemIdLocal")
    suspend fun remover(mensagemIdLocal: Long)

    @Query("UPDATE envio_pendente SET tentativas = tentativas + 1 WHERE mensagemIdLocal = :mensagemIdLocal")
    suspend fun contarTentativa(mensagemIdLocal: Long)
}

@Dao
interface SyncEstadoDao {
    @Query("SELECT valor FROM sync_estado WHERE chave = :chave")
    suspend fun ler(chave: String): String?

    @Upsert
    suspend fun gravar(estado: SyncEstadoEntidade)
}
