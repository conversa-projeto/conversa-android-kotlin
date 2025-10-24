package com.example.conversaandroid.data.api


import com.seudominio.conversa.data.api.request.*
import com.seudominio.conversa.data.api.response.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ConversaApiService {

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @PUT("usuario")
    suspend fun criarUsuario(
        @Body request: CriarUsuarioRequest
    ): Response<UsuarioResponse>

    @PATCH("usuario")
    suspend fun atualizarUsuario(
        @Body request: AtualizarUsuarioRequest
    ): Response<UsuarioResponse>

    @DELETE("usuario")
    suspend fun deletarUsuario(
        @Query("id") id: Int
    ): Response<Unit>

    @GET("usuario/contatos")
    suspend fun getContatos(): Response<List<UsuarioResponse>>

    @GET("conversas")
    suspend fun getConversas(): Response<List<ConversaResponse>>

    @PUT("conversa")
    suspend fun criarConversa(
        @Body request: CriarConversaRequest
    ): Response<ConversaResponse>

    @PUT("conversa/usuario")
    suspend fun adicionarUsuarioConversa(
        @Body request: AdicionarUsuarioConversaRequest
    ): Response<Unit>

    @GET("mensagens")
    suspend fun getMensagens(
        @Query("conversa") conversaId: Int,
        @Query("mensagemreferencia") mensagemReferencia: Int = 0,
        @Query("mensagensprevias") mensagensPrevias: Int = 20,
        @Query("mensagensseguintes") mensagensSeguintes: Int = 0
    ): Response<List<MensagemResponse>>

    @PUT("mensagem")
    suspend fun enviarMensagem(
        @Body request: EnviarMensagemRequest
    ): Response<MensagemResponse>

    @GET("mensagem/visualizar")
    suspend fun visualizarMensagem(
        @Query("conversa") conversaId: Int,
        @Query("mensagem") mensagemId: Int
    ): Response<Unit>

    @GET("mensagens/novas")
    suspend fun getMensagensNovas(
        @Query("ultima") ultimaMensagemId: Int
    ): Response<List<MensagemResponse>>

    @Multipart
    @PUT("anexo")
    suspend fun uploadAnexo(
        @Query("tipo") tipo: Int,
        @Query("nome") nome: String,
        @Query("extensao") extensao: String,
        @Part file: MultipartBody.Part
    ): Response<AnexoResponse>

    @GET("anexo")
    suspend fun downloadAnexo(
        @Query("identificador") identificador: String
    ): Response<ResponseBody>

    @GET("anexo/existe")
    suspend fun verificarAnexoExiste(
        @Query("identificador") identificador: String
    ): Response<ExisteResponse>

    @GET("status")
    suspend fun getStatus(): Response<StatusResponse>

    @GET("pesquisar")
    suspend fun pesquisar(
        @Query("usuario") usuarioId: Int,
        @Query("texto") texto: String
    ): Response<List<MensagemResponse>>
}