package com.conversa.conversa.data.repository

import android.content.Context
import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.model.ChamadaIdRequest
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.data.model.EstadoChamadaLocal
import com.conversa.conversa.data.model.EventoChamada
import com.conversa.conversa.data.model.IniciarChamadaRequest
import com.conversa.conversa.data.model.UsuarioIdDto
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository para gerenciar chamadas de áudio
 */
class ChamadaRepository(
    private val context: Context,
    private val api: ConversaApi,
    private val chamadaManager: ChamadaManager,
    private val socketManager: SocketManager,
    private val userPreferences: UserPreferences
) {
    
    companion object {
        private const val TAG = "ChamadaRepository"
        const val TCP_PORT = 9090
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _chamadaAtualFlow = MutableStateFlow<ChamadaResponse?>(null)
    val chamadaAtualFlow: StateFlow<ChamadaResponse?> = _chamadaAtualFlow.asStateFlow()
    
    private val _estadoLocalFlow = MutableStateFlow(EstadoChamadaLocal.DESCONHECIDO)
    val estadoLocalFlow: StateFlow<EstadoChamadaLocal> = _estadoLocalFlow.asStateFlow()
    
    private val _eventosChamadaFlow = MutableSharedFlow<EventoChamada>(replay = 0)
    val eventosChamadaFlow: SharedFlow<EventoChamada> = _eventosChamadaFlow.asSharedFlow()
    
    private val _eventosUIFlow = MutableSharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI>(replay = 0)
    val eventosUIFlow: SharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI> = _eventosUIFlow.asSharedFlow()
    
    var chamadaAtual: ChamadaResponse? = null
        private set(value) {
            field = value
            _chamadaAtualFlow.value = value
        }
    
    var onChamadaIniciada: ((ChamadaResponse) -> Unit)? = null
    var onChamadaConectada: (() -> Unit)? = null
    var onChamadaRealmenteIniciada: (() -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    var onErro: ((String) -> Unit)? = null
    var onConexaoTcpEstabelecida: (() -> Unit)? = null
    
    private var primeiroParticipanteEntrou: Boolean = false

    init {
        Log.d(TAG, "Repository criado - Limpando listeners antigos")
        limparListenersAntigos()
        resetarEstado()
        setupSocketEventListeners()
        setupChamadaManagerCallbacks()
        observarEventosParaUI()
    }

    /**
     * Limpa listeners antigos do SocketManager para evitar duplicação
     */
    private fun limparListenersAntigos() {
        socketManager.onChamadaRecebida = null
        socketManager.onChamadaFinalizada = null
        socketManager.onUsuarioRecusou = null
        socketManager.onUsuarioEntrou = null
        socketManager.onUsuarioSaiu = null
        Log.d(TAG, "Listeners antigos do SocketManager removidos")
    }

    /**
     * Reseta todos os estados internos
     */
    private fun resetarEstado() {
        primeiroParticipanteEntrou = false
        chamadaAtual = null
        _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
        Log.d(TAG, "Estado interno resetado")
    }
    
    private fun observarEventosParaUI() {
        scope.launch {
            eventosChamadaFlow.collect { evento ->
                when (evento) {
                    is EventoChamada.UsuarioEntrou -> {
                        val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == evento.usuarioId }
                        _eventosUIFlow.emit(
                            com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                                tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.PARTICIPANTE_ENTROU,
                                chamadaId = evento.chamadaId,
                                participanteId = evento.usuarioId,
                                participanteNome = usuario?.usuarioNome
                            )
                        )
                    }
                    
                    is EventoChamada.UsuarioSaiu -> {
                        val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == evento.usuarioId }
                        _eventosUIFlow.emit(
                            com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                                tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.PARTICIPANTE_SAIU,
                                chamadaId = evento.chamadaId,
                                participanteId = evento.usuarioId,
                                participanteNome = usuario?.usuarioNome
                            )
                        )
                    }
                    
                    is EventoChamada.UsuarioRecusou -> {
                        val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == evento.usuarioId }
                        _eventosUIFlow.emit(
                            com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                                tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_RECUSADA,
                                chamadaId = evento.chamadaId,
                                participanteId = evento.usuarioId,
                                participanteNome = usuario?.usuarioNome
                            )
                        )
                    }
                    
                    is EventoChamada.Finalizada -> {
                        _eventosUIFlow.emit(
                            com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                                tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_FINALIZADA,
                                chamadaId = evento.chamadaId,
                                participanteId = evento.usuarioId
                            )
                        )
                    }
                    
                    is EventoChamada.Recebida -> {
                        // Evento de chamada recebida não precisa ser propagado para UI
                        // pois é tratado diretamente pela Activity de chamada recebida
                    }
                }
            }
        }
    }
    
    private fun setupChamadaManagerCallbacks() {
        Log.d(TAG, "Configurando callbacks do ChamadaManager")
        
        chamadaManager.onConexaoTcpEstabelecida = {
            Log.d(TAG, "ChamadaManager: Conexão TCP estabelecida")
            
            scope.launch(Dispatchers.Main) {
                onConexaoTcpEstabelecida?.invoke()
            }
        }
        
        chamadaManager.onConexaoEstabelecida = {
            Log.d(TAG, "ChamadaManager: Conexão TCP de áudio estabelecida")
            Log.d(TAG, ">>> EMITINDO EVENTO CHAMADA_CONECTADA <<<")
            _estadoLocalFlow.value = EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO
            
            scope.launch {
                _eventosUIFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_CONECTADA,
                        chamadaId = chamadaAtual?.id ?: 0
                    )
                )
                Log.d(TAG, ">>> EVENTO CHAMADA_CONECTADA EMITIDO <<<")
            }
            
            scope.launch(Dispatchers.Main) {
                onChamadaConectada?.invoke()
            }
        }
        
        chamadaManager.onConexaoFalhou = { erro ->
            Log.e(TAG, "ChamadaManager: Falha na conexão de áudio: $erro")
            
            scope.launch(Dispatchers.Main) {
                onErro?.invoke("Falha ao conectar áudio: $erro")
            }
        }
        
        chamadaManager.onChamadaFinalizada = {
            Log.d(TAG, "ChamadaManager: Chamada finalizada")
            _estadoLocalFlow.value = EstadoChamadaLocal.CHAMADA_FINALIZADA
            
            scope.launch {
                _eventosUIFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_FINALIZADA,
                        chamadaId = chamadaAtual?.id ?: 0
                    )
                )
            }
            
            scope.launch(Dispatchers.Main) {
                onChamadaFinalizada?.invoke()
            }
        }
    }
    
    private fun setupSocketEventListeners() {
        
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "Socket: Chamada recebida $chamadaId de $usuarioNome")
            
            scope.launch {
                try {
                    val resultado = obterDadosChamada(chamadaId)
                    
                    if (resultado.isSuccess) {
                        val chamada = resultado.getOrNull()!!
                        chamadaAtual = chamada
                        _estadoLocalFlow.value = EstadoChamadaLocal.RECEBENDO_CHAMADA
                        
                        _eventosChamadaFlow.emit(
                            EventoChamada.Recebida(chamadaId, usuarioId, usuarioNome)
                        )
                        
                        Log.d(TAG, "Dados da chamada recebida carregados: ${chamada.usuarios.size} participantes")
                    } else {
                        Log.e(TAG, "Erro ao buscar dados da chamada recebida", resultado.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exceção ao processar chamada recebida", e)
                }
            }
        }
        
        socketManager.onChamadaFinalizada = { chamadaId, usuarioId ->
            Log.d(TAG, "Socket: Chamada finalizada $chamadaId")
            
            scope.launch {
                _estadoLocalFlow.value = EstadoChamadaLocal.CHAMADA_FINALIZADA
                
                if (chamadaAtual?.id == chamadaId) {
                    chamadaAtual = null
                }
                
                _eventosChamadaFlow.emit(
                    EventoChamada.Finalizada(chamadaId, usuarioId)
                )
                
                chamadaManager.finalizarChamada()
                
                withContext(Dispatchers.Main) {
                    onChamadaFinalizada?.invoke()
                }
            }
        }
        
        socketManager.onUsuarioRecusou = { chamadaId, usuarioId ->
            Log.d(TAG, "Socket: Usuário $usuarioId recusou chamada $chamadaId")
            
            scope.launch {
                if (chamadaAtual?.id == chamadaId) {
                    val resultado = obterDadosChamada(chamadaId)
                    if (resultado.isSuccess) {
                        chamadaAtual = resultado.getOrNull()
                    }
                }
                
                _eventosChamadaFlow.emit(
                    EventoChamada.UsuarioRecusou(chamadaId, usuarioId)
                )
            }
        }
        
        socketManager.onUsuarioEntrou = { chamadaId, usuarioId ->
            Log.d(TAG, "Socket: Usuário $usuarioId entrou na chamada $chamadaId")
            
            scope.launch {
                val usuarioAtualId = userPreferences.userId.firstOrNull() ?: 0
                
                // Sempre atualiza dados da chamada
                if (chamadaAtual?.id == chamadaId || chamadaAtual == null) {
                    val resultado = obterDadosChamada(chamadaId)
                    if (resultado.isSuccess) {
                        chamadaAtual = resultado.getOrNull()
                        Log.d(TAG, "Dados da chamada atualizados: ${chamadaAtual?.usuarios?.size} participantes")
                    }
                }
                
                // Verifica se é o primeiro participante diferente do usuário atual
                if (!primeiroParticipanteEntrou && usuarioId != usuarioAtualId) {
                    primeiroParticipanteEntrou = true
                    Log.d(TAG, "⚡ PRIMEIRO participante diferente entrou: $usuarioId (eu sou: $usuarioAtualId)")
                    Log.d(TAG, "🎙️ Iniciando captura e reprodução - Quem ESTAVA ESPERANDO")
                    
                    val chamadaIdAtual = chamadaAtual?.id
                    
                    if (chamadaIdAtual != null) {
                        _eventosUIFlow.emit(
                            com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                                tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_REALMENTE_INICIADA,
                                chamadaId = chamadaIdAtual
                            )
                        )
                        
                        withContext(Dispatchers.Main) {
                            onChamadaRealmenteIniciada?.invoke()
                        }
                        
                        chamadaManager.iniciarCapturaEReproducao()
                        Log.d(TAG, "✅ Captura e reprodução iniciadas com sucesso")
                    } else {
                        Log.e(TAG, "❌ ERRO: chamadaAtual ainda é null ao detectar primeiro participante")
                    }
                } else {
                    if (usuarioId == usuarioAtualId) {
                        Log.d(TAG, "⏭️ Evento ignorado: sou eu mesmo entrando ($usuarioId)")
                    } else if (primeiroParticipanteEntrou) {
                        Log.d(TAG, "⏭️ Evento ignorado: áudio já foi iniciado anteriormente")
                    }
                }
                
                _eventosChamadaFlow.emit(
                    EventoChamada.UsuarioEntrou(chamadaId, usuarioId)
                )
            }
        }
        
        socketManager.onUsuarioSaiu = { chamadaId, usuarioId ->
            Log.d(TAG, "Socket: Usuário $usuarioId saiu da chamada $chamadaId")
            
            scope.launch {
                if (chamadaAtual?.id == chamadaId) {
                    val resultado = obterDadosChamada(chamadaId)
                    if (resultado.isSuccess) {
                        chamadaAtual = resultado.getOrNull()
                    }
                }
                
                _eventosChamadaFlow.emit(
                    EventoChamada.UsuarioSaiu(chamadaId, usuarioId)
                )
            }
        }
    }
    
    suspend fun iniciarChamada(destinatariosIds: List<Int>): Result<ChamadaResponse> {
        return try {
            primeiroParticipanteEntrou = false
            _estadoLocalFlow.value = EstadoChamadaLocal.INICIANDO_CHAMADA
            
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val usuarioId = userPreferences.userId.first() ?: 0
            val apiUrl = userPreferences.apiUrl.first() ?: ""
            val tcpHost = extrairHost(apiUrl)
            
            Log.d(TAG, "=== INICIANDO CHAMADA ===")
            Log.d(TAG, "UsuarioId: $usuarioId")
            Log.d(TAG, "Destinatarios: $destinatariosIds")
            
            val todosUsuarios = destinatariosIds.toMutableList()
            if (usuarioId !in todosUsuarios) {
                todosUsuarios.add(0, usuarioId)
            }
            
            val tipo = if (todosUsuarios.size == 2) 1 else 2
            val request = IniciarChamadaRequest(
                tipo = tipo,
                usuarios = todosUsuarios.map { UsuarioIdDto(it) }
            )
            
            val response = api.iniciarChamada("Bearer $token", request)
            
            if (response.isSuccessful) {
                val chamada = response.body()!!
                
                Log.d(TAG, "✅ Chamada criada via API: ${chamada.id}")
                Log.d(TAG, "Atribuindo chamadaAtual ANTES de conectar TCP")
                
                // CRÍTICO: Atribui ANTES de conectar ao TCP
                chamadaAtual = chamada
                
                // Delay para garantir que servidor processou
                delay(200)
                
                val sucesso = chamadaManager.iniciarChamada(
                    serverHost = tcpHost,
                    serverPort = TCP_PORT,
                    chamadaId = chamada.id,
                    usuarioId = usuarioId
                )
                
                if (sucesso) {
                    Log.d(TAG, "✅ Conectado ao TCP - Quem INICIA")
                    
                    withContext(Dispatchers.Main) {
                        onChamadaIniciada?.invoke(chamada)
                    }
                    
                    Result.success(chamada)
                } else {
                    Log.e(TAG, "❌ Falha ao conectar TCP")
                    _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
                    Result.failure(Exception("Falha ao conectar áudio"))
                }
            } else {
                Log.e(TAG, "❌ Erro API ao iniciar: ${response.code()}")
                _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
                Result.failure(Exception("Erro ao iniciar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao iniciar chamada", e)
            _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
            Result.failure(e)
        }
    }
    
    suspend fun aceitarChamada(chamadaId: Int): Result<Unit> {
        return try {
            primeiroParticipanteEntrou = false
            
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val usuarioId = userPreferences.userId.firstOrNull() ?: 0
            val apiUrl = userPreferences.apiUrl.firstOrNull() ?: ""
            val tcpHost = extrairHost(apiUrl)
            
            Log.d(TAG, "=== ACEITANDO CHAMADA ===")
            Log.d(TAG, "ChamadaId: $chamadaId")
            Log.d(TAG, "UsuarioId: $usuarioId")
            
            // 1. Busca dados completos PRIMEIRO
            Log.d(TAG, "Buscando dados da chamada $chamadaId...")
            val resultado = obterDadosChamada(chamadaId)
            if (resultado.isFailure) {
                Log.e(TAG, "Erro ao buscar dados da chamada")
                return Result.failure(Exception("Erro ao buscar dados da chamada"))
            }
            
            // CRÍTICO: Atribui ANTES de qualquer operação
            chamadaAtual = resultado.getOrNull()
            Log.d(TAG, "✅ chamadaAtual atribuído: id=${chamadaAtual?.id}, ${chamadaAtual?.usuarios?.size} participantes")
            
            // 2. Notifica API que está entrando
            val response = api.entrarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ API notificada: entrou na chamada $chamadaId")
                
                // 3. Delay reduzido para garantir que servidor processou
                Log.d(TAG, "Aguardando servidor processar entrada...")
                delay(500)
                
                // 4. Conecta ao TCP (chamadaAtual já está definido)
                Log.d(TAG, "Conectando ao TCP com chamadaAtual.id=${chamadaAtual?.id}...")
                val sucesso = chamadaManager.iniciarChamada(
                    serverHost = tcpHost,
                    serverPort = TCP_PORT,
                    chamadaId = chamadaId,
                    usuarioId = usuarioId
                )
                
                if (sucesso) {
                    Log.d(TAG, "✅ Conectado ao TCP - Quem ACEITA")
                    
                    // CRÍTICO: Verifica se já tem outros participantes conectados
                    val outrosParticipantes = chamadaAtual?.usuarios?.filter { 
                        it.usuarioId != usuarioId 
                    } ?: emptyList()
                    
                    Log.d(TAG, "Verificando participantes: total=${chamadaAtual?.usuarios?.size}, outros=${outrosParticipantes.size}")
                    
                    if (outrosParticipantes.isNotEmpty()) {
                        Log.d(TAG, "⚡ Já existem ${outrosParticipantes.size} participante(s) conectado(s)")
                        outrosParticipantes.forEach { p ->
                            Log.d(TAG, "   - Participante: ${p.usuarioNome} (id=${p.usuarioId})")
                        }
                        Log.d(TAG, "🎙️ Iniciando captura e reprodução IMEDIATAMENTE - Quem ACEITOU")
                        primeiroParticipanteEntrou = true
                        chamadaManager.iniciarCapturaEReproducao()
                        
                        withContext(Dispatchers.Main) {
                            onChamadaRealmenteIniciada?.invoke()
                        }
                        
                        Log.d(TAG, "✅ Captura e reprodução iniciadas com sucesso")
                    } else {
                        Log.d(TAG, "⏳ Nenhum outro participante conectado ainda, aguardando entrada...")
                    }
                    
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "❌ Falha ao conectar TCP")
                    Result.failure(Exception("Falha ao conectar áudio"))
                }
            } else {
                Log.e(TAG, "❌ Erro API ao aceitar: ${response.code()}")
                Result.failure(Exception("Erro ao aceitar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao aceitar chamada", e)
            Result.failure(e)
        }
    }
    
    suspend fun recusarChamada(chamadaId: Int): Result<Unit> {
        return try {
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            Log.d(TAG, "Recusando chamada $chamadaId...")
            
            val response = api.recusarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
            
            if (response.isSuccessful) {
                Log.d(TAG, "Chamada recusada: $chamadaId")
                _estadoLocalFlow.value = EstadoChamadaLocal.RECUSADA
                
                if (chamadaAtual?.id == chamadaId) {
                    chamadaAtual = null
                }
                
                Result.success(Unit)
            } else {
                Log.e(TAG, "Erro ao recusar chamada: ${response.code()}")
                Result.failure(Exception("Erro ao recusar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao recusar chamada", e)
            Result.failure(e)
        }
    }
    
    suspend fun finalizarChamada(): Result<Unit> {
        return try {
            val chamada = chamadaAtual
            
            Log.d(TAG, "Finalizando chamada: ${chamada?.id ?: "nenhuma"}")
            
            chamadaManager.finalizarChamada()
            
            if (chamada != null) {
                val token = userPreferences.authToken.first()
                if (token != null) {
                    try {
                        // Sempre chama sairChamada - o servidor decide se finaliza:
                        // - Chamada Simples (2 participantes): servidor finaliza automaticamente
                        // - Chamada em Grupo: servidor só finaliza quando último participante sair
                        api.sairChamada("Bearer $token", ChamadaIdRequest(chamada.id))
                        Log.d(TAG, "API notificada sobre saída da chamada")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao notificar API, mas áudio já foi finalizado", e)
                    }
                }
            }
            
            primeiroParticipanteEntrou = false
            chamadaAtual = null
            _estadoLocalFlow.value = EstadoChamadaLocal.CHAMADA_FINALIZADA
            
            Log.d(TAG, "Chamada finalizada completamente")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao finalizar chamada", e)
            Result.failure(e)
        }
    }
    
    suspend fun obterDadosChamada(chamadaId: Int): Result<ChamadaResponse> {
        return try {
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val response = api.obterDadosChamada("Bearer $token", chamadaId)
            
            if (response.isSuccessful) {
                val chamada = response.body()!!
                Result.success(chamada)
            } else {
                Result.failure(Exception("Erro ao obter dados da chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter dados da chamada", e)
            Result.failure(e)
        }
    }
    
    private fun extrairHost(url: String): String {
        return try {
            val semProtocolo = url.replace("http://", "").replace("https://", "")
            semProtocolo.split(":").first().split("/").first()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair host", e)
            "localhost"
        }
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleanup do repository")
        
        // Apenas finaliza o ChamadaManager (áudio local)
        chamadaManager.cleanup()
        
        // Limpa estados locais
        chamadaAtual = null
        _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
        
        // Cancela escopo local
        scope.cancel()
        
        // NÃO toca no SocketManager - ele pertence ao serviço!
        Log.d(TAG, "Repository limpo (SocketManager preservado)")
    }
    
    fun pausarCaptura() {
        chamadaManager.pausarCaptura()
    }

    fun retormarCaptura() {
        chamadaManager.retormarCaptura()
    }

    fun atualizarParticipantesMutados(mutados: Set<Int>) {
        chamadaManager.atualizarParticipantesMutados(mutados)
    }

    /**
     * Muta/desmuta o microfone
     */
    fun toggleMuteMicrofone(muted: Boolean) {
        chamadaManager.toggleMuteMicrofone(muted)
    }

    /**
     * Muta/desmuta o áudio (reprodução)
     */
    fun toggleMuteAudio(muted: Boolean) {
        chamadaManager.toggleMuteAudio(muted)
    }

    /**
     * Alterna entre earpiece e speakerphone
     */
    fun toggleSpeaker(speakerOn: Boolean) {
        chamadaManager.toggleSpeaker(speakerOn)
    }
}
