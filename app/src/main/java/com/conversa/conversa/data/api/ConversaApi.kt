package com.conversa.conversa.data.api

import com.conversa.conversa.data.model.*
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.*
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
    
    // ========== ENDPOINTS DE CHAMADAS ==========
    
    /**
     * Inicia uma nova chamada
     */
    @PUT("chamada/iniciar")
    suspend fun iniciarChamada(
        @Header("Authorization") token: String,
        @Body request: IniciarChamadaRequest
    ): Response<ChamadaResponse>
    
    /**
     * Aceita e entra em uma chamada
     */
    @POST("chamada/entrar")
    suspend fun entrarChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest
    ): Response<Unit>
    
    /**
     * Recusa uma chamada
     */
    @POST("chamada/recusar")
    suspend fun recusarChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest
    ): Response<Unit>
    
    /**
     * Sai da chamada (mantém a chamada ativa)
     */
    @POST("chamada/sair")
    suspend fun sairChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest
    ): Response<Unit>
    
    /**
     * Cancela uma chamada (antes de alguém entrar)
     */
    @POST("chamada/cancelar")
    suspend fun cancelarChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest
    ): Response<Unit>
    
    /**
     * Finaliza uma chamada (forçadamente)
     */
    @POST("chamada/finalizar")
    suspend fun finalizarChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest
    ): Response<Unit>
    
    /**
     * Obtém dados completos de uma chamada
     */
    @GET("chamada/dados")
    suspend fun obterDadosChamada(
        @Header("Authorization") token: String,
        @Query("id") chamadaId: Int
    ): Response<ChamadaResponse>
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
