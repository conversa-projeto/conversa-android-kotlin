package com.example.conversaandroid.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class TipoConversa(val value: Int) {
    CHAT(1),
    GRUPO(2);

    companion object {
        fun fromValue(value: Int): TipoConversa = values().find { it.value == value } ?: CHAT
    }
}

@Parcelize
data class Conversa(
    val id: Int,
    val tipo: TipoConversa = TipoConversa.CHAT,
    val descricao: String = "",
    val ultimaMensagem: String = "",
    val ultimaMensagemData: Long = 0L,
    val ultimaMensagemId: Int = 0,
    val criadoEm: Long = System.currentTimeMillis(),
    val usuarios: MutableList<Usuario> = mutableListOf(),
    val mensagensSemVisualizar: Int = 0,
    val destinatarioId: Int? = null,
    val destinatarioNome: String? = null
) : Parcelable {

    fun getDestinatario(usuarioAtualId: Int): Usuario? {
        return usuarios.find { it.id != usuarioAtualId }
    }

    fun addUsuario(usuario: Usuario) {
        if (!usuarios.any { it.id == usuario.id }) {
            usuarios.add(usuario)
        }
    }

    fun getTitulo(usuarioAtualId: Int): String {
        return when (tipo) {
            TipoConversa.CHAT -> getDestinatario(usuarioAtualId)?.nome ?: destinatarioNome ?: "Chat"
            TipoConversa.GRUPO -> descricao
        }
    }
}