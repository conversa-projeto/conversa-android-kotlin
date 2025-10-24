package com.example.conversaandroid.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.data.repository.ConversaRepository
import com.seudominio.conversa.data.repository.MensagemRepository
import com.seudominio.conversa.data.websocket.SocketMessage
import com.seudominio.conversa.data.websocket.WebSocketClient
import com.seudominio.conversa.domain.model.Conversa
import com.seudominio.conversa.domain.model.Mensagem
import com.seudominio.conversa.domain.model.TipoConteudo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val mensagemRepository: MensagemRepository,
    private val conversaRepository: ConversaRepository,
    private val preferencesManager: PreferencesManager,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _mensagens = MutableStateFlow<List<Mensagem>>(emptyList())
    val mensagens: StateFlow<List<Mensagem>> = _mensagens

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentConversa: Conversa? = null

    init {
        observeWebSocketMessages()
    }

    fun loadConversa(conversa: Conversa) {
        currentConversa = conversa
        loadMensagensLocal()
        syncMensagens()
        marcarMensagensComoVisualizadas()
    }

    private fun loadMensagensLocal() {
        viewModelScope.launch {
            currentConversa?.let { conversa ->
                mensagemRepository.getMensagensLocal(conversa.id).collect { mensagens ->
                    _mensagens.value = mensagens
                }
            }
        }
    }

    private fun syncMensagens() {
        viewModelScope.launch {
            currentConversa?.let { conversa ->
                _isLoading.value = true

                when (val response = mensagemRepository.syncMensagens(conversa.id)) {
                    is ApiResponse.Success -> {
                        // Mensagens já salvas no repositório
                    }
                    is ApiResponse.Error -> {
                        _error.value = response.message
                    }
                    else -> {}
                }

                _isLoading.value = false
            }
        }
    }

    fun sendMessage(text: String? = null, file: File? = null, tipoArquivo: TipoConteudo = TipoConteudo.ARQUIVO) {
        viewModelScope.launch {
            currentConversa?.let { conversa ->
                _isSending.value = true

                when (val response = mensagemRepository.enviarMensagem(
                    conversaId = conversa.id,
                    texto = text,
                    arquivo = file,
                    tipoArquivo = tipoArquivo
                )) {
                    is ApiResponse.Success -> {
                        // Mensagem enviada com sucesso
                    }
                    is ApiResponse.Error -> {
                        _error.value = response.message
                    }
                    else -> {}
                }

                _isSending.value = false
            }
        }
    }

    private fun marcarMensagensComoVisualizadas() {
        viewModelScope.launch {
            currentConversa?.let { conversa ->
                mensagemRepository.marcarTodasComoVisualizadas(conversa.id)
            }
        }
    }

    private fun observeWebSocketMessages() {
        viewModelScope.launch {
            webSocketClient.messages.collect { message ->
                when (message) {
                    is SocketMessage.NovaMensagem -> {
                        if (message.conversaId == currentConversa?.id) {
                            syncMensagens()
                            marcarMensagensComoVisualizadas()
                        }
                    }
                    is SocketMessage.AtualizacaoStatus -> {
                        // Atualizar status das mensagens
                        syncMensagens()
                    }
                    else -> {}
                }
            }
        }
    }

    fun getCurrentUserId(): Int {
        return preferencesManager.getUser()?.id ?: 0
    }

    override fun onCleared() {
        super.onCleared()
        currentConversa = null
    }
}