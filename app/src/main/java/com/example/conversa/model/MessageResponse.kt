package com.example.conversa.model

import com.google.gson.annotations.SerializedName

data class MessageResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("remetente_id") val senderId: Int,
    @SerializedName("remetente") val senderName: String,
    @SerializedName("conversa_id") val conversationId: Int,
    @SerializedName("inserida") val insertedAt: String, // Data/hora como String ISO 8601
    @SerializedName("alterada") val alteredAt: String,
    @SerializedName("recebida") val received: Boolean,
    @SerializedName("visualizada") val viewed: Boolean,
    @SerializedName("reproduzida") val played: Boolean,
    @SerializedName("conteudos") val contents: List<MessageContent>
)