package com.example.conversa.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.conversa.data.request.LoginRequest
import com.example.conversa.data.repository.AuthRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException // Importe para lidar com erros HTTP

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    // LiveData que a Activity vai observar para saber o estado do login
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    // Sealed class para representar os diferentes estados da UI durante o login
    sealed class LoginState {
        object Initial : LoginState() // Estado inicial, antes de qualquer operação
        object Loading : LoginState() // Login em andamento
        object Success : LoginState() // Login bem-sucedido
        data class Error(val message: String) : LoginState() // Erro no login, com mensagem
    }

    // Função que a Activity chamará para iniciar o processo de login
    fun login(login: String, senha: String) {
        _loginState.value = LoginState.Loading // Indica que o login está carregando

        // viewModelScope garante que a coroutine será cancelada automaticamente quando o ViewModel for destruído
        viewModelScope.launch {
            try {
                val request = LoginRequest(login, senha)
                authRepository.login(request) // Chama o método de login do repositório
                _loginState.value = LoginState.Success // Notifica sucesso
            } catch (e: HttpException) {
                // Erro HTTP (ex: 401 Unauthorized, 404 Not Found)
                val errorMessage = when (e.code()) {
                    401 -> "Usuário ou senha inválidos."
                    else -> "Erro na rede: ${e.message() ?: "Erro desconhecido"}"
                }
                _loginState.value = LoginState.Error(errorMessage)
            } catch (e: Exception) {
                // Outros erros (ex: de conexão, parsing)
                _loginState.value = LoginState.Error(e.message ?: "Erro desconhecido ao logar.")
            }
        }
    }

    // Verifica se já existe um token de autenticação salvo
    fun isLoggedIn(): Boolean {
        return authRepository.getAuthToken() != null
    }

    // Opcional: para deslogar
    fun logout() {
        authRepository.clearAuthData()
        _loginState.value = LoginState.Initial // Reinicia o estado de login
    }
}