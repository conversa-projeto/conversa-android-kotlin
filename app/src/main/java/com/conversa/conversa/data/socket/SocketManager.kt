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
        
        // Tipos de mensagem (ver docs/websocket.md)
        const val TYPE_ERRO = 0
        const val TYPE_LOGIN = 1
        // ATENCAO: alinhado com backend Delphi (WebSocket.pas TSocketMessageType.NovaMensagem = 2)
        // e com cliente web (TipoEventoSocket.NovaMensagem = 2). Antes estava 20 (divergente).
        const val TYPE_NOVA_MENSAGEM = 2
        const val TYPE_STATUS_MENSAGEM = 3
        const val TYPE_DIGITANDO = 4
        const val TYPE_GRAVANDO_AUDIO = 5
        const val TYPE_REACAO_MENSAGEM = 7
        const val TYPE_CONVERSA_ATUALIZADA = 40
        const val TYPE_STATUS_USUARIO = 60
        const val TYPE_CHAMADA_RECEBIDA = 51
        const val TYPE_CHAMADA_FINALIZADA = 52
        const val TYPE_CHAMADA_USUARIO_RECUSOU = 53
        const val TYPE_CHAMADA_USUARIO_ENTROU = 54
        const val TYPE_CHAMADA_USUARIO_SAIU = 55
        const val TYPE_CHAMADA_VIDEO_ATIVADO = 56

        // Configurações de reconexão (infinita com backoff)
        private const val RECONNECT_DELAY_MS = 3000L       // 3 segundos inicial
        private const val MAX_RECONNECT_DELAY_MS = 30000L  // Máximo 30 segundos
    }
    
    private var webSocket: WebSocket? = null
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Sem timeout de leitura (WebSocket mantém conexão)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Sem pingInterval - evita desconexão por timeout de pong
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
    var onVideoAtivado: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null

    // Callbacks para eventos de mensagem
    var onNovaMensagem: ((conversaId: Int, remetenteId: Int, destinatarioId: Int, titulo: String, subtitulo: String, mensagem: String, tipoConversa: Int) -> Unit)? = null
    var onStatusMensagemAtualizado: ((conversaId: Int, mensagensIds: List<Int>) -> Unit)? = null
    var onDigitando: ((conversaId: Int, usuarioId: Int) -> Unit)? = null
    var onGravandoAudio: ((conversaId: Int, usuarioId: Int) -> Unit)? = null
    var onReacaoMensagem: ((conversaId: Int, mensagemId: Int, usuarioId: Int, emoji: String, acao: String) -> Unit)? = null
    var onConversaAtualizada: ((conversaId: Int) -> Unit)? = null
    var onStatusUsuario: ((usuarioId: Int, online: Boolean) -> Unit)? = null
    
    // Callbacks de conexão
    var onConectado: (() -> Unit)? = null
    var onDesconectado: (() -> Unit)? = null
    var onErro: ((erro: String) -> Unit)? = null

    // Multiplos consumidores podem reagir a onConectado sem sobrescrever o principal
    private val extrasOnConectado = mutableListOf<() -> Unit>()
    fun adicionarOnConectado(cb: () -> Unit) { extrasOnConectado += cb }
    fun removerOnConectado(cb: () -> Unit) { extrasOnConectado -= cb }
    internal fun dispararExtrasOnConectado() {
        extrasOnConectado.toList().forEach {
            try { it() } catch (e: Exception) { Log.w(TAG, "onConectado extra: ${e.message}") }
        }
    }
    
    /**
     * Conecta ao servidor WebSocket
     */
    fun conectar(host: String, port: Int, token: String) {
        currentHost = host
        currentPort = port
        currentToken = token
        shouldReconnect = true // Habilita reconexão automática

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
                dispararExtrasOnConectado()
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
                    // Novo payload (alinhado com backend): { tipo:2, mensagem: { id, conversa_id, usuario_id, inserida, conteudo[] } }
                    // Legacy payload (pre-existente no app): campos diretos titulo/mensagem/remetente_id.
                    // Tentamos primeiro o formato novo; fallback para legacy.
                    val msgObj = obj.optJSONObject("mensagem")
                    if (msgObj != null) {
                        val conversaId = msgObj.getInt("conversa_id")
                        val remetenteId = msgObj.optInt("usuario_id", msgObj.optInt("remetente_id", 0))
                        val titulo = obj.optString("titulo", "")
                        val subtitulo = obj.optString("subtitulo", titulo)
                        val texto = msgObj.optString("texto", "") // ou derivar de conteudo[0]
                        val tipoConversa = obj.optInt("tipo_conversa", 1)
                        val destId = obj.optInt("destinatario_id", 0)
                        Log.d(TAG, "NovaMensagem (novo payload) conv=$conversaId de=$remetenteId")
                        onNovaMensagem?.invoke(conversaId, remetenteId, destId, titulo, subtitulo, texto, tipoConversa)
                    } else if (obj.has("conversa_id")) {
                        val conversaId = obj.getInt("conversa_id")
                        val remetenteId = obj.getInt("remetente_id")
                        val destinatarioId = obj.getInt("destinatario_id")
                        val titulo = obj.optString("titulo", "")
                        val subtitulo = obj.optString("subtitulo", titulo)
                        val mensagem = obj.optString("mensagem", "")
                        val tipoConversa = obj.optInt("tipo_conversa", 1)
                        Log.d(TAG, "NovaMensagem (legacy payload) conv=$conversaId")
                        onNovaMensagem?.invoke(conversaId, remetenteId, destinatarioId, titulo, subtitulo, mensagem, tipoConversa)
                    } else {
                        Log.w(TAG, "NovaMensagem sem formato reconhecido: $json")
                    }
                }

                TYPE_STATUS_MENSAGEM -> {
                    // Payload oficial: { tipo:3, grupo: boolean, mensagens: [{conversa_id, mensagem_id, usuario_id, ...}, ...] }
                    // Tolera ambos formatos (grupo int legacy vs bool novo, mensagens CSV vs array).
                    val mensagensField = obj.opt("mensagens")
                    val (convId, ids) = when {
                        mensagensField is org.json.JSONArray -> {
                            val arr = mensagensField
                            val cid = if (arr.length() > 0) arr.getJSONObject(0).optInt("conversa_id", 0) else 0
                            val list = (0 until arr.length()).map { arr.getJSONObject(it).getInt("mensagem_id") }
                            cid to list
                        }
                        mensagensField is String -> {
                            // Legacy: grupo=int + mensagens CSV
                            val cid = obj.optInt("grupo", 0)
                            val list = mensagensField.split(",").mapNotNull { it.trim().toIntOrNull() }
                            cid to list
                        }
                        else -> 0 to emptyList()
                    }
                    if (ids.isNotEmpty() && convId > 0) {
                        onStatusMensagemAtualizado?.invoke(convId, ids)
                    }
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

                TYPE_CHAMADA_VIDEO_ATIVADO -> {
                    val chamadaId = obj.getInt("chamada_id")
                    val usuarioId = obj.getInt("usuario_id")
                    Log.d(TAG, "Video ativado na chamada: $chamadaId por $usuarioId")
                    onVideoAtivado?.invoke(chamadaId, usuarioId)
                }

                TYPE_DIGITANDO -> {
                    val conversaId = obj.getInt("conversa_id")
                    val usuarioId = obj.getInt("usuario_id")
                    onDigitando?.invoke(conversaId, usuarioId)
                }

                TYPE_GRAVANDO_AUDIO -> {
                    val conversaId = obj.getInt("conversa_id")
                    val usuarioId = obj.getInt("usuario_id")
                    onGravandoAudio?.invoke(conversaId, usuarioId)
                }

                TYPE_REACAO_MENSAGEM -> {
                    val conversaId = obj.getInt("conversa_id")
                    val mensagemId = obj.getInt("mensagem_id")
                    val usuarioId = obj.getInt("usuario_id")
                    val emoji = obj.optString("emoji", "")
                    val acao = obj.optString("acao", "add") // "add" ou "remove"
                    Log.d(TAG, "Reacao: $emoji $acao em msg $mensagemId")
                    onReacaoMensagem?.invoke(conversaId, mensagemId, usuarioId, emoji, acao)
                }

                TYPE_CONVERSA_ATUALIZADA -> {
                    val conversaId = obj.optInt("conversa_id", obj.optJSONObject("conversa")?.optInt("id", 0) ?: 0)
                    Log.d(TAG, "Conversa atualizada: $conversaId")
                    onConversaAtualizada?.invoke(conversaId)
                }

                TYPE_STATUS_USUARIO -> {
                    val usuarioId = obj.getInt("usuario_id")
                    val online = obj.optBoolean("online", false)
                    Log.d(TAG, "Status usuario $usuarioId: online=$online")
                    onStatusUsuario?.invoke(usuarioId, online)
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
     * Tenta reconectar ao servidor (reconexão infinita com backoff exponencial)
     */
    private fun tentarReconectar() {
        if (!shouldReconnect) {
            Log.d(TAG, "Reconexão desabilitada (desconexão intencional)")
            return
        }

        reconnectAttempts++

        // Backoff exponencial: 3s, 6s, 12s, 24s, 30s (máximo)
        val delay = minOf(
            RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempts - 1, 4)),
            MAX_RECONNECT_DELAY_MS
        )

        Log.d(TAG, "Tentando reconectar em ${delay}ms (tentativa $reconnectAttempts)")

        scope.launch {
            delay(delay)
            if (scope.isActive && shouldReconnect && currentHost != null && currentPort > 0 && currentToken != null) {
                conectar(currentHost!!, currentPort, currentToken!!)
            }
        }
    }

    /**
     * Reseta o contador de tentativas de reconexão
     */
    fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }
    
    // Flag para impedir reconexão quando desconexão é intencional
    private var shouldReconnect = true

    /**
     * Desconecta o WebSocket
     */
    fun desconectar() {
        Log.d(TAG, "Desconectando WebSocket")

        isConnected = false
        shouldReconnect = false // Impede reconexão

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
