// com.example.conversa.data.request/ContentItem.kt
package com.example.conversa.data.request

import com.google.gson.annotations.SerializedName

/**
 * Representa um item de conteúdo dentro da lista 'conteudos' da requisição de mensagem.
 */
data class ContentItem(
    @SerializedName("conteudo") // Nome esperado pelo backend para o conteúdo (texto, URL do arquivo, etc.)
    val content: String,
    @SerializedName("ordem")
    val order: Int,
    @SerializedName("tipo")
    val type: Int, // Ex: 1 para texto, 2 para imagem, 3 para vídeo
    @SerializedName("nome")
    val name: String = "", // Nome do arquivo para mídias (vazio para texto)
    @SerializedName("extensao")
    val extension: String = "" // Extensão do arquivo para mídias (vazio para texto)
)