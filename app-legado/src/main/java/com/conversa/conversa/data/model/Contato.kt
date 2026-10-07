package com.conversa.conversa.data.model

data class Contato(
    val id: Int,
    val nome: String,
    val login: String,
    val email: String?,
    val telefone: String?
)
