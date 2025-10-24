package com.example.conversaandroid.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seudominio.conversa.domain.model.Mensagem

@Entity(
    tableName = "mensagens",
    foreignKeys = [
        ForeignKey(
            entity = ConversaEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversaId")]
)
data class MensagemEntity(
    @PrimaryKey
    val id: Int,
    val localId: Long,
    val conversaId: Int,
    val remetenteId: Int,
    val remetenteNome: String,
    val inserida: Long,
    val alterada: Long?,
    val recebida: Boolean,
    val visualizada: Boolean,
    val reproduzida: Boolean
) {
    fun toMensagem(conteudos: List<ConteudoEntity> = emptyList()): Mensagem {
        return Mensagem(
            id = id,
            localId = localId,
            conversaId = conversaId,
            remetenteId = remetenteId,
            remetenteNome = remetenteNome,
            inserida = inserida,
            alterada = alterada,
            recebida = recebida,
            visualizada = visualizada,
            reproduzida = reproduzida,
            conteudos = conteudos.map { it.toConteudo() }
        )
    }

    companion object {
        fun fromMensagem(mensagem: Mensagem): MensagemEntity {
            return MensagemEntity(
                id = mensagem.id,
                localId = mensagem.localId,
                conversaId = mensagem.conversaId,
                remetenteId = mensagem.remetenteId,
                remetenteNome = mensagem.remetenteNome,
                inserida = mensagem.inserida,
                alterada = mensagem.alterada,
                recebida = mensagem.recebida,
                visualizada = mensagem.visualizada,
                reproduzida = mensagem.reproduzida
            )
        }
    }
}