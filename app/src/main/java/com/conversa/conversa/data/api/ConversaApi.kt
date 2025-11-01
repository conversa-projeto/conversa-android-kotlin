package com.conversa.conversa.data.api

import com.conversa.conversa.data.model.AdicionarUsuarioRequest
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.CriarConversaRequest
import com.conversa.conversa.data.model.CriarConversaResponse
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
import okhttp3.RequestBody

interface ConversaApi {
    
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @GET("conversas")
    suspend fun listarConversas(
        @Header("Authorization") token: String
    ): Response<List<Conversa>>
    
    /**
     * Lista os contatos do usuário autenticado
     */
    @GET("usuario/contatos")
    suspend fun listarContatos(
        @Header("Authorization") token: String
    ): Response<List<Contato>>
    
    /**
     * Cria uma nova conversa (1:1 ou Grupo)
     */
    @PUT("conversa")
    suspend fun criarConversa(
        @Header("Authorization") token: String,
        @Body request: CriarConversaRequest
    ): Response<CriarConversaResponse>
    
    /**
     * Adiciona um usuário a uma conversa
     */
    @PUT("conversa/usuario")
    suspend fun adicionarUsuarioConversa(
        @Header("Authorization") token: String,
        @Body request: AdicionarUsuarioRequest
    ): Response<Unit>
    
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
    @GET("anexo")
    suspend fun downloadAnexo(
        @Header("Authorization") token: String,
        @Query("identificador") conteudoId: String
    ): Response<ResponseBody>
    
    /**
     * Verifica se um anexo já existe no servidor
     */
    @GET("anexo/existe")
    suspend fun verificarAnexoExiste(
        @Header("Authorization") token: String,
        @Query("identificador") identificador: String
    ): Response<AnexoExisteResponse>
    
    /**
     * Faz upload de um anexo (imagem, arquivo, áudio)
     */
    @PUT("anexo")
    suspend fun uploadAnexo(
        @Header("Authorization") token: String,
        @Query("tipo") tipo: Int,
        @Query("nome") nome: String,
        @Query("extensao") extensao: String,
        @Body arquivo: RequestBody
    ): Response<AnexoUploadResponse>
}

data class AnexoExisteResponse(
    val existe: Boolean
)

data class AnexoUploadResponse(
    val id: Int,
    val identificador: String,
    val tipo: Int,
    val tamanho: Long
)
