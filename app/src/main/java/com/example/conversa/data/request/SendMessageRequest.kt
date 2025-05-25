// com.example.conversa.data.request/SendMessageRequest.kt
package com.example.conversa.data.request

import com.google.gson.annotations.SerializedName

/**
 * Representa o corpo completo da requisição para enviar uma mensagem.
 * Formato esperado pelo backend:
 * {
 * "conversa_id": 41,
 * "conteudos": [{
 * "conteudo": "Teste",
 * "ordem": 0,
 * "tipo": 1,
 * "nome": "",
 * "extensao": ""
 * }]
 * }
 */
data class SendMessageRequest(
    @SerializedName("conversa_id")
    val conversationId: Int,
    @SerializedName("conteudos")
    val contents: List<ContentItem>
)