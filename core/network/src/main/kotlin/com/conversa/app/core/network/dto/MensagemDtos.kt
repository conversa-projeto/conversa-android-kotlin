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
    /** Na mensagem da ligação: o chat criado nela, só para quem é membro (servidor `964ed72`). */
    @SerialName("chat_chamada_id") val chatChamadaId: Long? = null,
    /** Áudio: segundos e picos de 0 a 100, gravados no envio (servidor `ef13c22`); nulos no resto. */
    val duracao: Double? = null,
    val intensidade: List<Int>? = null,
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
    /** Postagem em grupo (servidor `12cf0cf`, contrato §10.15): as respostas ficam embaixo dela. */
    val postagem: PostagemDto? = null,
    /** Resposta de postagem: fica fora de `/mensagens` (só nas `ultimas` e em `/postagem/respostas`). */
    @SerialName("postagem_id") val postagemId: Long? = null,
    /** Confirmação de leitura pedida por quem enviou (`b8ea0c2`, `6d20ff7`, contrato §10.14). */
    val confirmacao: ConfirmacaoDto? = null,
    /** As pastas que eu enxergo em que a mensagem está (`ac710eb`, contrato §10.17); só vem quando há alguma. */
    val pastas: List<Long> = emptyList(),
)

@Serializable
data class PostagemDto(
    val titulo: String? = null,
    val respostas: Int = 0,
    @SerialName("nao_lidas") val naoLidas: Int = 0,
    /** As 2 últimas respostas, da mais antiga para a mais nova (inclui as apagadas). */
    val ultimas: List<MensagemDto> = emptyList(),
)

@Serializable
data class ConfirmacaoDto(
    /** Quantas pessoas precisam confirmar (o tamanho da lista). */
    val total: Int = 0,
    val confirmou: Boolean = false,
    @SerialName("pode_confirmar") val podeConfirmar: Boolean = false,
    val destinatarios: List<DestinatarioConfirmacaoDto> = emptyList(),
    val usuarios: List<ConfirmouDto> = emptyList(),
)

@Serializable
data class DestinatarioConfirmacaoDto(@SerialName("usuario_id") val usuarioId: Long, val nome: String = "")

@Serializable
data class ConfirmouDto(
    @SerialName("usuario_id") val usuarioId: Long,
    val nome: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("confirmada_em") val confirmadaEm: Instant? = null,
)

@Serializable
data class VisualizarPostagemRequisicao(@SerialName("postagem_id") val postagemId: Long)

@Serializable
data class RespostasVistasDto(val vistas: Int = 0)

@Serializable
data class ConteudoEnvioDto(val ordem: Int, val tipo: Int, val conteudo: String?)

/** `origem_mensagem_id` = a mensagem respondida/encaminhada (nome invertido em relação ao banco). */
@Serializable
data class ReferenciaEnvioDto(val tipo: Int, @SerialName("origem_mensagem_id") val origemMensagemId: Long)

/** `postagem` do `PUT /mensagem` (7.13): a mensagem vira uma postagem do grupo, com título opcional. */
@Serializable
data class PostagemEnvioDto(val titulo: String? = null)

@Serializable
data class EnviarMensagemRequisicao(
    @SerialName("conversa_id") val conversaId: Long,
    val conteudos: List<ConteudoEnvioDto>,
    @SerialName("visivel_em") val visivelEm: String? = null,
    @SerialName("mensagem_referencia") val mensagemReferencia: ReferenciaEnvioDto? = null,
    /** Postagem do grupo (7.13). */
    val postagem: PostagemEnvioDto? = null,
    /** Resposta a uma postagem (7.13): vai para baixo dela. O servidor recusa se for agendada. */
    @SerialName("postagem_id") val postagemId: Long? = null,
    /** Pede confirmação de leitura (FC-521). */
    @SerialName("pede_confirmacao") val pedeConfirmacao: Boolean? = null,
    /** Quem precisa confirmar (sem a lista: todos os outros membros). */
    @SerialName("confirmacao_usuarios") val confirmacaoUsuarios: List<Long>? = null,
)

/** `POST /mensagem/confirmar` (FC-521): corpo e resposta. */
@Serializable
data class MensagemIdDto(@SerialName("mensagem_id") val mensagemId: Long = 0)

/** `POST /mensagem/pedir-confirmacao` (FC-525): sem [usuarios], todos os outros membros. */
@Serializable
data class PedirConfirmacaoRequisicao(@SerialName("mensagem_id") val mensagemId: Long, val usuarios: List<Long>? = null)

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
