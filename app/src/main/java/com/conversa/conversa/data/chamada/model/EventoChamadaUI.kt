package com.conversa.conversa.data.chamada.model

enum class TipoEventoChamadaUI {
    PARTICIPANTE_ENTROU,
    PARTICIPANTE_SAIU,
    CHAMADA_RECUSADA,
    CHAMADA_CONECTADA,
    CHAMADA_FINALIZADA,
    AUDIO_ALTERADO,
    VIDEO_ALTERADO,
    CHAMADA_REALMENTE_INICIADA
}

data class EventoChamadaUI(
    val tipo: TipoEventoChamadaUI,
    val chamadaId: Int,
    val participanteId: Int? = null,
    val participanteNome: String? = null,
    val audioAtivo: Boolean? = null,
    val videoAtivo: Boolean? = null
)
