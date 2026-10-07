package com.conversa.conversa.ui.chamada

import org.webrtc.VideoTrack

data class ParticipanteUI(
    val id: Int,
    val nome: String,
    val fotoUrl: String?,
    val isFalando: Boolean = false,
    val status: String = "Conectado",
    val mutadoLocalmente: Boolean = false,
    val volume: Int = 100,
    val lastAudioTimestamp: Long = 0L,
    /** Track de vídeo remoto deste participante (null = sem vídeo, mostrar avatar). */
    val videoTrack: VideoTrack? = null
)
