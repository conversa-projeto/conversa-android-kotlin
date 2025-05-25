package com.example.conversa.data.request

data class CreateConversationRequest(
    val tipo: Int, // 1 para chat, 2 para grupo
    val descricao: String
)