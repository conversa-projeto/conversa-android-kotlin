package com.example.conversaandroid.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "conversa_usuarios",
    primaryKeys = ["conversaId", "usuarioId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversaEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuarioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversaId"), Index("usuarioId")]
)
data class ConversaUsuarioEntity(
    val conversaId: Int,
    val usuarioId: Int
)