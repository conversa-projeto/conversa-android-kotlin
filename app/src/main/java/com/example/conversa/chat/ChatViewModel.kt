package com.example.conversa.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.conversa.data.repository.MessageRepository
import com.example.conversa.model.Message
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messageRepository: MessageRepository,
    val currentUserId: Int // <-- AQUI! Removido 'private'
) : ViewModel() {

    private val TAG = "ChatViewModel"

    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> get() = _messages

    private val _uiState = MutableLiveData<ChatUiState>()
    val uiState: LiveData<ChatUiState> get() = _uiState

    private var _chatId: Int = -1
    private var _contactName: String = ""

    fun setChatInfo(chatId: Int, contactName: String) {
        if (_chatId == chatId && _contactName == contactName) {
            Log.d(TAG, "ChatInfo já definido para chatId: $chatId. Ignorando setChatInfo.")
            return
        }
        _chatId = chatId
        _contactName = contactName
        Log.d(TAG, "Configurando chat com ID: $_chatId, Contato: $_contactName, Usuário logado: $currentUserId")
        loadMessages(chatId)
    }

    fun getContactName(): String {
        return _contactName
    }

    private fun loadMessages(chatId: Int) {
        if (chatId == -1 || currentUserId == -1) {
            _uiState.value = ChatUiState.Error("ID da conversa ou do usuário logado inválido para carregar mensagens.")
            Log.e(TAG, "Não foi possível carregar mensagens: chatId=$chatId, currentUserId=$currentUserId")
            return
        }

        _uiState.value = ChatUiState.Loading
        viewModelScope.launch {
            val result = messageRepository.getMessages(chatId, currentUserId)
            result.onSuccess { fetchedMessages ->
                _messages.value = fetchedMessages
                _uiState.value = ChatUiState.Success
                Log.d(TAG, "LiveData _messages atualizado com ${fetchedMessages.size} mensagens.")
                if (fetchedMessages.isEmpty()) {
                    Log.w(TAG, "Nenhuma mensagem retornada do MessageRepository para chatId: $chatId.")
                } else {
                    Log.d(TAG, "Primeira mensagem no LiveData: ID=${fetchedMessages.first().id}, Conteúdo='${fetchedMessages.first().content}', isMine=${fetchedMessages.first().isMine}")
                }
            }.onFailure { error ->
                _uiState.value = ChatUiState.Error("Erro ao carregar mensagens: ${error.message}")
                Log.e(TAG, "Erro ao carregar mensagens para chatId: $chatId", error)
            }
        }
    }

    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) {
            _uiState.value = ChatUiState.Error("Mensagem não pode ser vazia.")
            return
        }

        if (currentUserId == -1 || _chatId == -1) {
            _uiState.value = ChatUiState.Error("Erro: ID do usuário logado ou da conversa não disponível para enviar mensagem.")
            Log.e(TAG, "Não foi possível enviar mensagem: currentUserId=$currentUserId, _chatId=$_chatId")
            return
        }

        _uiState.value = ChatUiState.Loading

        viewModelScope.launch {
            val result = messageRepository.sendMessage(_chatId, currentUserId, messageText)
            result.onSuccess {
                val currentMessages = _messages.value.orEmpty().toMutableList()
                val tempMessage = Message(
                    id = System.currentTimeMillis().toInt(),
                    chatId = _chatId,
                    senderId = currentUserId,
                    senderName = "Você",
                    content = messageText,
                    timestamp = System.currentTimeMillis(),
                    isMine = true,
                    contents = emptyList()
                )
                currentMessages.add(tempMessage)
                _messages.value = currentMessages
                _uiState.value = ChatUiState.MessageSent
                Log.d(TAG, "Mensagem temporária adicionada e LiveData _messages atualizado. Total: ${currentMessages.size}")

                loadMessages(_chatId)

            }.onFailure { error ->
                _uiState.value = ChatUiState.Error("Falha ao enviar mensagem: ${error.message}")
                Log.e(TAG, "Erro ao enviar mensagem: ${error.message}", error)
            }
        }
    }

    sealed class ChatUiState {
        object Initial : ChatUiState()
        object Loading : ChatUiState()
        object Success : ChatUiState()
        object MessageSent : ChatUiState()
        data class Error(val message: String) : ChatUiState()
    }
}