package com.conversa.conversa.data.model

data class LoginRequest(
    val login: String,
    val senha: String,
    val dispositivo_id: Int? = null
)
