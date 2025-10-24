package com.example.conversaandroid.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.repository.AuthRepository
import com.seudominio.conversa.data.repository.ConversaRepository
import com.seudominio.conversa.data.websocket.ConnectionState
import com.seudominio.conversa.data.websocket.SocketMessage
import com.seudominio.conversa.data.websocket.WebSocketClient
import com.seudominio.conversa.domain.model.Conversa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val conversaRepository: ConversaRepository,
    private val authRepository: AuthRepository,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _conversas = MutableStateFlow<List<Conversa>>(emptyList())
    val conversas: StateFlow<List<Conversa>> = _conversas

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    init {
        observeWebSocket()
        loadConversasFromLocal()
    }

    fun loadConversas() {
        viewModelScope.launch {
            conversaRepository.getConversasLocal().collect { localConversas ->
                _conversas.value = localConversas
            }
        }
    }

    private fun loadConversasFromLocal() {
        viewModelScope.launch {
            conversaRepository.getConversasLocal().collect { conversas ->
                _conversas.value = conversas
            }
        }
    }

    fun syncConversas() {
        viewModelScope.launch {
            _isLoading.value = true

            when (val response = conversaRepository.syncConversas()) {
                is ApiResponse.Success -> {
                    // Dados já salvos no repositório
                }
                is ApiResponse.Error -> {
                    // TODO: Mostrar erro
                }
                else -> {}
            }

            _isLoading.value = false
        }
    }

    fun connectWebSocket() {
        webSocketClient.connect()
    }

    fun disconnectWebSocket() {
        webSocketClient.disconnect()
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            webSocketClient.connectionState.collect { state ->
                _connectionState.value = when (state) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting"
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> "Error"
                }
            }
        }

        viewModelScope.launch {
            webSocketClient.messages.collect { message ->
                when (message) {
                    is SocketMessage.NovaMensagem -> {
                        // Atualizar conversa específica
                        syncConversas()
                    }
                    is SocketMessage.AtualizacaoStatus -> {
                        // Atualizar status das mensagens
                    }
                    else -> {}
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
        webSocketClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
    }
}