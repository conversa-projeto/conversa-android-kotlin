package com.example.conversa.data.response

data class LoginResponse(
    val id: Int,
    val nome: String,
    val email: String,
    val telefone: String,
    val token: String
)