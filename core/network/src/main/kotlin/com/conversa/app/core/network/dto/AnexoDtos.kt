@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.IdFlexivelSerializer
import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Anexos, MinIO e transcrição (contrato §8).

@Serializable
data class AnexoExisteDto(
    val existe: Boolean,
    @Serializable(with = IdFlexivelSerializer::class) val id: Long? = null,
    val identificador: String? = null,
    val tipo: Int? = null,
    val tamanho: Long? = null,
    @SerialName("upload_status") val uploadStatus: Int? = null,
)

/** `PUT /anexo`. Tamanho máximo 1 GiB; `extensao` até 10 caracteres. */
@Serializable
data class IncluirAnexoRequisicao(
    val identificador: String,
    val tipo: Int,
    val nome: String? = null,
    val extensao: String? = null,
    val tamanho: Long,
)

/**
 * Resposta de `PUT /anexo`:
 * - novo: `existe=false`, `upload_url` = PUT pré-assinado (300 s);
 * - existente: `existe=true`, `id` como **texto** e `upload_url` = GET de download (600 s).
 */
@Serializable
data class IncluirAnexoResposta(
    val existe: Boolean,
    @Serializable(with = IdFlexivelSerializer::class) val id: Long,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("upload_status") val uploadStatus: Int? = null,
)

@Serializable
data class UrlAnexoDto(val url: String)

@Serializable
data class ConfirmarAnexoDto(val confirmado: Boolean = false, @SerialName("upload_status") val uploadStatus: Int? = null)

@Serializable
data class AnexoItemDto(
    @SerialName("anexo_id") val anexoId: Long,
    val identificador: String,
    val nome: String? = null,
    val extensao: String? = null,
    val tamanho: Long = 0,
    @SerialName("criado_em") val criadoEm: Instant? = null,
    val tipo: Int,
    @SerialName("mensagem_id") val mensagemId: Long? = null,
    @SerialName("conversa_id") val conversaId: Long? = null,
    @SerialName("conversa_descricao") val conversaDescricao: String? = null,
    @SerialName("autor_id") val autorId: Long? = null,
    @SerialName("autor_nome") val autorNome: String? = null,
    val url: String? = null,
)

@Serializable
data class TranscricaoDto(val status: Int = 0, val texto: String = "", val erro: String = "")

@Serializable
data class IdentificadorDto(val identificador: String)
