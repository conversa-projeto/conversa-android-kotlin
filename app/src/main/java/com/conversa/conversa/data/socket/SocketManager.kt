package com.conversa.conversa.data.socket

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gerenciador de Socket TCP para notificações em tempo real
 * Responsável por:
 * - Conectar e manter conexão TCP na porta 8090
 * - Autenticar usando JWT
 * - Notificar eventos de chamada e mensagens
 * - Reconectar automaticamente em caso de falha
 * 
 * Protocolo: [tamanho (4 bytes LITTLE_ENDIAN)][dados JSON (UTF-8)]
 */
class SocketManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SocketManager"
        
        // Tipos de mensagem
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
        private const val SOCKET_TIMEOUT_MS = 30000 // 30 segundos
    }
    
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    private var currentHost: String? = null
    private var currentPort: Int = 0
    private var currentToken: String? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiveJob: Job? = null
    
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
     * Conecta ao servidor TCP
     */
    fun conectar(host: String, port: Int) {
        currentHost = host
        currentPort = port
        
        scope.launch {
            try {
                Log.d(TAG, "Conectando ao servidor: $host:$port")
                
                // Conecta ao servidor TCP
                socket = Socket(host, port).apply {
                    tcpNoDelay = true
                    soTimeout = SOCKET_TIMEOUT_MS
                    keepAlive = true
                }
                
                outputStream = socket!!.getOutputStream()
                inputStream = socket!!.getInputStream()
                
                isConnected = true
                reconnectAttempts = 0
                
                Log.d(TAG, "Socket TCP conectado")
                
                // Inicia recepção de mensagens
                iniciarRecepcao()
                
                withContext(Dispatchers.Main) {
                    onConectado?.invoke()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar", e)
                isConnected = false
                
                withContext(Dispatchers.Main) {
                    onErro?.invoke(e.message ?: "Erro ao conectar")
                }
                
                tentarReconectar()
            }
        }
    }
    
    /**
     * Inicia recepção de mensagens do servidor
     */
    private fun iniciarRecepcao() {
        receiveJob = scope.launch {
            try {
                Log.d(TAG, "Recepção de mensagens iniciada")
                
                while (isConnected && isActive) {
                    receberMensagem()
                }
            } catch (e: EOFException) {
                Log.e(TAG, "Conexão encerrada pelo servidor")
                desconectarInterno()
            } catch (e: Exception) {
                Log.e(TAG, "Erro na recepção de mensagens", e)
                desconectarInterno()
            } finally {
                Log.d(TAG, "Recepção de mensagens finalizada")
            }
        }
    }
    
    /**
     * Recebe e processa uma mensagem do servidor
     * Protocolo: [tamanho (4 bytes LITTLE_ENDIAN)][dados JSON]
     */
    private suspend fun receberMensagem() {
        try {
            // Lê tamanho da mensagem (4 bytes, LITTLE_ENDIAN)
            val tamanhoBytes = ByteArray(4)
            inputStream?.read(tamanhoBytes, 0, 4)
            val tamanho = ByteBuffer.wrap(tamanhoBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            if (tamanho <= 0 || tamanho > 1024 * 1024) { // Max 1MB
                Log.w(TAG, "Tamanho de mensagem inválido: $tamanho bytes")
                return
            }
            
            // Lê dados da mensagem
            val dados = ByteArray(tamanho)
            var totalLido = 0
            while (totalLido < tamanho) {
                val lido = inputStream?.read(dados, totalLido, tamanho - totalLido) ?: -1
                if (lido == -1) throw EOFException("Conexão encerrada durante leitura")
                totalLido += lido
            }
            
            // Converte para string JSON
            val json = String(dados, Charsets.UTF_8)
            
            Log.d(TAG, "Mensagem recebida: $json")
            
            withContext(Dispatchers.Main) {
                processarMensagem(json)
            }
            
        } catch (e: Exception) {
            throw e
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
     * Protocolo: [tamanho (4 bytes LITTLE_ENDIAN)][dados JSON]
     */
    fun enviarMensagem(mensagem: String): Boolean {
        return try {
            if (!isConnected || socket == null) {
                Log.w(TAG, "Socket não conectado, não é possível enviar mensagem")
                return false
            }
            
            val dados = mensagem.toByteArray(Charsets.UTF_8)
            val tamanho = dados.size
            
            // Monta pacote: [tamanho][dados]
            val packet = ByteBuffer.allocate(4 + tamanho).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(tamanho)
            packet.put(dados)
            
            outputStream?.write(packet.array())
            outputStream?.flush()
            
            Log.d(TAG, "Mensagem enviada: $mensagem")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar mensagem", e)
            desconectarInterno()
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
                conectar(currentHost!!, currentPort)
            }
        }
    }
    
    /**
     * Desconecta internamente (sem chamar callback)
     */
    private fun desconectarInterno() {
        isConnected = false
        
        receiveJob?.cancel()
        
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar socket", e)
        }
        
        socket = null
        outputStream = null
        inputStream = null
        
        scope.launch(Dispatchers.Main) {
            onDesconectado?.invoke()
        }
        
        tentarReconectar()
    }
    
    /**
     * Desconecta o socket
     */
    fun desconectar() {
        Log.d(TAG, "Desconectando socket")
        
        isConnected = false
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS // Impede reconexão
        
        receiveJob?.cancel()
        
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar socket", e)
        }
        
        socket = null
        outputStream = null
        inputStream = null
        currentHost = null
        currentPort = 0
        
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
    }
}
