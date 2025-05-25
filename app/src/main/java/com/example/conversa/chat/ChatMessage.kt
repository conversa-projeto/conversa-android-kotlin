// com.example.conversa.chat.ChatMessage.kt (ou com.example.conversa.model.ChatMessage.kt)

package com.example.conversa.chat // ou .model

enum class MessageType(val apiValue: Int) {
    NONE(0),
    TEXT(1),
    IMAGE(2),
    FILE(3),
    AUDIO(4)
}

data class ChatMessage(
    val type: MessageType,
    val content: String, // Para texto ou URL do áudio/imagem/arquivo
    val timestamp: Long, // Para ordenar e exibir a hora da mensagem (em milissegundos)
    val isSentByMe: Boolean, // TRUE se você enviou, FALSE se o contato enviou
    val order: Int = 0, // Adicionado para 'ordem' no array de conteudos
    val fileName: String = "", // Para 'nome' do arquivo de mídia
    val fileExtension: String = "" // Para 'extensao' do arquivo de mídia
)