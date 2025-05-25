package com.example.conversa.data.remote

import com.example.conversa.data.request.LoginRequest
import com.example.conversa.data.request.CreateConversationRequest
import com.example.conversa.data.request.SendMessageRequest
import com.example.conversa.data.response.LoginResponse
import com.example.conversa.data.response.CreateConversationResponse
import com.example.conversa.model.Contact
import com.example.conversa.model.Conversation
import com.example.conversa.model.MessageResponse // Importe o novo modelo de resposta
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query // Importe Query para parâmetros de consulta

interface ApiService {

    @POST("/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("/usuario/contatos")
    suspend fun getContacts(): List<Contact>

    @GET("/conversas")
    suspend fun getConversations(): List<Conversation>

    @PUT("/conversa")
    suspend fun createConversation(@Body request: CreateConversationRequest): CreateConversationResponse

    @PUT("/mensagem")
    suspend fun sendMessageText(@Body request: SendMessageRequest): Response<Unit>

    @Multipart
    @PUT("/mensagem")
    suspend fun uploadMessageFile(
        @Part("json_data") json_data: RequestBody,
        @Part arquivo: MultipartBody.Part
    ): String

    // Endpoint corrigido para buscar mensagens com query parameters
    @GET("/mensagens")
    suspend fun getMessages(
        @Query("conversa") conversationId: Int,
        @Query("usuario") userId: Int,
        @Query("mensagensprevias") mensagensprevias: Int,
    ): List<MessageResponse>
}