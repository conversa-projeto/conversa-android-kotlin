package com.conversa.conversa.data.api

import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.EnviarMensagemRequest
import com.conversa.conversa.data.model.EnviarMensagemResponse
import com.conversa.conversa.data.model.LoginRequest
import com.conversa.conversa.data.model.LoginResponse
import com.conversa.conversa.data.model.Mensagem
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ConversaApi {
    
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @GET("conversas")
    suspend fun listarConversas(
        @Header("Authorization") token: String
    ): Response<List<Conversa>>
    
    /**
     * Obtém mensagens de uma conversa
     * @param token JWT token
     * @param conversaId ID da conversa
     * @param mensagemReferencia ID da mensagem de referência (0 para última)
     * @param mensagensPrevias Quantidade de mensagens anteriores
     * @param mensagensSeguintes Quantidade de mensagens seguintes
     */
    @GET("mensagens")
    suspend fun obterMensagens(
        @Header("Authorization") token: String,
        @Query("conversa") conversaId: Int,
        @Query("mensagemreferencia") mensagemReferencia: Int = 0,
        @Query("mensagensprevias") mensagensPrevias: Int = 50,
        @Query("mensagensseguintes") mensagensSeguintes: Int = 0
    ): Response<List<Mensagem>>
    
    /**
     * Envia nova mensagem
     */
    @PUT("mensagem")
    suspend fun enviarMensagem(
        @Header("Authorization") token: String,
        @Body mensagem: EnviarMensagemRequest
    ): Response<EnviarMensagemResponse>
    
    /**
     * Marca mensagem como visualizada
     */
    @GET("mensagem/visualizar")
    suspend fun visualizarMensagem(
        @Header("Authorization") token: String,
        @Query("conversa") conversaId: Int,
        @Query("mensagem") mensagemId: Int
    ): Response<Unit>
    
    /**
     * Faz download de um anexo (imagem, arquivo, áudio)
     */
    @Streaming
    @GET("conteudo")
    suspend fun downloadAnexo(
        @Header("Authorization") token: String,
        @Query("id") conteudoId: Int
    ): Response<ResponseBody>
}
