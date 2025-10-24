package com.example.conversaandroid.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seudominio.conversa.domain.model.Usuario

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey
    val id: Int,
    val nome: String,
    val login: String,
    val email: String,
    val telefone: String?
) {
    fun toUsuario(): Usuario {
        return Usuario(
            id = id,
            nome = nome,
            login = login,
            email = email,
            telefone = telefone ?: ""
        )
    }

    companion object {
        fun fromUsuario(usuario: Usuario): UsuarioEntity {
            return UsuarioEntity(
                id = usuario.id,
                nome = usuario.nome,
                login = usuario.login,
                email = usuario.email,
                telefone = usuario.telefone
            )
        }
    }
}