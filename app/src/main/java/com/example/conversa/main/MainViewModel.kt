// com.example.conversa.main/MainViewModel.kt
package com.example.conversa.main

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.repository.ContactRepository
import com.example.conversa.data.repository.ConversationRepository
import com.example.conversa.data.request.CreateConversationRequest
import com.example.conversa.data.response.CreateConversationResponse
import com.example.conversa.model.Contact
import com.example.conversa.model.Conversation
import com.example.conversa.utils.Constants
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainViewModel(
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val prefs: SharedPreferences // Injetar SharedPreferences para obter o ID do usuário
) : ViewModel() {

    // LiveData para a lista de contatos
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    // LiveData para a lista de conversas
    private val _conversations = MutableLiveData<List<Conversation>>()
    val conversations: LiveData<List<Conversation>> = _conversations

    // LiveData para o estado da UI (carregando, erro, sucesso)
    private val _uiState = MutableLiveData<MainUiState>()
    val uiState: LiveData<MainUiState> = _uiState

    // LiveData para eventos de navegação para o chat (novo chat criado)
    private val _navigateToChat = MutableLiveData<Pair<Int, String>>() // Pair<chatId, contactName>
    val navigateToChat: LiveData<Pair<Int, String>> = _navigateToChat

    sealed class MainUiState {
        object Loading : MainUiState()
        object Success : MainUiState()
        data class Error(val message: String) : MainUiState()
        object Initial : MainUiState()
    }

    init {
        fetchConversations()
        fetchContacts()
    }

    fun fetchConversations() {
        _uiState.value = MainUiState.Loading
        viewModelScope.launch {
            try {
                val fetchedConversations = conversationRepository.getConversations()
                _conversations.postValue(fetchedConversations) // Usar postValue para atualizar em background thread
                _uiState.postValue(MainUiState.Success)
            } catch (e: Exception) {
                _uiState.postValue(MainUiState.Error(e.message ?: "Erro ao carregar conversas"))
            }
        }
    }

    fun fetchContacts() {
        _uiState.value = MainUiState.Loading
        viewModelScope.launch {
            try {
                val fetchedContacts = contactRepository.getContacts()
                _contacts.postValue(fetchedContacts)
                _uiState.postValue(MainUiState.Success)
            } catch (e: Exception) {
                _uiState.postValue(MainUiState.Error(e.message ?: "Erro ao carregar contatos"))
            }
        }
    }

    fun createNewConversation(contact: Contact) {
        _uiState.value = MainUiState.Loading
        viewModelScope.launch {
            try {
                // A API espera 'descricao' para a nova conversa.
                // Usaremos o nome do contato como descrição por enquanto.
                val request = CreateConversationRequest(tipo = 1, descricao = contact.nome) // Tipo 1 para chat individual
                val response = conversationRepository.createConversation(request)

                // Após criar a conversa, navegar para o chat
                _navigateToChat.postValue(Pair(response.id, contact.nome)) // Passa o ID da nova conversa e o nome do contato
                _uiState.postValue(MainUiState.Success) // Marca como sucesso após navegação
            } catch (e: HttpException) {
                val errorMessage = when (e.code()) {
                    409 -> "Conversa com ${contact.nome} já existe." // Exemplo de tratamento para conflito
                    else -> "Erro ao criar conversa: ${e.message() ?: "Erro desconhecido"}"
                }
                _uiState.postValue(MainUiState.Error(errorMessage))
            } catch (e: Exception) {
                _uiState.postValue(MainUiState.Error(e.message ?: "Erro desconhecido ao criar conversa"))
            }
        }
    }

    fun getUserId(): Int {
        return prefs.getInt(Constants.AUTH_USER_ID, -1)
    }

    fun getUserName(): String {
        return prefs.getString(Constants.AUTH_USER_NAME, "Usuário") ?: "Usuário"
    }

    // Método para resetar o evento de navegação após ele ser consumido
    fun doneNavigatingToChat() {
        _navigateToChat.value = null
    }
}