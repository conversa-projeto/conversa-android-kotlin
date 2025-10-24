package com.example.conversaandroid.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seudominio.conversa.domain.model.Conteudo
import com.seudominio.conversa.domain.model.TipoConteudo

@Entity(
    tableName = "conteudos",
    foreignKeys = [
        ForeignKey(
            entity = MensagemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mensagemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mensagemId")]
)
data class ConteudoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val mensagemId: Int,
    val tipo: TipoConteudo,
    val ordem: Int,
    val conteudo: String,
    val nome: String,
    val extensao: String,
    val identificador: String?
) {
    fun toConteudo(): Conteudo {
        return Conteudo(
            id = id,
            tipo = tipo,
            ordem = ordem,
            conteudo = conteudo,
            nome = nome,
            extensao = extensao,
            identificador = identificador
        )
    }

    companion object {
        fun fromConteudo(conteudo: Conteudo, mensagemId: Int): ConteudoEntity {
            return ConteudoEntity(
                id = conteudo.id,
                mensagemId = mensagemId,
                tipo = conteudo.tipo,
                ordem = conteudo.ordem,
                conteudo = conteudo.conteudo,
                nome = conteudo.nome,
                extensao = conteudo.extensao,
                identificador = conteudo.identificador
            )
        }
    }
}