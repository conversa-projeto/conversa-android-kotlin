package com.conversa.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /conversa/resumo` (contrato §13.5): `periodo` ∈ `24h`, `7d`, `30d`, `recentes`. */
@Serializable
data class PedirResumoRequisicao(@SerialName("conversa_id") val conversaId: Long, val periodo: String)

@Serializable
data class AssuntoResumoDto(
    val titulo: String = "",
    val resumo: String = "",
    val pendencias: List<String> = emptyList(),
    val mensagens: List<Long> = emptyList(),
)

/** Resposta do `POST` e do `GET /conversa/resumo`: o resumo é feito em segundo plano. */
@Serializable
data class ResumoDto(
    val id: String,
    @SerialName("conversa_id") val conversaId: Long = 0,
    val periodo: String = "24h",
    val status: String = "processando",
    val mensagens: Int = 0,
    val assuntos: List<AssuntoResumoDto> = emptyList(),
    val erro: String = "",
)
