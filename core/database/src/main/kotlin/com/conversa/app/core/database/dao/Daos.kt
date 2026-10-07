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

    @Query("SELECT * FROM conversa")
    suspend fun todas(): List<ConversaEntidade>

    @Query("DELETE FROM conversa WHERE id = :id")
    suspend fun remover(id: Long)

    @Query("UPDATE conversa SET fixadaOrdem = NULL")
    suspend fun desafixarTodas()

    @Query("UPDATE conversa SET fixadaOrdem = :ordem WHERE id = :id")
    suspend fun definirOrdemFixada(id: Long, ordem: Int)

    /** Aplica a ordem completa das fixadas (posição = índice + 1), como o `PATCH /conversa/fixadas`. */
    @Transaction
    suspend fun aplicarFixadas(ids: List<Long>) {
        desafixarTodas()
        ids.forEachIndexed { indice, id -> definirOrdemFixada(id, indice + 1) }
    }

    /** Uma não lida a menos (li uma mensagem); nunca abaixo de zero. */
    @Query("UPDATE conversa SET naoLidas = MAX(0, naoLidas - 1) WHERE id = :id")
    suspend fun descontarNaoLida(id: Long)

    @Query("UPDATE conversa SET arquivadaEm = :arquivadaEmMs WHERE id = :id")
    suspend fun definirArquivada(id: Long, arquivadaEmMs: Long?)

    /** Arquivar também desfixa (contrato §11.7). */
    @Transaction
    suspend fun marcarArquivada(id: Long, arquivadaEmMs: Long?) {
        definirArquivada(id, arquivadaEmMs)
        if (arquivadaEmMs != null) {
            val restantes = todas().filter { it.fixadaOrdem != null && it.id != id }.sortedBy { it.fixadaOrdem }.map { it.id }
            aplicarFixadas(restantes)
        }
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

    /**
     * Status vindo de `GET /mensagem/status`, que é sempre o agregado de **todos** os
     * destinatários (contrato §10.5). Só vale para as minhas mensagens; nas dos outros,
     * os campos guardam o **meu** status e não podem ser sobrescritos.
     */
    @Query(
        "UPDATE mensagem SET recebida = :recebida, visualizada = :visualizada, reproduzida = :reproduzida " +
            "WHERE id = :id AND remetenteId = :eu",
    )
    suspend fun atualizarStatusDaMinha(id: Long, eu: Long, recebida: Boolean, visualizada: Boolean, reproduzida: Boolean)

    /** Ocultar vale para qualquer mensagem. */
    @Query("UPDATE mensagem SET excluidaEm = :excluidaEmMs WHERE id = :id")
    suspend fun atualizarOculta(id: Long, excluidaEmMs: Long?)

    /** Eu li a mensagem de outra pessoa (otimista, antes da resposta do servidor). */
    @Query("UPDATE mensagem SET recebida = 1, visualizada = 1 WHERE id = :id")
    suspend fun marcarLidaPorMim(id: Long)

    /** Eu ouvi o áudio de outra pessoa (otimista). Não marca como lida (o servidor também não). */
    @Query("UPDATE mensagem SET reproduzida = 1 WHERE id = :id")
    suspend fun marcarReproduzidaPorMim(id: Long)

    /** Mensagem otimista: saindo, saiu ou desistiu. */
    @Query("UPDATE mensagem SET enviando = :enviando, falhou = :falhou WHERE id = :id")
    suspend fun marcarEnvio(id: Long, enviando: Boolean, falhou: Boolean)

    @Query("SELECT MIN(id) FROM mensagem WHERE conversaId = :conversaId AND id > 0")
    suspend fun primeiraSalva(conversaId: Long): Long?

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

    @Query("DELETE FROM contato")
    suspend fun limpar()

    @Transaction
    suspend fun substituirTodos(contatos: List<ContatoEntidade>) {
        if (contatos.isEmpty()) limpar() else removerForaDe(contatos.map { it.id })
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

    @Query("SELECT * FROM envio_pendente WHERE mensagemIdLocal = :mensagemIdLocal")
    suspend fun buscar(mensagemIdLocal: Long): EnvioPendenteEntidade?

    /** Corpo do envio atualizado (ex.: anexo já subiu e ganhou o identificador). */
    @Query("UPDATE envio_pendente SET payloadJson = :payloadJson WHERE mensagemIdLocal = :mensagemIdLocal")
    suspend fun atualizarPayload(mensagemIdLocal: Long, payloadJson: String)

    @Query("UPDATE envio_pendente SET tentativas = 0 WHERE mensagemIdLocal = :mensagemIdLocal")
    suspend fun zerarTentativas(mensagemIdLocal: Long)

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
