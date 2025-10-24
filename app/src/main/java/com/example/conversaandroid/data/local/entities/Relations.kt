package com.example.conversaandroid.data.local.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ConversaWithUsuarios(
    @Embedded val conversa: ConversaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ConversaUsuarioEntity::class,
            parentColumn = "conversaId",
            entityColumn = "usuarioId"
        )
    )
    val usuarios: List<UsuarioEntity>
)

data class MensagemWithConteudos(
    @Embedded val mensagem: MensagemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mensagemId"
    )
    val conteudos: List<ConteudoEntity>
)