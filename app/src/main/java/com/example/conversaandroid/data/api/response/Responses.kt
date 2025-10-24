package com.example.conversaandroid.data.api.response

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    val id: Int,
    val nome: String,
    val email: String,
    val telefone: String?,
    val token: String,
    val dispositivo: DispositivoResponse?
)

data class DispositivoResponse(
    val id: Int,
    val nome: String?,
    val modelo: String?,
    @SerializedName("versao_so")
    val versaoSO: String?,
    val plataforma: String?,
    val ativo: Boolean
)

data class UsuarioResponse(
    val id: Int,
    val nome: String,
    val login: String,
    val email: String,
    val telefone: String?
)

data class ConversaResponse(
    val id: Int,
    val tipo: Int,
    val descricao: String?,
    @SerializedName("destinatario_id")
    val destinatarioId: Int?,
    val nome: String?,
    @SerializedName("ultima_mensagem_texto")
    val ultimaMensagemTexto: String?,
    @SerializedName("ultima_mensagem")
    val ultimaMensagem: Long?,
    val inserida: Long,
    @SerializedName("mensagem_id")
    val mensagemId: Int?,
    @SerializedName("mensagens_sem_visualizar")
    val mensagensSemVisualizar: Int
)

data class MensagemResponse(
    val id: Int,
    @SerializedName("conversa_id")
    val conversaId: Int,
    @SerializedName("remetente_id")
    val remetenteId: Int,
    val remetente: String?,
    val inserida: Long,
    val alterada: Long?,
    val recebida: Boolean,
    val visualizada: Boolean,
    val reproduzida: Boolean,
    val conteudos: List<ConteudoResponse>
)

data class ConteudoResponse(
    val id: Int,
    val tipo: Int,
    val ordem: Int,
    val conteudo: String?,
    val nome: String?,
    val extensao: String?,
    val identificador: String?
)

data class AnexoResponse(
    val id: Int,
    val identificador: String,
    val tipo: Int,
    val tamanho: Long
)

data class ExisteResponse(
    val existe: Boolean
)

data class StatusResponse(
    val ativo: Boolean
)

data class ErrorResponse(
    val error: String
)