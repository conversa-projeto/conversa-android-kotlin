package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

/**
 * Representa uma mensagem no chat
 */
data class Mensagem(
    val id: Int,
    @SerializedName("conversa_id")
    val conversaId: Int,
    @SerializedName("remetente_id")
    val usuarioId: Int,
    val remetente: String,
    val inserida: String,
    val alterada: String?,
    val recebida: Boolean,
    val visualizada: Boolean,
    val reproduzida: Boolean,
    val conteudos: List<Conteudo>
)

/**
 * Conteúdo de uma mensagem (texto, imagem, arquivo, áudio)
 */
data class Conteudo(
    val id: Int,
    val tipo: Int, // 1=Texto, 2=Imagem, 3=Arquivo, 4=MensagemAudio
    val ordem: Int,
    val conteudo: String,
    val nome: String?,
    val extensao: String?
) {
    companion object {
        const val TIPO_TEXTO = 1
        const val TIPO_IMAGEM = 2
        const val TIPO_ARQUIVO = 3
        const val TIPO_AUDIO = 4
    }
}

/**
 * Request para enviar mensagem
 */
data class EnviarMensagemRequest(
    @SerializedName("conversa_id")
    val conversaId: Int,
    val conteudos: List<ConteudoRequest>
)

data class ConteudoRequest(
    val tipo: Int,
    val ordem: Int,
    val conteudo: String,
    val nome: String? = null,
    val extensao: String? = null
)

/**
 * Response do servidor ao enviar mensagem
 */
data class EnviarMensagemResponse(
    val id: Int,
    @SerializedName("usuario_id")
    val usuarioId: Int,
    @SerializedName("conversa_id")
    val conversaId: Int,
    val inserida: String,
    val alterada: String
)
