package com.conversa.conversa.data.socket

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gerenciador de WebSocket para notificações em tempo real
 * Responsável por:
 * - Conectar via WebSocket (protocolo ws://)
 * - Autenticar usando JWT
 * - Notificar eventos de chamada e mensagens
 * - Reconectar automaticamente em caso de falha
 * 
 * Protocolo WebSocket com frames padrão
 */
class SocketManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SocketManager"
        
        // Tipos de mensagem
        const val TYPE_ERRO = 0
        const val TYPE_LOGIN = 1
        const val TYPE_NOVA_MENSAGEM = 20
        const val TYPE_STATUS_MENSAGEM = 3
        const val TYPE_CHAMADA_RECEBIDA = 51
        const val TYPE_CHAMADA_FINALIZADA = 52
        const val TYPE_CHAMADA_USUARIO_RECUSOU = 53
        const val TYPE_CHAMADA_USUARIO_ENTROU = 54
        const val TYPE_CHAMADA_USUARIO_SAIU = 55
        
        // Configurações de reconexão
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }
    
    private var webSocket: WebSocket? = null
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Sem timeout de leitura (WebSocket mantém conexão)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(60, TimeUnit.SECONDS) // Ping a cada 60s (mais espaçado)
            .retryOnConnectionFailure(true) // Retry automático
            .build()
    }

    private var currentHost: String? = null
    private var currentPort: Int = 0
    private var currentToken: String? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Callbacks para eventos de chamada
    var onChamadaRecebida: ((chamadaId: Int, usuarioId: Int, usuarioNome: String) -> Unit)? = null
    var onChamadaFinalizada: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioRecusou: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioEntrou: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioSaiu: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    
    // Callbacks para eventos de mensagem
    var onNovaMensagem: ((conversaId: Int, remetenteId: Int, destinatarioId: Int, titulo: String, mensagem: String, tipo: Int) -> Unit)? = null
    var onStatusMensagemAtualizado: ((conversaId: Int, mensagensIds: List<Int>) -> Unit)? = null
    
    // Callbacks de conexão
    var onConectado: (() -> Unit)? = null
    var onDesconectado: (() -> Unit)? = null
    var onErro: ((erro: String) -> Unit)? = null
    
    /**
     * Conecta ao servidor WebSocket
     */
    fun conectar(host: String, port: Int, token: String) {
        currentHost = host
        currentPort = port
        currentToken = token
        
        scope.launch {
            try {
                Log.d(TAG, "Conectando ao WebSocket: ws://$host:$port")
                
                val url = "ws://$host:$port"
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
                
                Log.d(TAG, "WebSocket iniciado")
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar WebSocket", e)
                isConnected = false
                
                withContext(Dispatchers.Main) {
                    onErro?.invoke(e.message ?: "Erro ao conectar")
                }
                
                tentarReconectar()
            }
        }
    }
    
    /**
     * Cria o listener do WebSocket
     */
    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket conectado")
            isConnected = true
            reconnectAttempts = 0
            
            // Autentica após conexão
            autenticar(currentToken ?: "")
            
            scope.launch(Dispatchers.Main) {
                onConectado?.invoke()
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Mensagem recebida: $text")
            
            scope.launch(Dispatchers.Main) {
                processarMensagem(text)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket fechando: $code - $reason")
            webSocket.close(1000, null)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket fechado: $code - $reason")
            isConnected = false
            
            scope.launch(Dispatchers.Main) {
                onDesconectado?.invoke()
            }
            
            // Reconecta se não foi fechamento intencional
            if (code != 1000) {
                tentarReconectar()
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Erro no WebSocket", t)
            isConnected = false
            
            scope.launch(Dispatchers.Main) {
                onErro?.invoke(t.message ?: "Erro no WebSocket")
                onDesconectado?.invoke()
            }
            
            tentarReconectar()
        }
    }
    
    /**
     * Autentica no servidor
     */
    private fun autenticar(token: String) {
        scope.launch {
            delay(100) // Pequeno delay para garantir que conexão estabilizou
            
            val loginMsg = JSONObject().apply {
                put("tipo", TYPE_LOGIN)
                put("token", token)
            }
            
            val sucesso = enviarMensagem(loginMsg.toString())
            
            if (sucesso) {
                Log.d(TAG, "Autenticação enviada com sucesso")
            } else {
                Log.e(TAG, "ERRO: Falha ao enviar autenticação!")
                withContext(Dispatchers.Main) {
                    onErro?.invoke("Falha ao autenticar")
                }
            }
        }
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
                    val conversaId = obj.getInt("conversa_id")
                    val remetenteId = obj.getInt("remetente_id")
                    val destinatarioId = obj.getInt("destinatario_id")
                    val titulo = obj.getString("titulo")
                    val mensagem = obj.getString("mensagem")
                    val tipo = obj.getInt("tipo")
                    Log.d(TAG, "Nova mensagem recebida - Conversa: $conversaId, Remetente: $remetenteId")
                    onNovaMensagem?.invoke(conversaId, remetenteId, destinatarioId, titulo, mensagem, tipo)
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
                
                TYPE_CHAMADA_USUARIO_RECUSOU -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Usuário recusou chamada: $chamadaId")
                    onUsuarioRecusou?.invoke(chamadaId, usuarioId)
                }
                
                TYPE_CHAMADA_USUARIO_ENTROU -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Usuário entrou na chamada: $chamadaId")
                    onUsuarioEntrou?.invoke(chamadaId, usuarioId)
                }
                
                TYPE_CHAMADA_USUARIO_SAIU -> {
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
     * Envia mensagem via WebSocket
     */
    fun enviarMensagem(mensagem: String): Boolean {
        return try {
            if (!isConnected || webSocket == null) {
                Log.w(TAG, "WebSocket não conectado, não é possível enviar mensagem")
                return false
            }
            
            val sucesso = webSocket?.send(mensagem) ?: false
            
            if (sucesso) {
                Log.d(TAG, "Mensagem WebSocket enviada: ${mensagem.take(100)}")
            } else {
                Log.e(TAG, "ERRO: Falha ao enviar via WebSocket: ${mensagem.take(50)}")
            }
            
            sucesso
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar mensagem WebSocket", e)
            false
        }
    }
    
    /**
     * Tenta reconectar ao servidor
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
            if (scope.isActive && currentHost != null && currentPort > 0) {
                conectar(currentHost!!, currentPort, currentToken!!)
            }
        }
    }
    
    /**
     * Desconecta o WebSocket
     */
    fun desconectar() {
        Log.d(TAG, "Desconectando WebSocket")
        
        isConnected = false
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS // Impede reconexão
        
        webSocket?.close(1000, "Desconexão intencional")
        webSocket = null
        
        currentHost = null
        currentPort = 0
        currentToken = null
        
        onDesconectado?.invoke()
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
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }
}
