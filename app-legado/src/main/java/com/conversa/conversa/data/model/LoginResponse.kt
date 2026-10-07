package com.conversa.conversa.data.model

data class LoginResponse(
    val id: Int,
    val nome: String,
    val email: String?,
    val telefone: String?,
    val token: String,
    val dispositivo: Dispositivo?
)

data class Dispositivo(
    val id: Int,
    val nome: String,
    val modelo: String?,
    val versao_so: String?,
    val plataforma: String?,
    val ativo: Boolean
)
