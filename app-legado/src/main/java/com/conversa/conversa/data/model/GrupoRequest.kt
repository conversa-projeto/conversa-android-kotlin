package com.conversa.conversa.data.model

import java.time.LocalDateTime

// Request para criar conversa (1:1 ou Grupo)
data class CriarConversaRequest(
    val descricao: String,     // Vazio para 1:1, Nome para grupo
    val tipo: Int,             // 1 = Chat 1:1, 2 = Grupo
    val inserida: String       // Data ISO 8601
)

// Request para adicionar usuário à conversa
data class AdicionarUsuarioRequest(
    val conversa_id: Int,
    val usuario_id: Int
)

// Response de criar conversa
data class CriarConversaResponse(
    val id: Int,
    val descricao: String,
    val tipo: Int,
    val inserida: LocalDateTime?
)
