package com.conversa.app.core.database.entidades

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant

// Cache local (Room). É uma cópia do que veio do servidor, para abrir as telas
// sem rede e atualizar aos poucos. A fonte da verdade continua sendo o servidor.

@Entity(tableName = "conversa")
data class ConversaEntidade(
    @PrimaryKey val id: Long,
    val tipo: Int,
    val descricao: String?,
    val nome: String?,
    val destinatarioId: Long?,
    val ultimaMensagemId: Long,
    val ultimaMensagemEm: Instant?,
    val ultimaMensagemTexto: String?,
    val naoLidas: Int,
    val fixadaOrdem: Int?,
    val arquivadaEm: Instant?,
    val avatarUrl: String?,
)

/**
 * Mensagem. `id` negativo = mensagem otimista ainda não confirmada pelo servidor.
 * A cadeia de referência (resposta/encaminhada, até 5 níveis) fica em JSON.
 */
@Entity(
    tableName = "mensagem",
    indices = [Index("conversaId"), Index(value = ["conversaId", "dataEfetiva"])],
)
data class MensagemEntidade(
    @PrimaryKey val id: Long,
    val conversaId: Long,
    val remetenteId: Long,
    val remetente: String,
    val inserida: Instant,
    val visivelEm: Instant?,
    val excluidaEm: Instant?,
    /** `coalesce(visivel_em, inserida)`: a ordem da conversa. */
    val dataEfetiva: Instant,
    val referenciaJson: String?,
    val recebida: Boolean,
    val visualizada: Boolean,
    val reproduzida: Boolean,
    val enviando: Boolean = false,
    val falhou: Boolean = false,
)

@Entity(
    tableName = "conteudo",
    primaryKeys = ["mensagemId", "ordem"],
    foreignKeys = [
        ForeignKey(
            entity = MensagemEntidade::class,
            parentColumns = ["id"],
            childColumns = ["mensagemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ConteudoEntidade(
    val mensagemId: Long,
    val ordem: Int,
    val id: Long?,
    val tipo: Int,
    val conteudo: String,
    val nome: String,
    val extensao: String,
    val transcricaoStatus: Int,
    val transcricao: String,
)

@Entity(
    tableName = "reacao",
    primaryKeys = ["mensagemId", "emoji"],
    foreignKeys = [
        ForeignKey(
            entity = MensagemEntidade::class,
            parentColumns = ["id"],
            childColumns = ["mensagemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReacaoEntidade(val mensagemId: Long, val emoji: String, val quantidade: Int, val reagiu: Boolean, val usuariosJson: String)

data class MensagemCompleta(
    @Embedded val mensagem: MensagemEntidade,
    @Relation(parentColumn = "id", entityColumn = "mensagemId") val conteudos: List<ConteudoEntidade>,
    @Relation(parentColumn = "id", entityColumn = "mensagemId") val reacoes: List<ReacaoEntidade>,
)

@Entity(tableName = "contato")
data class ContatoEntidade(
    @PrimaryKey val id: Long,
    val nome: String,
    val login: String,
    val email: String?,
    val telefone: String?,
    val avatarUrl: String?,
)

@Entity(tableName = "atividade")
data class AtividadeEntidade(
    @PrimaryKey val id: Long,
    val tipo: Int,
    val criadoEm: Instant?,
    val nova: Boolean,
    val autorId: Long?,
    val autorNome: String?,
    val autorAvatarUrl: String?,
    val conversaId: Long?,
    val conversaTipo: Int?,
    val conversaDescricao: String?,
    val mensagemId: Long?,
    val conteudoTipo: Int?,
    val texto: String?,
    val chamadaId: Long?,
    val chamadaTipo: Int?,
    val emoji: String?,
)

@Entity(tableName = "chamada_historico")
data class ChamadaHistoricoEntidade(
    @PrimaryKey val id: Long,
    val tipo: Int,
    val status: Int,
    val criadoEm: Instant?,
    val criadoPor: Long?,
    val conversaId: Long?,
    val iniciada: Instant?,
    val finalizada: Instant?,
    val duracaoSegundos: Long?,
    val participantesJson: String,
)

/** Mensagem esperando para ser enviada (fila do WorkManager). */
@Entity(tableName = "envio_pendente", indices = [Index("mensagemIdLocal", unique = true)])
data class EnvioPendenteEntidade(
    @PrimaryKey(autoGenerate = true) val idFila: Long = 0,
    val conversaId: Long,
    /** Id negativo da mensagem otimista. */
    val mensagemIdLocal: Long,
    /** Corpo do `PUT /mensagem` (e anexos a enviar) em JSON. */
    val payloadJson: String,
    val tentativas: Int = 0,
    val criadoEm: Instant,
)

/** Pequenos valores de sincronização (ex.: cursor `ate` de `/mensagens/novas`). */
@Entity(tableName = "sync_estado")
data class SyncEstadoEntidade(@PrimaryKey val chave: String, val valor: String)
