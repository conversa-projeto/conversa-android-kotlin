package com.conversa.app.core.network.api

import com.conversa.app.core.network.dto.AdicionarUsuarioChamadaRequisicao
import com.conversa.app.core.network.dto.AlterarConversaRequisicao
import com.conversa.app.core.network.dto.AlterarDispositivoRequisicao
import com.conversa.app.core.network.dto.AlterarParametrosRequisicao
import com.conversa.app.core.network.dto.AlterarSenhaRequisicao
import com.conversa.app.core.network.dto.AlterarUsuarioRequisicao
import com.conversa.app.core.network.dto.AnexoExisteDto
import com.conversa.app.core.network.dto.AnexoItemDto
import com.conversa.app.core.network.dto.ArquivadaResposta
import com.conversa.app.core.network.dto.ArquivarRequisicao
import com.conversa.app.core.network.dto.AtividadeDto
import com.conversa.app.core.network.dto.CadastroRequisicao
import com.conversa.app.core.network.dto.ChamadaHistoricoDto
import com.conversa.app.core.network.dto.ChamadaPendenteDto
import com.conversa.app.core.network.dto.ChatChamadaDto
import com.conversa.app.core.network.dto.ConfirmarAnexoDto
import com.conversa.app.core.network.dto.ContatoDto
import com.conversa.app.core.network.dto.ConversaCriadaDto
import com.conversa.app.core.network.dto.ConversaDto
import com.conversa.app.core.network.dto.CriarConversaRequisicao
import com.conversa.app.core.network.dto.CriarEnqueteRequisicao
import com.conversa.app.core.network.dto.DadosChamadaDto
import com.conversa.app.core.network.dto.DispositivoDto
import com.conversa.app.core.network.dto.EncerrarEnqueteRequisicao
import com.conversa.app.core.network.dto.EnqueteCriadaDto
import com.conversa.app.core.network.dto.EnqueteDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.dto.FixadasDto
import com.conversa.app.core.network.dto.IceDto
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.IdentificadorDto
import com.conversa.app.core.network.dto.IncluirAnexoRequisicao
import com.conversa.app.core.network.dto.IncluirAnexoResposta
import com.conversa.app.core.network.dto.IncluirMembroRequisicao
import com.conversa.app.core.network.dto.IniciarChamadaRequisicao
import com.conversa.app.core.network.dto.LoginRequisicao
import com.conversa.app.core.network.dto.LoginResposta
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.MembroDto
import com.conversa.app.core.network.dto.MensagemCriadaDto
import com.conversa.app.core.network.dto.MensagemDto
import com.conversa.app.core.network.dto.MensagemExcluidaDto
import com.conversa.app.core.network.dto.NovaMensagemDto
import com.conversa.app.core.network.dto.ParametrosDto
import com.conversa.app.core.network.dto.PermissaoUsuarioDto
import com.conversa.app.core.network.dto.PermissoesDto
import com.conversa.app.core.network.dto.PrazoEnqueteRequisicao
import com.conversa.app.core.network.dto.QuantidadeDto
import com.conversa.app.core.network.dto.ReacaoRequisicao
import com.conversa.app.core.network.dto.ReacaoResposta
import com.conversa.app.core.network.dto.RecusarChamadaRequisicao
import com.conversa.app.core.network.dto.SipDto
import com.conversa.app.core.network.dto.StatusDetalheDto
import com.conversa.app.core.network.dto.StatusMensagemDto
import com.conversa.app.core.network.dto.SucessoDto
import com.conversa.app.core.network.dto.TranscricaoDto
import com.conversa.app.core.network.dto.UrlAnexoDto
import com.conversa.app.core.network.dto.UsuarioDto
import com.conversa.app.core.network.dto.VinculoConversaDto
import com.conversa.app.core.network.dto.VistasDto
import com.conversa.app.core.network.dto.VotarEnqueteRequisicao
import com.conversa.app.core.network.http.AutenticacaoInterceptor
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

private const val PUBLICA = AutenticacaoInterceptor.CABECALHO_PUBLICA + ": 1"

/**
 * Todas as rotas REST do servidor (contrato §4, 67 rotas). Caminhos relativos a `<base>/api/`.
 * O token é colocado pelo [AutenticacaoInterceptor]; rotas públicas levam o cabeçalho marcador.
 */
interface ConversaApi {
    // --- Públicas ---

    @Headers(PUBLICA)
    @POST("login")
    suspend fun login(@Body corpo: LoginRequisicao): LoginResposta

    @Headers(PUBLICA)
    @PUT("usuario")
    suspend fun cadastrar(@Body corpo: CadastroRequisicao): UsuarioDto

    // --- Usuário, dispositivo, contatos ---

    @POST("alterar-senha")
    suspend fun alterarSenha(@Body corpo: AlterarSenhaRequisicao): JsonObject

    @PATCH("dispositivo")
    suspend fun alterarDispositivo(@Body corpo: AlterarDispositivoRequisicao): DispositivoDto

    @PUT("dispositivo/usuario")
    suspend fun vincularDispositivo(@Query("dispositivo_id") dispositivoId: Long): JsonObject

    @PATCH("usuario")
    suspend fun alterarUsuario(@Body corpo: AlterarUsuarioRequisicao): UsuarioDto

    @DELETE("usuario")
    suspend fun excluirUsuario(@Query("id") id: Long): UsuarioDto

    @PUT("usuario/contato")
    suspend fun incluirContato(@Query("relacionamento_id") usuarioId: Long): JsonObject

    @DELETE("usuario/contato")
    suspend fun excluirContato(@Query("id") id: Long): JsonObject

    @GET("usuario/contatos")
    suspend fun contatos(): List<ContatoDto>

    @GET("contatos/online")
    suspend fun contatosOnline(): List<Long>

    // --- Conversas ---

    @PUT("conversa")
    suspend fun criarConversa(@Body corpo: CriarConversaRequisicao): ConversaCriadaDto

    @PATCH("conversa")
    suspend fun alterarConversa(@Body corpo: AlterarConversaRequisicao): ConversaCriadaDto

    /** Não usar: falha com 500 por chave estrangeira (contrato §11.6). */
    @DELETE("conversa")
    suspend fun excluirConversa(@Query("id") id: Long): JsonObject

    @GET("conversas")
    suspend fun conversas(): List<ConversaDto>

    @PATCH("conversa/fixadas")
    suspend fun ordenarFixadas(@Body corpo: FixadasDto): FixadasDto

    @PATCH("conversa/arquivada")
    suspend fun arquivarConversa(@Body corpo: ArquivarRequisicao): ArquivadaResposta

    @GET("conversa/usuarios")
    suspend fun membros(@Query("conversa") conversaId: Long): List<MembroDto>

    @PUT("conversa/usuario")
    suspend fun incluirMembro(@Body corpo: IncluirMembroRequisicao): VinculoConversaDto

    /** `id` = id do vínculo (`conversa_usuario`), não do usuário. */
    @DELETE("conversa/usuario")
    suspend fun excluirMembro(@Query("id") vinculoId: Long): JsonObject

    @POST("conversa/digitando")
    suspend fun avisarDigitando(@Body corpo: IdDto): JsonObject

    @POST("conversa/gravando")
    suspend fun avisarGravando(@Body corpo: IdDto): JsonObject

    // --- Mensagens ---

    @PUT("mensagem")
    suspend fun enviarMensagem(@Body corpo: EnviarMensagemRequisicao): MensagemCriadaDto

    @DELETE("mensagem")
    suspend fun ocultarMensagem(@Query("id") id: Long): MensagemExcluidaDto

    /** No máximo 100 por chamada; sempre em ordem crescente (contrato §10.4). */
    @GET("mensagens")
    suspend fun mensagens(
        @Query("conversa") conversaId: Long,
        @Query("mensagemreferencia") mensagemReferencia: Long = 0,
        @Query("mensagensprevias") mensagensPrevias: Int = 0,
        @Query("mensagensseguintes") mensagensSeguintes: Int = 0,
    ): List<MensagemDto>

    @POST("mensagem/visualizar")
    suspend fun visualizar(@Body corpo: MarcarStatusRequisicao): SucessoDto

    @POST("mensagem/reproduzir")
    suspend fun reproduzir(@Body corpo: MarcarStatusRequisicao): SucessoDto

    /** `mensagens` = ids separados por vírgula. */
    @GET("mensagem/status")
    suspend fun statusMensagens(@Query("conversa") conversaId: Long, @Query("mensagem") mensagens: String): List<StatusMensagemDto>

    @GET("mensagem/status/detalhe")
    suspend fun statusDetalhe(@Query("id") mensagemId: Long): List<StatusDetalheDto>

    @GET("mensagens/novas")
    suspend fun mensagensNovas(@Query("desde") desde: String = ""): List<NovaMensagemDto>

    /** `conversa = 0` pesquisa em todas. */
    @GET("pesquisar")
    suspend fun pesquisar(@Query("texto") texto: String, @Query("conversa") conversaId: Long = 0): List<MensagemDto>

    @PUT("mensagem/reacao")
    suspend fun reagir(@Body corpo: ReacaoRequisicao): ReacaoResposta

    // --- Anexos ---

    @GET("anexo/existe")
    suspend fun anexoExiste(@Query("identificador") identificador: String): AnexoExisteDto

    @GET("anexo")
    suspend fun urlAnexo(@Query("identificador") identificador: String): UrlAnexoDto

    @PUT("anexo")
    suspend fun incluirAnexo(@Body corpo: IncluirAnexoRequisicao): IncluirAnexoResposta

    /** O identificador vai na **query**, sem corpo. */
    @POST("anexo/confirmar")
    suspend fun confirmarAnexo(@Query("identificador") identificador: String): ConfirmarAnexoDto

    @GET("anexos")
    suspend fun anexos(
        @Query("conversa") conversaId: Long = 0,
        @Query("autor") autorId: Long = 0,
        @Query("direcao") direcao: String = "",
        @Query("tipos") tipos: String = "",
        @Query("antes") antes: Long = 0,
        @Query("limite") limite: Int = 0,
    ): List<AnexoItemDto>

    @GET("anexo/transcricao")
    suspend fun transcricao(@Query("identificador") identificador: String): TranscricaoDto

    @PUT("anexo/transcricao")
    suspend fun transcrever(@Body corpo: IdentificadorDto): TranscricaoDto

    // --- Chamadas ---

    @PUT("chamada/iniciar")
    suspend fun iniciarChamada(@Body corpo: IniciarChamadaRequisicao): DadosChamadaDto

    @POST("chamada/cancelar")
    suspend fun cancelarChamada(@Body corpo: IdDto): IdDto

    @POST("chamada/entrar")
    suspend fun entrarChamada(@Body corpo: IdDto): IdDto

    @POST("chamada/recusar")
    suspend fun recusarChamada(@Body corpo: RecusarChamadaRequisicao): IdDto

    @POST("chamada/sair")
    suspend fun sairChamada(@Body corpo: IdDto): IdDto

    @PUT("chamada/usuario")
    suspend fun adicionarUsuarioChamada(@Body corpo: AdicionarUsuarioChamadaRequisicao): IdDto

    @POST("chamada/finalizar")
    suspend fun finalizarChamada(@Body corpo: IdDto): IdDto

    @PUT("chamada/chat")
    suspend fun chatChamada(@Body corpo: IdDto): ChatChamadaDto

    @GET("chamada/dados")
    suspend fun dadosChamada(@Query("id") chamadaId: Long): DadosChamadaDto

    @GET("chamadas/pendentes")
    suspend fun chamadasPendentes(): List<ChamadaPendenteDto>

    /** `de` = data/hora (>=), `ate` = AAAA-MM-DD (inclusiva). */
    @GET("chamadas")
    suspend fun historicoChamadas(
        @Query("participante") participanteId: Long = 0,
        @Query("de") de: String = "",
        @Query("ate") ate: String = "",
    ): List<ChamadaHistoricoDto>

    @POST("chamada/video")
    suspend fun ativarVideo(@Body corpo: IdDto): JsonObject

    @GET("ice")
    suspend fun ice(): IceDto

    // --- Permissões, parâmetros, atividades ---

    @GET("usuario/permissoes")
    suspend fun minhasPermissoes(): List<String>

    @GET("permissoes")
    suspend fun permissoes(): PermissoesDto

    @PUT("permissao/usuario")
    suspend fun concederPermissao(@Body corpo: PermissaoUsuarioDto): PermissaoUsuarioDto

    @DELETE("permissao/usuario")
    suspend fun retirarPermissao(@Query("usuario_id") usuarioId: Long, @Query("codigo") codigo: String): PermissaoUsuarioDto

    @GET("parametros")
    suspend fun parametros(): ParametrosDto

    @PATCH("parametros")
    suspend fun alterarParametros(@Body corpo: AlterarParametrosRequisicao): ParametrosDto

    @GET("atividades")
    suspend fun atividades(@Query("antes") antes: Long = 0, @Query("limite") limite: Int = 30): List<AtividadeDto>

    @GET("atividades/novas")
    suspend fun atividadesNovas(): QuantidadeDto

    @HTTP(method = "POST", path = "atividades/vistas", hasBody = false)
    suspend fun marcarAtividadesVistas(): VistasDto

    // --- SIP ---

    @GET("sip")
    suspend fun sip(): SipDto

    @PUT("sip")
    suspend fun incluirSip(@Body corpo: SipDto): SipDto

    @PATCH("sip")
    suspend fun alterarSip(@Body corpo: SipDto): SipDto

    // --- Enquetes (votação em grupo) ---

    @PUT("enquete")
    suspend fun criarEnquete(@Body corpo: CriarEnqueteRequisicao): EnqueteCriadaDto

    @GET("enquete")
    suspend fun enquete(@Query("id") id: Long): EnqueteDto

    /** Substitui o voto do usuário pelas opções enviadas (lista vazia tira o voto). */
    @POST("enquete/votar")
    suspend fun votarEnquete(@Body corpo: VotarEnqueteRequisicao): EnqueteDto

    /** Encerrar antes do prazo (🆕 5cad911): quem criou a votação ou o grupo. */
    @POST("enquete/encerrar")
    suspend fun encerrarEnquete(@Body corpo: EncerrarEnqueteRequisicao): EnqueteDto

    /** Definir, adiar ou tirar a data final (🆕 5cad911): só quem criou a votação. */
    @PATCH("enquete")
    suspend fun alterarPrazoEnquete(@Body corpo: PrazoEnqueteRequisicao): EnqueteDto
}
