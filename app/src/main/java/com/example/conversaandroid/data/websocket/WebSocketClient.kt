package com.example.conversaandroid.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.seudominio.conversa.BuildConfig
import com.seudominio.conversa.data.preferences.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesManager: PreferencesManager,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY = 5000L // 5 segundos
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var isConnected = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableSharedFlow<SocketMessage>()
    val messages: SharedFlow<SocketMessage> = _messages

    private val _connectionState = MutableSharedFlow<ConnectionState>()
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    fun connect() {
        if (isConnected) {
            Log.d(TAG, "WebSocket já está conectado")
            return
        }

        val token = preferencesManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token não encontrado, não é possível conectar")
            coroutineScope.launch {
                _connectionState.emit(ConnectionState.Error("Token não encontrado"))
            }
            return
        }

        val request = Request.Builder()
            .url(BuildConfig.WS_URL)
            .build()

        val client = okHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())

        coroutineScope.launch {
            _connectionState.emit(ConnectionState.Connecting)
        }
    }

    private fun createWebSocketListener() = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket conectado")
            isConnected = true
            reconnectAttempts = 0

            // Envia mensagem de login
            val token = preferencesManager.getToken()
            val loginMessage = SocketMessage.Login(token ?: "")
            sendMessage(loginMessage)

            coroutineScope.launch {
                _connectionState.emit(ConnectionState.Connected)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Mensagem recebida: $text")

            coroutineScope.launch {
                try {
                    val message = parseMessage(text)
                    _messages.emit(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar mensagem", e)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket fechando: $code - $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket fechado: $code - $reason")
            isConnected = false

            coroutineScope.launch {
                _connectionState.emit(ConnectionState.Disconnected)
            }

            // Tenta reconectar se não foi fechamento intencional
            if (code != 1000) {
                attemptReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket erro", t)
            isConnected = false

            coroutineScope.launch {
                _connectionState.emit(ConnectionState.Error(t.message ?: "Erro desconhecido"))
            }

            attemptReconnect()
        }
    }

    private fun attemptReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            Log.d(TAG, "Tentando reconectar... Tentativa $reconnectAttempts")

            coroutineScope.launch {
                delay(RECONNECT_DELAY * reconnectAttempts)
                connect()
            }
        } else {
            Log.e(TAG, "Máximo de tentativas de reconexão atingido")
            coroutineScope.launch {
                _connectionState.emit(ConnectionState.Error("Não foi possível reconectar"))
            }
        }
    }

    fun sendMessage(message: SocketMessage) {
        if (!isConnected) {
            Log.e(TAG, "WebSocket não está conectado")
            return
        }

        val json = when (message) {
            is SocketMessage.Login -> {
                gson.toJson(mapOf("tipo" to 1, "token" to message.token))
            }
            is SocketMessage.NovaMensagem -> {
                gson.toJson(mapOf(
                    "tipo" to 2,
                    "conversa_id" to message.conversaId,
                    "mensagem_id" to message.mensagemId
                ))
            }
            is SocketMessage.AtualizacaoStatus -> {
                gson.toJson(mapOf(
                    "tipo" to 3,
                    "grupo" to message.grupo,
                    "mensagens" to message.mensagens.joinToString(",")
                ))
            }
            else -> return
        }

        webSocket?.send(json)
    }

    private fun parseMessage(json: String): SocketMessage {
        val jsonObject = gson.fromJson(json, Map::class.java)
        val tipo = (jsonObject["tipo"] as Double).toInt()

        return when (tipo) {
            0 -> SocketMessage.Erro(jsonObject["message"] as? String ?: "Erro desconhecido")
            1 -> SocketMessage.Login(jsonObject["token"] as? String ?: "")
            2 -> SocketMessage.NovaMensagem(
                conversaId = (jsonObject["conversa_id"] as Double).toInt(),
                mensagemId = (jsonObject["mensagem_id"] as Double).toInt()
            )
            3 -> {
                val mensagensStr = jsonObject["mensagens"] as? String ?: ""
                val mensagens = mensagensStr.split(",").mapNotNull { it.toIntOrNull() }
                SocketMessage.AtualizacaoStatus(
                    grupo = (jsonObject["grupo"] as Double).toInt(),
                    mensagens = mensagens
                )
            }
            else -> SocketMessage.Desconhecida(json)
        }
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Desconectando")
        webSocket = null
    }

    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
    }
}