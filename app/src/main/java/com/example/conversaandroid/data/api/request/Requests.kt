package com.example.conversaandroid.data.api.request

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val login: String,
    val senha: String,
    @SerializedName("dispositivo_id")
    val dispositivoId: String? = null
)

data class CriarUsuarioRequest(
    val nome: String,
    val login: String,
    val email: String,
    val telefone: String,
    val senha: String
)

data class AtualizarUsuarioRequest(
    val id: Int,
    val nome: String? = null,
    val telefone: String? = null
)

data class CriarConversaRequest(
    val descricao: String,
    val tipo: Int = 1
)

data class AdicionarUsuarioConversaRequest(
    @SerializedName("usuario_id")
    val usuarioId: Int,
    @SerializedName("conversa_id")
    val conversaId: Int
)

data class EnviarMensagemRequest(
    @SerializedName("conversa_id")
    val conversaId: Int,
    val conteudos: List<ConteudoRequest>
)

data class ConteudoRequest(
    val ordem: Int,
    val tipo: Int,
    val conteudo: String? = null,
    val identificador: String? = null,
    val nome: String? = null,
    val extensao: String? = null
)