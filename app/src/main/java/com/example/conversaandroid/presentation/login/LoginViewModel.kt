package com.example.conversaandroid.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.data.repository.AuthRepository
import com.seudominio.conversa.data.websocket.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String, senha: String, manterConectado: Boolean) {
        viewModelScope.launch {
            authRepository.login(email, senha).collect { response ->
                when (response) {
                    is ApiResponse.Loading -> {
                        _loginState.value = LoginState.Loading
                    }
                    is ApiResponse.Success -> {
                        preferencesManager.setKeepLogged(manterConectado)
                        webSocketClient.connect()
                        _loginState.value = LoginState.Success
                    }
                    is ApiResponse.Error -> {
                        _loginState.value = LoginState.Error(response.message)
                    }
                }
            }
        }
    }

    fun registrar(nome: String, email: String, telefone: String, senha: String) {
        viewModelScope.launch {
            authRepository.registrar(nome, email, telefone, senha).collect { response ->
                when (response) {
                    is ApiResponse.Loading -> {
                        _loginState.value = LoginState.Loading
                    }
                    is ApiResponse.Success -> {
                        // Após registrar, fazer login automaticamente
                        login(email, senha, false)
                    }
                    is ApiResponse.Error -> {
                        _loginState.value = LoginState.Error(response.message)
                    }
                }
            }
        }
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}