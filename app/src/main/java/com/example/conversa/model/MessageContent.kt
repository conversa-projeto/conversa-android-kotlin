package com.example.conversa.model

import com.google.gson.annotations.SerializedName

data class MessageContent(
    @SerializedName("id") val id: Int,
    @SerializedName("tipo") val type: Int,
    @SerializedName("ordem") val order: Int,
    @SerializedName("conteudo") val content: String,
    @SerializedName("nome") val name: String,
    @SerializedName("extensao") val extension: String
)