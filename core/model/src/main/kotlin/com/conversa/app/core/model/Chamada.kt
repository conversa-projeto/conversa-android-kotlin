package com.conversa.app.core.model

import java.time.Instant

/** Tipo da chamada para o cliente: 1 áudio, 2 vídeo (contrato §9.1). */
enum class TipoChamada(val codigo: Int) {
    AUDIO(1),
    VIDEO(2),
    ;

    companion object {
        fun de(codigo: Int): TipoChamada = entries.firstOrNull { it.codigo == codigo } ?: AUDIO
    }
}

enum class StatusChamada(val codigo: Int) {
    PENDENTE(1),
    RECUSADA(2),
    EM_ANDAMENTO(3),
    ENCERRADA(4),
    DESCONECTADA(5),
    CANCELADA(6),
    DESCONHECIDO(-1),
    ;

    val final: Boolean get() = this == RECUSADA || this == ENCERRADA || this == DESCONECTADA || this == CANCELADA

    companion object {
        fun de(codigo: Int): StatusChamada = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}

enum class StatusParticipante(val codigo: Int) {
    PENDENTE(1),
    RECUSOU(2),
    ENTROU(3),
    SAIU(4),
    DESCONECTOU(5),
    DESCONHECIDO(-1),
    ;

    companion object {
        fun de(codigo: Int): StatusParticipante = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}

data class ParticipanteChamada(
    val usuarioId: Long,
    val nome: String,
    val status: StatusParticipante,
    val entrouEm: Instant? = null,
    val saiuEm: Instant? = null,
)

/** Dados da chamada (`PUT /chamada/iniciar`, `GET /chamada/dados`, contrato §9.3). */
data class Chamada(
    val id: Long,
    val tipo: TipoChamada,
    val status: StatusChamada,
    val criadoEm: Instant?,
    val criadoPor: Long?,
    val iniciada: Instant?,
    val finalizada: Instant?,
    val conversaChatId: Long?,
    val participantes: List<ParticipanteChamada>,
)

/** Chamada tocando para o usuário (`GET /chamadas/pendentes`, contrato §9.6). Não traz participantes. */
data class ChamadaPendente(
    val id: Long,
    val tipo: TipoChamada,
    val status: StatusChamada,
    /** 0 quando a chamada não tem conversa. */
    val conversaId: Long,
    val criadoEm: Instant?,
    val criadoPor: Long?,
)

/** Servidores ICE de `GET /ice` (contrato §9.5). */
data class ServidoresIce(
    val servidores: List<ServidorIce>,
    /** `iceTransportPolicy = relay`: na prática a mídia só passa pelo TURN. */
    val somenteRelay: Boolean,
)

data class ServidorIce(val urls: List<String>, val usuario: String?, val credencial: String?)
