package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

// Cadastro de novo usuario — PUT /usuario
data class CadastrarUsuarioRequest(
    val nome: String,
    val login: String,
    val email: String,
    val senha: String,
    val telefone: String? = null,
)

// Atualizacao de perfil — PATCH /usuario
data class AtualizarUsuarioRequest(
    val nome: String? = null,
    val email: String? = null,
    val telefone: String? = null,
    @SerializedName("avatar_anexo_id") val avatarAnexoId: Int? = null,
)

// Alterar senha — POST /alterar-senha
data class AlterarSenhaRequest(
    @SerializedName("senha_atual") val senhaAtual: String,
    @SerializedName("senha_nova") val senhaNova: String,
)

// Perfil de usuario
data class UsuarioPerfil(
    val id: Int,
    val nome: String,
    val login: String,
    val email: String,
    val telefone: String?,
    @SerializedName("avatar_anexo_id") val avatarAnexoId: Int?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("avatar_identificador") val avatarIdentificador: String?,
)
