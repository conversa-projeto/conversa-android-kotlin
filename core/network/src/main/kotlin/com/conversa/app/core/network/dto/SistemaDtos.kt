@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Atividades, permissões, parâmetros e SIP (contrato §12, §13, §14).

/** `GET /recursos` (servidor `769bd7d`): o transcritor e a IA configurados. */
@Serializable
data class RecursosDto(val transcricao: Boolean = false, val ia: Boolean = false)

@Serializable
data class AtividadeDto(
    val id: Long,
    val tipo: Int,
    @SerialName("criado_em") val criadoEm: Instant? = null,
    /** Servidor `ef13c22`: substitui o `nova` (o que chegou desde a última visita à aba). */
    val pendente: Boolean = false,
    @SerialName("autor_id") val autorId: Long? = null,
    @SerialName("autor_nome") val autorNome: String? = null,
    @SerialName("autor_avatar_url") val autorAvatarUrl: String? = null,
    @SerialName("conversa_id") val conversaId: Long? = null,
    @SerialName("conversa_tipo") val conversaTipo: Int? = null,
    @SerialName("conversa_descricao") val conversaDescricao: String? = null,
    @SerialName("mensagem_id") val mensagemId: Long? = null,
    @SerialName("conteudo_tipo") val conteudoTipo: Int? = null,
    val texto: String? = null,
    @SerialName("chamada_id") val chamadaId: Long? = null,
    @SerialName("chamada_tipo") val chamadaTipo: Int? = null,
    val emoji: String? = null,
)

@Serializable
data class QuantidadeDto(val quantidade: Int = 0)

/** `POST /atividades/reacoes-vistas`: as minhas mensagens com reação que apareceram na tela. */
@Serializable
data class ReacoesVistasRequisicao(val mensagens: List<Long>)

@Serializable
data class ConcluidasDto(val concluidas: Int = 0)

@Serializable
data class VistasDto(@SerialName("vistas_em") val vistasEm: Instant? = null)

@Serializable
data class PermissaoDto(val codigo: String, val descricao: String = "", val uso: Boolean = false)

@Serializable
data class UsuarioPermissoesDto(
    val id: Long,
    val nome: String,
    val login: String = "",
    val permissoes: List<String> = emptyList(),
    /** Servidor `ac710eb`: "Usuário desde", se é bot e os bytes dos arquivos que enviou. */
    @SerialName("criado_em") val criadoEm: Instant? = null,
    val bot: Boolean = false,
    val espaco: Double = 0.0,
)

@Serializable
data class PermissoesDto(
    val permissoes: List<PermissaoDto> = emptyList(),
    val usuarios: List<UsuarioPermissoesDto> = emptyList(),
    @SerialName("modo_aberto") val modoAberto: Boolean = false,
)

@Serializable
data class PermissaoUsuarioDto(@SerialName("usuario_id") val usuarioId: Long, val codigo: String)

@Serializable
data class ParametrosDto(
    @SerialName("fcm_project_id") val fcmProjectId: String? = null,
    @SerialName("fcm_client_email") val fcmClientEmail: String? = null,
    @SerialName("fcm_private_key_configurada") val fcmPrivateKeyConfigurada: Boolean = false,
    @SerialName("turn_forcar_relay") val turnForcarRelay: Boolean = true,
    @SerialName("transcritor_url") val transcritorUrl: String? = null,
    @SerialName("transcritor_idioma") val transcritorIdioma: String? = null,
    @SerialName("gravacao_dias") val gravacaoDias: Int = 0,
    @SerialName("s3_bucket") val s3Bucket: String? = null,
    @SerialName("cadastro_aberto") val cadastroAberto: Boolean = true,
)

/** `PATCH /parametros`: só os campos alterados (nulos não são enviados). */
@Serializable
data class AlterarParametrosRequisicao(
    @SerialName("fcm_project_id") val fcmProjectId: String? = null,
    @SerialName("fcm_client_email") val fcmClientEmail: String? = null,
    @SerialName("fcm_private_key") val fcmPrivateKey: String? = null,
    @SerialName("turn_forcar_relay") val turnForcarRelay: Boolean? = null,
    @SerialName("transcritor_url") val transcritorUrl: String? = null,
    @SerialName("transcritor_idioma") val transcritorIdioma: String? = null,
    @SerialName("gravacao_dias") val gravacaoDias: Int? = null,
    @SerialName("cadastro_aberto") val cadastroAberto: Boolean? = null,
)

/** `GET /sip` devolve a linha ou `{}` (então tudo é opcional). */
@Serializable
data class SipDto(
    val id: Long? = null,
    @SerialName("usuario_id") val usuarioId: Long? = null,
    @SerialName("sip_user") val sipUser: String? = null,
    @SerialName("auth_user") val authUser: String? = null,
    @SerialName("sip_password") val sipPassword: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val domain: String? = null,
    @SerialName("ws_server") val wsServer: String? = null,
    val ativo: Boolean? = null,
)
