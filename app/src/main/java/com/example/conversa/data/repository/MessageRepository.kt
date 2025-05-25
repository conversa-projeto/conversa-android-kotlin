package com.example.conversa.data.repository

import android.util.Log
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.request.ContentItem
import com.example.conversa.data.request.SendMessageRequest
import com.example.conversa.model.Message
import com.example.conversa.model.MessageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant // Para parsing de data/hora

class MessageRepository(private val apiService: ApiService) {

    private val TAG = "MessageRepository"

    suspend fun sendMessage(chatId: Int, senderId: Int, messageText: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val contentItem = ContentItem(
                    content = messageText,
                    order = 0,
                    type = 1, // Tipo 1 para texto
                    name = "",
                    extension = ""
                )

                val requestBody = SendMessageRequest(
                    conversationId = chatId,
                    contents = listOf(contentItem)
                )

                val requestUrl = "/mensagem"
                val requestPayload = "{\"conversationId\":$chatId, \"contents\":[{\"content\":\"$messageText\", ...}]}"
                Log.d(TAG, "Requisição POST para: $requestUrl")
                Log.d(TAG, "Payload enviado: $requestPayload")

                val response = apiService.sendMessageText(requestBody)

                Log.d(TAG, "Resposta da requisição para: $requestUrl")
                Log.d(TAG, "Código de Status: ${response.code()}")
                if (response.isSuccessful) {
                    Log.d(TAG, "Corpo da Resposta (Sucesso): ${response.body()}")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Corpo da Resposta (Erro): $errorBody")
                }

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorString = response.errorBody()?.string()
                    Result.failure(Exception("Erro ao enviar mensagem: ${response.code()} - $errorString"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na requisição sendMessage: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getMessages(chatId: Int, currentUserId: Int): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestUrl = "/mensagens?conversa=$chatId&usuario=$currentUserId&mensagensprevias=50"
                Log.d(TAG, "Requisição GET para: $requestUrl")

                val messageResponses = apiService.getMessages(chatId, currentUserId, 50)

                Log.d(TAG, "Resposta da requisição para: $requestUrl")
                Log.d(TAG, "Mensagens recebidas (raw): ${messageResponses.size}")
                if (messageResponses.isNotEmpty()) {
                    Log.d(TAG, "Primeira mensagem recebida (raw): $messageResponses.first()")
                }


                val messages = messageResponses.map { response ->
                    val content = response.contents.firstOrNull()?.content ?: ""
                    val timestampMillis = try {
                        Instant.parse(response.insertedAt).toEpochMilli()
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao parsear timestamp: ${response.insertedAt}", e)
                        System.currentTimeMillis() // Fallback
                    }

                    val message = Message(
                        id = response.id,
                        chatId = response.conversationId,
                        senderId = response.senderId,
                        senderName = response.senderName,
                        content = content,
                        timestamp = timestampMillis,
                        isMine = response.senderId == currentUserId,
                        contents = response.contents
                    )
                    // Log da mensagem mapeada para o modelo de domínio
                    Log.d(TAG, "Mensagem mapeada: ID=${message.id}, Conteúdo='${message.content}', isMine=${message.isMine}")
                    message
                }
                Log.d(TAG, "Total de mensagens mapeadas para o modelo de domínio: ${messages.size}")
                Result.success(messages)
            } catch (e: Exception) {
                Log.e(TAG, "Erro na requisição getMessages: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}