package com.conversa.conversa.ui.chamada

data class ParticipanteUI(
    val id: Int,
    val nome: String,
    val fotoUrl: String?,
    val isFalando: Boolean = false,
    val status: String = "Conectado",
    val mutadoLocalmente: Boolean = false,
    val volume: Int = 100,
    val lastAudioTimestamp: Long = 0L
)
