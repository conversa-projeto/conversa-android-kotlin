package com.conversa.conversa.data.websocket

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gerenciador de WebSocket para sinalização em tempo real
 * Responsável por:
 * - Conectar e manter conexão WebSocket
 * - Autenticar usando JWT
 * - Notificar eventos de chamada
 * - Reconectar automaticamente em caso de falha
 */
class WebSocketManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WebSocketManager"
        
        // Tipos de mensagem WebSocket
        const val TYPE_ERRO = 0
        const val TYPE_LOGIN = 1
        const val TYPE_NOVA_MENSAGEM = 2
        const val TYPE_STATUS_MENSAGEM = 3
        const val TYPE_CHAMADA_RECEBIDA = 51
        const val TYPE_CHAMADA_FINALIZADA = 52
        const val TYPE_USUARIO_RECUSOU = 53
        const val TYPE_USUARIO_ENTROU = 54
        const val TYPE_USUARIO_SAIU = 55
        
        // Configurações de reconexão
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sem timeout para WebSocket
        .build()
    
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    
    // Scope para gerenciar corrotinas
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Callbacks para eventos de chamada
    var onChamadaRecebida: ((chamadaId: Int, usuarioId: Int, usuarioNome: String) -> Unit)? = null
    var onChamadaFinalizada: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioRecusou: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioEntrou: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioSaiu: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    
    // Callbacks para eventos de mensagem
    var onNovaMensagem: ((titulo: String, mensagem: String) -> Unit)? = null
    var onStatusMensagemAtualizado: ((conversaId: Int, mensagensIds: List<Int>) -> Unit)? = null
    
    // Callbacks de conexão
    var onConectado: (() -> Unit)? = null
    var onDesconectado: (() -> Unit)? = null
    var onErro: ((erro: String) -> Unit)? = null
    
    /**
     * Conecta ao WebSocket e autentica com JWT
     */
    fun conectar(wsUrl: String, token: String) {
        currentUrl = wsUrl
        currentToken = token
        
        Log.d(TAG, "Conectando ao WebSocket: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket?.close(1000, "Nova conexão")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket conectado")
                isConnected = true
                reconnectAttempts = 0
                
                // Autentica com JWT
                autenticar(token)
                
                onConectado?.invoke()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensagem recebida: $text")
                processarMensagem(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Erro no WebSocket", t)
                isConnected = false
                onDesconectado?.invoke()
                onErro?.invoke(t.message ?: "Erro desconhecido")
                
                // Tentar reconectar
                tentarReconectar()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket fechado: $code - $reason")
                isConnected = false
                onDesconectado?.invoke()
            }
        })
    }
    
    /**
     * Envia mensagem de autenticação
     */
    private fun autenticar(token: String) {
        val loginMsg = JSONObject().apply {
            put("tipo", TYPE_LOGIN)
            put("token", token)
        }
        
        enviarMensagem(loginMsg.toString())
    }
    
    /**
     * Processa mensagem recebida do servidor
     */
    private fun processarMensagem(json: String) {
        try {
            val obj = JSONObject(json)
            val tipo = obj.getInt("tipo")
            
            when (tipo) {
                TYPE_ERRO -> {
                    val mensagem = obj.optString("message", "Erro desconhecido")
                    Log.e(TAG, "Erro do servidor: $mensagem")
                    onErro?.invoke(mensagem)
                }
                
                TYPE_NOVA_MENSAGEM -> {
                    val titulo = obj.getString("titulo")
                    val mensagem = obj.getString("mensagem")
                    onNovaMensagem?.invoke(titulo, mensagem)
                }
                
                TYPE_STATUS_MENSAGEM -> {
                    val conversaId = obj.getInt("grupo")
                    val mensagensStr = obj.getString("mensagens")
                    val mensagensIds = mensagensStr.split(",").map { it.toInt() }
                    onStatusMensagemAtualizado?.invoke(conversaId, mensagensIds)
                }
                
                TYPE_CHAMADA_RECEBIDA -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    val usuarioNome = obj.optString("usuario_nome", "Desconhecido")
                    Log.d(TAG, "Chamada recebida: $chamadaId de $usuarioNome")
                    onChamadaRecebida?.invoke(chamadaId, usuarioId, usuarioNome)
                }
                
                TYPE_CHAMADA_FINALIZADA -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Chamada finalizada: $chamadaId")
                    onChamadaFinalizada?.invoke(chamadaId, usuarioId)
                }
                
                TYPE_USUARIO_RECUSOU -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Usuário recusou chamada: $chamadaId")
                    onUsuarioRecusou?.invoke(chamadaId, usuarioId)
                }
                
                TYPE_USUARIO_ENTROU -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Usuário entrou na chamada: $chamadaId")
                    onUsuarioEntrou?.invoke(chamadaId, usuarioId)
                }
                
                TYPE_USUARIO_SAIU -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Usuário saiu da chamada: $chamadaId")
                    onUsuarioSaiu?.invoke(chamadaId, usuarioId)
                }
                
                else -> {
                    Log.w(TAG, "Tipo de mensagem desconhecido: $tipo")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar mensagem", e)
            onErro?.invoke("Erro ao processar mensagem: ${e.message}")
        }
    }
    
    /**
     * Envia mensagem para o servidor
     */
    fun enviarMensagem(mensagem: String): Boolean {
        return webSocket?.send(mensagem) ?: false
    }
    
    /**
     * Tenta reconectar ao WebSocket
     */
    private fun tentarReconectar() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Máximo de tentativas de reconexão atingido")
            onErro?.invoke("Não foi possível reconectar ao servidor")
            return
        }
        
        reconnectAttempts++
        Log.d(TAG, "Tentando reconectar ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (scope.isActive && currentUrl != null && currentToken != null) {
                conectar(currentUrl!!, currentToken!!)
            }
        }
    }
    
    /**
     * Desconecta o WebSocket
     */
    fun desconectar() {
        Log.d(TAG, "Desconectando WebSocket")
        webSocket?.close(1000, "Desconexão normal")
        webSocket = null
        isConnected = false
        currentUrl = null
        currentToken = null
        reconnectAttempts = 0
    }
    
    /**
     * Verifica se está conectado
     */
    fun isConectado(): Boolean = isConnected
    
    /**
     * Limpa recursos
     */
    fun cleanup() {
        desconectar()
        scope.cancel()
    }
}
