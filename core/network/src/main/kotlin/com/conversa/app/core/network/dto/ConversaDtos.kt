@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Conversas e membros (contrato §11).

@Serializable
data class ConversaDto(
    val id: Long,
    val descricao: String? = null,
    val tipo: Int = 1,
    val inserida: Instant? = null,
    val nome: String? = null,
    @SerialName("destinatario_id") val destinatarioId: Long? = null,
    @SerialName("mensagem_id") val mensagemId: Long = 0,
    @SerialName("ultima_mensagem") val ultimaMensagem: Instant? = null,
    @SerialName("ultima_mensagem_texto") val ultimaMensagemTexto: String? = null,
    @SerialName("mensagens_sem_visualizar") val mensagensSemVisualizar: Int = 0,
    @SerialName("fixada_ordem") val fixadaOrdem: Int? = null,
    @SerialName("arquivada_em") val arquivadaEm: Instant? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class CriarConversaRequisicao(val descricao: String? = null, val tipo: Int)

@Serializable
data class ConversaCriadaDto(
    val id: Long,
    val descricao: String? = null,
    val tipo: Int = 1,
    val inserida: Instant? = null,
    @SerialName("criado_por") val criadoPor: Long? = null,
)

@Serializable
data class AlterarConversaRequisicao(val id: Long, val descricao: String)

@Serializable
data class FixadasDto(val conversas: List<Long>)

@Serializable
data class ArquivarRequisicao(val conversa: Long, val arquivada: Boolean)

@Serializable
data class ArquivadaResposta(val id: Long, val arquivada: Boolean)

@Serializable
data class MembroDto(
    /** Id do vínculo `conversa_usuario`. */
    val id: Long,
    @SerialName("usuario_id") val usuarioId: Long,
    val nome: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class IncluirMembroRequisicao(@SerialName("conversa_id") val conversaId: Long, @SerialName("usuario_id") val usuarioId: Long)

@Serializable
data class VinculoConversaDto(
    val id: Long,
    @SerialName("conversa_id") val conversaId: Long? = null,
    @SerialName("usuario_id") val usuarioId: Long? = null,
)

/** Corpo `{ "id": n }` usado por várias rotas (digitando, gravando, chamadas…). */
@Serializable
data class IdDto(val id: Long)
