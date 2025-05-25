package com.example.conversa.model

// Importar Instant para parsing de data/hora
import java.time.Instant

data class Message(
    val id: Int,
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val content: String, // Conteúdo principal da mensagem (geralmente o primeiro 'conteudo')
    val timestamp: Long, // Unix timestamp em milissegundos para facilitar o uso na UI
    val isMine: Boolean, // Se a mensagem foi enviada pelo usuário logado
    val contents: List<MessageContent> // Mantém a lista completa de conteúdos se necessário
)