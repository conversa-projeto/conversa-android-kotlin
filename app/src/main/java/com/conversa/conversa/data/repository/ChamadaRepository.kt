package com.conversa.conversa.data.repository

import android.content.Context
import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.model.ChamadaIdRequest
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.data.model.IniciarChamadaRequest
import com.conversa.conversa.data.model.UsuarioIdDto
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.socket.SocketManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Response

/**
 * Repository para gerenciar chamadas de áudio
 * Integra API REST, WebSocket e gerenciador de áudio
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
    
    // Estado atual da chamada
    var chamadaAtual: ChamadaResponse? = null
        private set
    
    // Callbacks
    var onChamadaIniciada: ((ChamadaResponse) -> Unit)? = null
    var onChamadaConectada: (() -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    var onErro: ((String) -> Unit)? = null
    
    init {
        setupWebSocketCallbacks()
        setupChamadaManagerCallbacks()
    }
    
    /**
     * Configura callbacks do Socket TCP
     */
    private fun setupWebSocketCallbacks() {
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "Chamada recebida: $chamadaId de $usuarioNome")
            // O callback será tratado pela Activity
        }
        
        socketManager.onChamadaFinalizada = { chamadaId, usuarioId ->
            Log.d(TAG, "Chamada finalizada: $chamadaId")
            if (chamadaAtual?.id == chamadaId) {
                finalizarChamadaLocal()
            }
        }
        
        socketManager.onUsuarioEntrou = { chamadaId, usuarioId ->
            Log.d(TAG, "Usuário entrou na chamada: $chamadaId")
            // O callback será tratado pela Activity
        }
        
        socketManager.onUsuarioSaiu = { chamadaId, usuarioId ->
            Log.d(TAG, "Usuário saiu da chamada: $chamadaId")
            // O callback será tratado pela Activity
        }
        
        socketManager.onUsuarioRecusou = { chamadaId, usuarioId ->
            Log.d(TAG, "Usuário recusou a chamada: $chamadaId")
            // O callback será tratado pela Activity
        }
    }
    
    /**
     * Configura callbacks do gerenciador de chamadas
     */
    private fun setupChamadaManagerCallbacks() {
        chamadaManager.onConexaoEstabelecida = {
            Log.d(TAG, "Conexão de áudio estabelecida")
            onChamadaConectada?.invoke()
        }
        
        chamadaManager.onConexaoFalhou = { erro ->
            Log.e(TAG, "Falha na conexão de áudio: $erro")
            onErro?.invoke("Falha ao conectar áudio: $erro")
        }
        
        chamadaManager.onChamadaFinalizada = {
            Log.d(TAG, "Chamada finalizada pelo gerenciador")
            onChamadaFinalizada?.invoke()
        }
    }
    
    /**
     * Inicia uma nova chamada
     */
    suspend fun iniciarChamada(destinatariosIds: List<Int>): Result<ChamadaResponse> {
        return try {
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val usuarioId = userPreferences.userId.first() ?: 0
            val apiUrl = userPreferences.apiUrl.first() ?: ""
            val tcpHost = extrairHost(apiUrl)
            
            // Adiciona o próprio usuário na lista
            val todosUsuarios = destinatariosIds.toMutableList()
            if (usuarioId !in todosUsuarios) {
                todosUsuarios.add(0, usuarioId)
            }
            
            // Cria request
            val tipo = if (todosUsuarios.size == 2) 1 else 2 // 1=Simples, 2=Grupo
            val request = IniciarChamadaRequest(
                tipo = tipo,
                usuarios = todosUsuarios.map { UsuarioIdDto(it) }
            )
            
            // Chama API
            val response = api.iniciarChamada("Bearer $token", request)
            
            if (response.isSuccessful) {
                val chamada = response.body()!!
                chamadaAtual = chamada
                
                Log.d(TAG, "Chamada iniciada: ${chamada.id}")
                
                // Conecta ao servidor TCP para áudio
                val sucesso = chamadaManager.iniciarChamada(
                    serverHost = tcpHost,
                    serverPort = TCP_PORT,
                    chamadaId = chamada.id,
                    usuarioId = usuarioId
                )
                
                if (sucesso) {
                    onChamadaIniciada?.invoke(chamada)
                    Result.success(chamada)
                } else {
                    Result.failure(Exception("Falha ao conectar áudio"))
                }
            } else {
                Result.failure(Exception("Erro ao iniciar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar chamada", e)
            Result.failure(e)
        }
    }
    
    /**
     * Aceita e entra em uma chamada recebida
     */
    suspend fun aceitarChamada(chamadaId: Int): Result<Unit> {
        return try {
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val usuarioId = userPreferences.userId.firstOrNull() ?: 0
            val apiUrl = userPreferences.apiUrl.firstOrNull() ?: ""
            val tcpHost = extrairHost(apiUrl)
            
            // Informa ao servidor que está entrando na chamada
            val response = api.entrarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
            
            if (response.isSuccessful) {
                Log.d(TAG, "Chamada aceita: $chamadaId")
                
                // Conecta ao servidor TCP
                val sucesso = chamadaManager.iniciarChamada(
                    serverHost = tcpHost,
                    serverPort = TCP_PORT,
                    chamadaId = chamadaId,
                    usuarioId = usuarioId
                )
                
                if (sucesso) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Falha ao conectar áudio"))
                }
            } else {
                Result.failure(Exception("Erro ao aceitar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aceitar chamada", e)
            Result.failure(e)
        }
    }
    
    /**
     * Recusa uma chamada recebida
     */
    suspend fun recusarChamada(chamadaId: Int): Result<Unit> {
        return try {
            val token = userPreferences.authToken.first()
            if (token == null) {
                return Result.failure(Exception("Token não disponível"))
            }
            
            val response = api.recusarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
            
            if (response.isSuccessful) {
                Log.d(TAG, "Chamada recusada: $chamadaId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erro ao recusar chamada: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao recusar chamada", e)
            Result.failure(e)
        }
    }
    
    /**
     * Finaliza a chamada atual
     */
    suspend fun finalizarChamada(): Result<Unit> {
        return try {
            val chamada = chamadaAtual ?: return Result.success(Unit)
            val token = userPreferences.authToken.first()
            
            // Finaliza áudio localmente
            chamadaManager.finalizarChamada()
            
            // Notifica o servidor
            if (token != null) {
                api.finalizarChamada("Bearer $token", ChamadaIdRequest(chamada.id))
            }
            
            finalizarChamadaLocal()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao finalizar chamada", e)
            Result.failure(e)
        }
    }
    
    /**
     * Finaliza a chamada localmente
     */
    private fun finalizarChamadaLocal() {
        chamadaManager.finalizarChamada()
        chamadaAtual = null
        onChamadaFinalizada?.invoke()
    }
    
    /**
     * Obtém dados da chamada do servidor
     */
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
    
    /**
     * Extrai o host de uma URL
     */
    private fun extrairHost(url: String): String {
        return try {
            // Remove protocolo
            val semProtocolo = url.replace("http://", "").replace("https://", "")
            // Remove porta e path
            semProtocolo.split(":").first().split("/").first()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair host", e)
            "localhost"
        }
    }
    
    /**
     * Limpa recursos
     */
    fun cleanup() {
        chamadaManager.cleanup()
        chamadaAtual = null
    }
}
