@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

// Chamadas, ICE e chat da chamada (contrato §9).

/** O chamador deve se incluir em `usuarios` (senão o servidor responde 404 nas ações seguintes). */
@Serializable
data class IniciarChamadaRequisicao(
    val tipo: Int? = null,
    @SerialName("conversa_id") val conversaId: Long? = null,
    val usuarios: List<IdDto>,
)

@Serializable
data class UsuarioChamadaDto(
    @SerialName("usuario_id") val usuarioId: Long,
    @SerialName("usuario_nome") val usuarioNome: String = "",
    val status: Int,
    @SerialName("adicionado_por") val adicionadoPor: Long? = null,
    @SerialName("adicionado_por_nome") val adicionadoPorNome: String? = null,
    @SerialName("adicionado_em") val adicionadoEm: Instant? = null,
    @SerialName("entrou_em") val entrouEm: Instant? = null,
    @SerialName("saiu_em") val saiuEm: Instant? = null,
    @SerialName("recusou_em") val recusouEm: Instant? = null,
)

/** "Dados da chamada" (contrato §9.3). Não traz a conversa de origem, só `conversa_chat_id`. */
@Serializable
data class DadosChamadaDto(
    val id: Long,
    val iniciada: Instant? = null,
    val finalizada: Instant? = null,
    val tipo: Int,
    val status: Int,
    @SerialName("criado_em") val criadoEm: Instant? = null,
    @SerialName("criado_por") val criadoPor: Long? = null,
    @SerialName("conversa_chat_id") val conversaChatId: Long? = null,
    val usuarios: List<UsuarioChamadaDto> = emptyList(),
)

@Serializable
data class RecusarChamadaRequisicao(
    val id: Long,
    /** `true` quando o app recusa sozinho (30 s tocando, ocupado, pendente antiga): vira chamada perdida. */
    @SerialName("nao_atendeu") val naoAtendeu: Boolean? = null,
)

@Serializable
data class AdicionarUsuarioChamadaRequisicao(@SerialName("chamada_id") val chamadaId: Long, @SerialName("usuario_id") val usuarioId: Long)

@Serializable
data class ChatChamadaDto(@SerialName("conversa_id") val conversaId: Long)

/** `GET /chamadas/pendentes` — **sem** `usuarios`; `conversa_id` nulo vira 0. */
@Serializable
data class ChamadaPendenteDto(
    val id: Long,
    val tipo: Int,
    val status: Int,
    val iniciada: Instant? = null,
    val finalizada: Instant? = null,
    @SerialName("conversa_id") val conversaId: Long = 0,
    @SerialName("criado_em") val criadoEm: Instant? = null,
    @SerialName("criado_por") val criadoPor: Long? = null,
)

@Serializable
data class ParticipanteHistoricoDto(
    @SerialName("usuario_id") val usuarioId: Long,
    val nome: String = "",
    val status: Int,
    val duracao: Long? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ChamadaHistoricoDto(
    val id: Long,
    val tipo: Int,
    val status: Int,
    @SerialName("criado_em") val criadoEm: Instant? = null,
    @SerialName("criado_por") val criadoPor: Long? = null,
    @SerialName("conversa_id") val conversaId: Long? = null,
    val iniciada: Instant? = null,
    val finalizada: Instant? = null,
    /** Segundos; nulo se faltou início ou fim. */
    val duracao: Long? = null,
    val participantes: List<ParticipanteHistoricoDto> = emptyList(),
)

/** `GET /ice` — a única resposta em camelCase. `urls` é texto (pode vir lista). */
@Serializable
data class IceDto(val iceServers: List<ServidorIceDto> = emptyList(), val iceTransportPolicy: String = "all")

@Serializable
data class ServidorIceDto(val urls: JsonElement, val username: String? = null, val credential: String? = null)
