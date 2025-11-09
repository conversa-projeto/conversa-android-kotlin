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
    
    var chamadaAtual: ChamadaResponse? = null
        private set(value) {
            field = value
            _chamadaAtualFlow.value = value
        }
    
    var onChamadaIniciada: ((ChamadaResponse) -> Unit)? = null
    var onChamadaConectada: (() -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    var onErro: ((String) -> Unit)? = null
    
    init {
        setupSocketEventListeners()
    }
    
    private fun setupChamadaManagerCallbacks() {
        Log.d(TAG, "Configurando callbacks do ChamadaManager")
        
        chamadaManager.onConexaoEstabelecida = {
            Log.d(TAG, "ChamadaManager: Conexão de áudio estabelecida")
            _estadoLocalFlow.value = EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO
            
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
                if (chamadaAtual?.id == chamadaId) {
                    val resultado = obterDadosChamada(chamadaId)
                    if (resultado.isSuccess) {
                        chamadaAtual = resultado.getOrNull()
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
            _estadoLocalFlow.value = EstadoChamadaLocal.INICIANDO_CHAMADA
            
            setupChamadaManagerCallbacks()
            
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
                chamadaAtual = chamada
                
                Log.d(TAG, "✅ Chamada criada via API: ${chamada.id}")
                
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
            setupChamadaManagerCallbacks()
            
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
            
            // 1. Notifica API que está entrando
            val response = api.entrarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ API notificada: entrou na chamada $chamadaId")
                
                // 2. Busca dados completos
                val resultado = obterDadosChamada(chamadaId)
                if (resultado.isSuccess) {
                    chamadaAtual = resultado.getOrNull()
                    Log.d(TAG, "Dados da chamada carregados: ${chamadaAtual?.usuarios?.size} participantes")
                }
                
                // 3. Delay para garantir que servidor processou
                Log.d(TAG, "Aguardando servidor processar entrada...")
                delay(3000)
                
                // 4. Conecta ao TCP
                Log.d(TAG, "Conectando ao TCP...")
                val sucesso = chamadaManager.iniciarChamada(
                    serverHost = tcpHost,
                    serverPort = TCP_PORT,
                    chamadaId = chamadaId,
                    usuarioId = usuarioId
                )
                
                if (sucesso) {
                    Log.d(TAG, "✅ Conectado ao TCP - Quem ACEITA")
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
                        api.finalizarChamada("Bearer $token", ChamadaIdRequest(chamada.id))
                        Log.d(TAG, "API notificada sobre finalização")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao notificar API, mas áudio já foi finalizado", e)
                    }
                }
            }
            
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
        chamadaManager.cleanup()
        chamadaAtual = null
        _estadoLocalFlow.value = EstadoChamadaLocal.DESCONHECIDO
        scope.cancel()
    }
}
