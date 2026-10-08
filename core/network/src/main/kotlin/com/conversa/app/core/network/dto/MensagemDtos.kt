@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

// Mensagens, status, reações, pesquisa e enquetes (contrato §10).

@Serializable
data class ConteudoDto(
    val id: Long? = null,
    val ordem: Int,
    val tipo: Int,
    val conteudo: String? = null,
    val nome: String = "",
    val extensao: String = "",
    @SerialName("transcricao_status") val transcricaoStatus: Int = 0,
    val transcricao: String = "",
)

@Serializable
data class MensagemResumidaDto(
    val id: Long,
    @SerialName("conversa_id") val conversaId: Long = 0,
    val remetente: String = "",
    val inserida: Instant? = null,
    @SerialName("excluida_em") val excluidaEm: Instant? = null,
    val conteudos: List<ConteudoDto> = emptyList(),
    @SerialName("mensagem_referencia") val mensagemReferencia: ReferenciaDto? = null,
)

@Serializable
data class ReferenciaDto(
    val tipo: Int,
    /** Pode faltar se a referenciada não existe mais. */
    val mensagem: MensagemResumidaDto? = null,
)

@Serializable
data class UsuarioReacaoDto(
    @SerialName("usuario_id") val usuarioId: Long,
    val nome: String = "",
    @SerialName("reagido_em") val reagidoEm: Instant? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ReacaoDto(
    val emoji: String,
    val quantidade: Int = 0,
    val reagiu: Boolean = false,
    val usuarios: List<UsuarioReacaoDto> = emptyList(),
)

/** `MensagemResposta` do servidor (contrato §10.2). */
@Serializable
data class MensagemDto(
    val id: Long,
    @SerialName("remetente_id") val remetenteId: Long,
    val remetente: String = "",
    @SerialName("conversa_id") val conversaId: Long,
    val inserida: Instant,
    val alterada: Instant? = null,
    @SerialName("visivel_em") val visivelEm: Instant? = null,
    @SerialName("excluida_em") val excluidaEm: Instant? = null,
    @SerialName("mensagem_referencia") val mensagemReferencia: ReferenciaDto? = null,
    val recebida: Boolean = false,
    val visualizada: Boolean = false,
    val reproduzida: Boolean = false,
    val conteudos: List<ConteudoDto> = emptyList(),
    val reacoes: List<ReacaoDto> = emptyList(),
)

@Serializable
data class ConteudoEnvioDto(val ordem: Int, val tipo: Int, val conteudo: String?)

/** `origem_mensagem_id` = a mensagem respondida/encaminhada (nome invertido em relação ao banco). */
@Serializable
data class ReferenciaEnvioDto(val tipo: Int, @SerialName("origem_mensagem_id") val origemMensagemId: Long)

@Serializable
data class EnviarMensagemRequisicao(
    @SerialName("conversa_id") val conversaId: Long,
    val conteudos: List<ConteudoEnvioDto>,
    @SerialName("visivel_em") val visivelEm: String? = null,
    @SerialName("mensagem_referencia") val mensagemReferencia: ReferenciaEnvioDto? = null,
)

/** Resposta de `PUT /mensagem`: a linha crua (usa `usuario_id`, sem conteúdos). */
@Serializable
data class MensagemCriadaDto(
    val id: Long,
    @SerialName("usuario_id") val usuarioId: Long? = null,
    @SerialName("conversa_id") val conversaId: Long,
    val inserida: Instant? = null,
    @SerialName("visivel_em") val visivelEm: Instant? = null,
)

/** Resposta de `DELETE /mensagem`: com `excluida_em` (oculta) ou sem (agendada apagada). */
@Serializable
data class MensagemExcluidaDto(
    val id: Long,
    @SerialName("conversa_id") val conversaId: Long? = null,
    @SerialName("excluida_em") val excluidaEm: Instant? = null,
)

@Serializable
data class MarcarStatusRequisicao(val conversa: Long, val mensagem: Long)

@Serializable
data class SucessoDto(val sucesso: Boolean = false)

/** `GET /mensagem/status`: aqui os status são booleanos agregados. */
@Serializable
data class StatusMensagemDto(
    @SerialName("conversa_id") val conversaId: Long = 0,
    @SerialName("mensagem_id") val mensagemId: Long,
    val recebida: Boolean = false,
    val visualizada: Boolean = false,
    val reproduzida: Boolean = false,
    @SerialName("excluida_em") val excluidaEm: Instant? = null,
)

/** `GET /mensagem/status/detalhe`: aqui os status são **datas**. */
@Serializable
data class StatusDetalheDto(
    @SerialName("usuario_id") val usuarioId: Long,
    val nome: String = "",
    val recebida: Instant? = null,
    val visualizada: Instant? = null,
    val reproduzida: Instant? = null,
)

/** `GET /mensagens/novas`. `ate` é guardado como texto e usado como próximo `desde`. */
@Serializable
data class NovaMensagemDto(
    @SerialName("conversa_id") val conversaId: Long,
    @SerialName("mensagem_id") val mensagemId: Long,
    val ate: String,
)

@Serializable
data class ReacaoRequisicao(@SerialName("mensagem_id") val mensagemId: Long, val emoji: String)

@Serializable
data class ReacaoResposta(
    @SerialName("mensagem_id") val mensagemId: Long,
    val emoji: String,
    /** "add" ou "remove". */
    val acao: String,
)

@Serializable
data class CriarEnqueteRequisicao(
    @SerialName("conversa_id") val conversaId: Long,
    val pergunta: String,
    val opcoes: List<String>,
    val multipla: Boolean,
    /** 🆕 785bdef: data final em ISO (sem = sem data final). */
    @SerialName("encerra_em") val encerraEm: String? = null,
)

@Serializable
data class EnqueteCriadaDto(val id: Long, @SerialName("conversa_id") val conversaId: Long, @SerialName("enquete_id") val enqueteId: Long)

@Serializable
data class VotanteDto(val id: Long, val nome: String = "")

@Serializable
data class OpcaoEnqueteDto(val id: Long, val texto: String, val votantes: List<VotanteDto> = emptyList())

@Serializable
data class EnqueteDto(
    val id: Long,
    @SerialName("conversa_id") val conversaId: Long,
    @SerialName("mensagem_id") val mensagemId: Long? = null,
    val pergunta: String,
    val multipla: Boolean = false,
    @SerialName("criado_por") val criadoPor: Long? = null,
    val opcoes: List<OpcaoEnqueteDto> = emptyList(),
    @SerialName("total_votantes") val totalVotantes: Int = 0,
    @SerialName("meus_votos") val meusVotos: List<Long> = emptyList(),
    // 🆕 5cad911: data final e encerramento.
    @SerialName("encerra_em") val encerraEm: Instant? = null,
    @SerialName("encerrada_em") val encerradaEm: Instant? = null,
    val encerrada: Boolean = false,
    @SerialName("pode_encerrar") val podeEncerrar: Boolean = false,
    @SerialName("pode_alterar_prazo") val podeAlterarPrazo: Boolean = false,
)

@Serializable
data class VotarEnqueteRequisicao(@SerialName("enquete_id") val enqueteId: Long, val opcoes: List<Long>)

/** `POST /enquete/encerrar` (🆕 5cad911). */
@Serializable
data class EncerrarEnqueteRequisicao(@SerialName("enquete_id") val enqueteId: Long)

/** `PATCH /enquete` (🆕 5cad911): `encerra_em` em ISO, ou [com.conversa.app.core.network.json.nuloExplicito] para tirar a data. */
@Serializable
data class PrazoEnqueteRequisicao(@SerialName("enquete_id") val enqueteId: Long, @SerialName("encerra_em") val encerraEm: JsonElement)
