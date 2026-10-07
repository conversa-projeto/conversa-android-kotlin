package com.conversa.conversa.data.api

import com.conversa.conversa.data.model.*
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.*
import okhttp3.RequestBody

interface ConversaApi {
    
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ========== USUARIO ==========

    /** Cadastrar novo usuario (signup) */
    @PUT("usuario")
    suspend fun cadastrarUsuario(@Body request: CadastrarUsuarioRequest): Response<UsuarioPerfil>

    /** Atualizar perfil do usuario autenticado */
    @PATCH("usuario")
    suspend fun atualizarUsuario(
        @Header("Authorization") token: String,
        @Body request: AtualizarUsuarioRequest,
    ): Response<UsuarioPerfil>

    /** Alterar senha */
    @POST("alterar-senha")
    suspend fun alterarSenha(
        @Header("Authorization") token: String,
        @Body request: AlterarSenhaRequest,
    ): Response<Unit>

    /** Deletar conta */
    @DELETE("usuario")
    suspend fun deletarUsuario(
        @Header("Authorization") token: String,
        @Query("id") id: Int,
    ): Response<Unit>

    // ========== DISPOSITIVO ==========

    /** Atualizar info do dispositivo (modelo, SO, token FCM) */
    @PATCH("dispositivo")
    suspend fun atualizarDispositivo(
        @Header("Authorization") token: String,
        @Body request: AtualizarDispositivoRequest,
    ): Response<DispositivoResponse>

    /** Vincular dispositivo ao usuario autenticado */
    @PUT("dispositivo/usuario")
    suspend fun vincularDispositivo(
        @Header("Authorization") token: String,
        @Query("dispositivo_id") dispositivoId: Int,
    ): Response<Unit>

    // ========== CONTATOS ==========

    /** Adicionar usuario aos meus contatos */
    @PUT("usuario/contato")
    suspend fun adicionarContato(
        @Header("Authorization") token: String,
        @Query("relacionamento_id") relacionamentoId: Int,
    ): Response<Unit>

    /** Remover contato */
    @DELETE("usuario/contato")
    suspend fun removerContato(
        @Header("Authorization") token: String,
        @Query("id") id: Int,
    ): Response<Unit>

    /** Lista de IDs de usuarios online no momento */
    @GET("contatos/online")
    suspend fun listarContatosOnline(
        @Header("Authorization") token: String,
    ): Response<List<Int>>

    @GET("conversas")
    suspend fun listarConversas(
        @Header("Authorization") token: String
    ): Response<List<Conversa>>

    /**
     * Obtém dados completos de uma conversa
     */
    @GET("conversa/dados")
    suspend fun obterDadosConversa(
        @Header("Authorization") token: String,
        @Query("id") conversaId: Int
    ): Response<ConversaCompleta>

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

    /** Remove usuario de conversa (grupo) */
    @DELETE("conversa/usuario")
    suspend fun removerUsuarioConversa(
        @Header("Authorization") token: String,
        @Query("id") conversaUsuarioId: Int,
    ): Response<Unit>

    /** Atualiza conversa (rename grupo) */
    @PATCH("conversa")
    suspend fun atualizarConversa(
        @Header("Authorization") token: String,
        @Body request: Map<String, Any?>,
    ): Response<Unit>

    /** Deleta conversa */
    @DELETE("conversa")
    suspend fun deletarConversa(
        @Header("Authorization") token: String,
        @Query("id") id: Int,
    ): Response<Unit>

    /** Lista usuarios da conversa */
    @GET("conversa/usuarios")
    suspend fun listarUsuariosConversa(
        @Header("Authorization") token: String,
        @Query("conversa") conversaId: Int,
    ): Response<List<Contato>>

    /** Broadcast "estou digitando" */
    @POST("conversa/digitando")
    suspend fun broadcastDigitando(
        @Header("Authorization") token: String,
        @Body request: DigitandoRequest,
    ): Response<Unit>

    /** Broadcast "gravando audio" */
    @POST("conversa/gravando")
    suspend fun broadcastGravando(
        @Header("Authorization") token: String,
        @Body request: DigitandoRequest,
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

    /** Marca audio como reproduzido */
    @POST("mensagem/reproduzir")
    suspend fun reproduzirMensagem(
        @Header("Authorization") token: String,
        @Body request: Map<String, Int>, // { "mensagem_id": X, "conversa_id": Y }
    ): Response<Unit>

    /** Deletar mensagem */
    @DELETE("mensagem")
    suspend fun deletarMensagem(
        @Header("Authorization") token: String,
        @Query("id") id: Int,
    ): Response<Unit>

    /** Envia mensagem com referencia (responder/encaminhar) ou agendamento */
    @PUT("mensagem")
    suspend fun enviarMensagemComReferencia(
        @Header("Authorization") token: String,
        @Body request: EnviarMensagemComReferenciaRequest,
    ): Response<EnviarMensagemResponse>

    /** Sync incremental — mensagens novas desde timestamp */
    @GET("mensagens/novas")
    suspend fun obterMensagensNovas(
        @Header("Authorization") token: String,
        @Query("desde") desde: String, // ISO-8601
    ): Response<List<MensagemNovaItem>>

    /** Status de entrega/visualizacao/reproducao em batch */
    @GET("mensagem/status")
    suspend fun statusMensagem(
        @Header("Authorization") token: String,
        @Query("conversa") conversaId: Int,
        @Query("mensagem") mensagemId: Int,
    ): Response<List<MensagemStatusResponse>>

    /** Toggle de emoji reaction */
    @PUT("mensagem/reacao")
    suspend fun reagirMensagem(
        @Header("Authorization") token: String,
        @Body request: ReacaoRequest,
    ): Response<ReacaoResponse>

    /** Busca full-text (em conversa especifica ou global) */
    @GET("pesquisar")
    suspend fun pesquisar(
        @Header("Authorization") token: String,
        @Query("texto") texto: String,
        @Query("conversa") conversaId: Int? = null,
        @Query("usuario") usuarioId: Int? = null,
    ): Response<List<PesquisaResultado>>
    
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

    /** Confirma upload completo ao MinIO (chama depois do PUT presigned) */
    @POST("anexo/confirmar")
    suspend fun confirmarAnexo(
        @Header("Authorization") token: String,
        @Query("identificador") identificador: String,
    ): Response<Unit>

    /** Listagem de anexos (galeria) */
    @GET("anexos")
    suspend fun listarAnexos(
        @Header("Authorization") token: String,
        @Query("conversa") conversaId: Int? = null,
        @Query("autor") autorId: Int? = null,
        @Query("direcao") direcao: String? = null, // "enviados" / "recebidos"
        @Query("tipos") tipos: String? = null, // CSV de tipos (ex: "2,3,4")
        @Query("antes") antes: Int? = null, // paginacao (id < antes)
        @Query("limite") limite: Int? = null,
    ): Response<List<AnexoListItem>>
    
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

    /**
     * Lista o historico de chamadas do usuario
     */
    @GET("chamadas")
    suspend fun listarHistoricoChamadas(
        @Header("Authorization") token: String
    ): Response<List<HistoricoChamada>>

    /** Adiciona participante a chamada ativa */
    @PUT("chamada/usuario")
    suspend fun adicionarUsuarioChamada(
        @Header("Authorization") token: String,
        @Body request: Map<String, Any>, // { "chamada_id": X, "usuario_id": Y }
    ): Response<Unit>

    /** Upgrade audio -> video (dispara evento WS 56) */
    @POST("chamada/video")
    suspend fun ativarVideoChamada(
        @Header("Authorization") token: String,
        @Body request: ChamadaIdRequest,
    ): Response<Unit>

    /** Chamadas pendentes (onde sou usuario com status=Pendente) */
    @GET("chamadas/pendentes")
    suspend fun listarChamadasPendentes(
        @Header("Authorization") token: String,
    ): Response<List<ChamadaResponse>>

    // ========== SIP ==========

    @GET("sip")
    suspend fun obterSip(
        @Header("Authorization") token: String,
    ): Response<SipConfig>

    @PUT("sip")
    suspend fun criarSip(
        @Header("Authorization") token: String,
        @Body request: SipRequest,
    ): Response<SipConfig>

    @PATCH("sip")
    suspend fun atualizarSip(
        @Header("Authorization") token: String,
        @Body request: SipRequest,
    ): Response<SipConfig>
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
