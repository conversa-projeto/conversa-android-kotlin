@file:UseSerializers(InstantFlexivelSerializer::class)

package com.conversa.app.core.network.dto

import com.conversa.app.core.network.json.InstantFlexivelSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

// Autenticação, usuário, dispositivo e contatos (contrato §2, §5.1, §7.3).

/** Item de `GET /contatos/presenca`: [estado] é `ativo`/`ausente`/`offline`; [vistoEm] nulo se a pessoa esconde. */
@Serializable
data class PresencaContatoDto(
    @SerialName("usuario_id") val usuarioId: Long,
    val estado: String = "offline",
    @SerialName("visto_em") val vistoEm: Instant? = null,
)

@Serializable
data class LoginRequisicao(val login: String, val senha: String, @SerialName("dispositivo_id") val dispositivoId: Long? = null)

@Serializable
data class LoginResposta(
    val id: Long,
    val nome: String,
    val email: String? = null,
    val telefone: String? = null,
    @SerialName("avatar_identificador") val avatarIdentificador: String? = null,
    val dispositivo: DispositivoDto? = null,
    /** Vazio = resposta inválida ("Resposta de login inválida", AUT-01). */
    val token: String = "",
)

@Serializable
data class DispositivoDto(
    val id: Long,
    val nome: String? = null,
    val modelo: String? = null,
    @SerialName("versao_so") val versaoSo: String? = null,
    val plataforma: String? = null,
    val ativo: Boolean? = null,
    @SerialName("token_fcm") val tokenFcm: String? = null,
)

/**
 * `PATCH /dispositivo`. Campos `null` não são enviados; para limpar o token
 * (logout) use `tokenFcm = nuloExplicito`.
 * Limites: nome/modelo 50, versão/plataforma 15 (acima disso o servidor dá 500).
 */
@Serializable
data class AlterarDispositivoRequisicao(
    val id: Long,
    val nome: String? = null,
    val modelo: String? = null,
    @SerialName("versao_so") val versaoSo: String? = null,
    val plataforma: String? = null,
    @SerialName("token_fcm") val tokenFcm: JsonElement? = null,
)

/** `GET /cadastro` (pública): se a tela de login oferece o cadastro (servidor `ef13c22`). */
@Serializable
data class CadastroDto(val aberto: Boolean = true)

@Serializable
data class CadastroRequisicao(val nome: String, val login: String, val email: String, val senha: String, val telefone: String? = null)

@Serializable
data class UsuarioDto(
    val id: Long,
    val nome: String,
    val login: String? = null,
    val email: String? = null,
    val telefone: String? = null,
    @SerialName("avatar_anexo_id") val avatarAnexoId: Long? = null,
    @SerialName("criado_em") val criadoEm: Instant? = null,
)

/** `PATCH /usuario`. `telefone` e `avatarAnexoId` aceitam `nuloExplicito` para limpar. */
@Serializable
data class AlterarUsuarioRequisicao(
    val id: Long,
    val nome: String? = null,
    val email: String? = null,
    val telefone: JsonElement? = null,
    @SerialName("avatar_anexo_id") val avatarAnexoId: JsonElement? = null,
    /** Privacidade (FC-810, servidor `769bd7d`): vai só a chave que mudou. */
    @SerialName("mostrar_visto_em") val mostrarVistoEm: Boolean? = null,
    @SerialName("mostrar_na_conversa") val mostrarNaConversa: Boolean? = null,
    @SerialName("aparecer_offline") val aparecerOffline: Boolean? = null,
)

/** `GET /usuario/privacidade`. */
@Serializable
data class PrivacidadeDto(
    @SerialName("mostrar_visto_em") val mostrarVistoEm: Boolean = true,
    @SerialName("mostrar_na_conversa") val mostrarNaConversa: Boolean = true,
    @SerialName("aparecer_offline") val aparecerOffline: Boolean = false,
)

@Serializable
data class AlterarSenhaRequisicao(@SerialName("senha_atual") val senhaAtual: String, val senha: String)

@Serializable
data class ContatoDto(
    val id: Long,
    val nome: String,
    val login: String = "",
    val email: String? = null,
    val telefone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
