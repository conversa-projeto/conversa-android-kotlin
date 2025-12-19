package com.conversa.conversa.data.model

data class ParticipanteItem(
    val usuarioId: Int,
    val nome: String,
    val status: UsuarioChamadaStatus
)
