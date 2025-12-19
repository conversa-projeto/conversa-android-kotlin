package com.conversa.conversa.ui.chamada

data class ParticipanteItem(
    val id: Int,
    val nome: String,
    val fotoUrl: String?,
    val audioAtivo: Boolean,
    val status: String, // Status textual: "Conectado", "Aguardando", etc
    val mutadoLocalmente: Boolean = false // Se EU mutei este participante
)
