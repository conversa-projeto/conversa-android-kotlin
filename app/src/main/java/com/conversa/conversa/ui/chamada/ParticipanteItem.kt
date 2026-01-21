package com.conversa.conversa.ui.chamada

data class ParticipanteItem(
    val id: Int,
    val nome: String,
    val fotoUrl: String?,
    val audioAtivo: Boolean,
    val status: String,
    val mutadoLocalmente: Boolean = false,
    val lastAudioTimestamp: Long = 0L
)
