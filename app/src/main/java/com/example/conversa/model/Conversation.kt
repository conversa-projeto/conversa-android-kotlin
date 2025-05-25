package com.example.conversa.model

import com.google.gson.annotations.SerializedName

data class Conversation(
    val id: Int,
    val descricao: String,
    val tipo: Int, // 1 para chat, 2 para grupo
    val inserida: String, // Timestamp ISO 8601 de criação
    val nome: String, // Nome do contato ou grupo associado à conversa
    @SerializedName("destinatario_id")
    val destinatarioId: Int?, // ID do destinatário para chats 1:1, pode ser nulo para grupos
    @SerializedName("mensagem_id")
    val ultimaMensagemId: Int?,
    @SerializedName("ultima_mensagem")
    val ultimaMensagemTimestamp: String?,
    @SerializedName("ultima_mensagem_texto")
    val ultimaMensagemTexto: String?,
    @SerializedName("mensagens_sem_visualizar")
    val mensagensSemVisualizar: Int
)