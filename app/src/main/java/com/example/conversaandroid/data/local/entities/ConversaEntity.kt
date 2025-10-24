package com.example.conversaandroid.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seudominio.conversa.domain.model.Conversa
import com.seudominio.conversa.domain.model.TipoConversa

@Entity(tableName = "conversas")
data class ConversaEntity(
    @PrimaryKey
    val id: Int,
    val tipo: TipoConversa,
    val descricao: String,
    val ultimaMensagem: String,
    val ultimaMensagemData: Long,
    val ultimaMensagemId: Int,
    val criadoEm: Long,
    val mensagensSemVisualizar: Int,
    val destinatarioId: Int?,
    val destinatarioNome: String?
) {
    fun toConversa(): Conversa {
        return Conversa(
            id = id,
            tipo = tipo,
            descricao = descricao,
            ultimaMensagem = ultimaMensagem,
            ultimaMensagemData = ultimaMensagemData,
            ultimaMensagemId = ultimaMensagemId,
            criadoEm = criadoEm,
            mensagensSemVisualizar = mensagensSemVisualizar,
            destinatarioId = destinatarioId,
            destinatarioNome = destinatarioNome
        )
    }

    companion object {
        fun fromConversa(conversa: Conversa): ConversaEntity {
            return ConversaEntity(
                id = conversa.id,
                tipo = conversa.tipo,
                descricao = conversa.descricao,
                ultimaMensagem = conversa.ultimaMensagem,
                ultimaMensagemData = conversa.ultimaMensagemData,
                ultimaMensagemId = conversa.ultimaMensagemId,
                criadoEm = conversa.criadoEm,
                mensagensSemVisualizar = conversa.mensagensSemVisualizar,
                destinatarioId = conversa.destinatarioId,
                destinatarioNome = conversa.destinatarioNome
            )
        }
    }
}