package com.example.conversaandroid.data.repository

import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.api.ConversaApiService
import com.seudominio.conversa.data.api.request.CriarUsuarioRequest
import com.seudominio.conversa.data.api.request.LoginRequest
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.domain.model.Usuario
import com.seudominio.conversa.utils.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ConversaApiService,
    private val preferencesManager: PreferencesManager
) {

    suspend fun login(email: String, senha: String): Flow<ApiResponse<Usuario>> = flow {
        emit(ApiResponse.Loading())

        val deviceId = preferencesManager.getDeviceId()
        val request = LoginRequest(
            login = email,
            senha = senha,
            dispositivoId = deviceId
        )

        val response = NetworkUtils.safeApiCall {
            apiService.login(request)
        }

        when (response) {
            is ApiResponse.Success -> {
                val loginResponse = response.data
                preferencesManager.saveToken(loginResponse.token)

                val usuario = Usuario(
                    id = loginResponse.id,
                    nome = loginResponse.nome,
                    email = loginResponse.email,
                    telefone = loginResponse.telefone ?: ""
                )
                preferencesManager.saveUser(usuario)

                emit(ApiResponse.Success(usuario))
            }
            is ApiResponse.Error -> {
                emit(ApiResponse.Error(response.message, response.code))
            }
            is ApiResponse.Loading -> {
                // Já emitido
            }
        }
    }

    suspend fun registrar(
        nome: String,
        email: String,
        telefone: String,
        senha: String
    ): Flow<ApiResponse<Usuario>> = flow {
        emit(ApiResponse.Loading())

        val request = CriarUsuarioRequest(
            nome = nome,
            login = email,
            email = email,
            telefone = telefone,
            senha = senha
        )

        val response = NetworkUtils.safeApiCall {
            apiService.criarUsuario(request)
        }

        when (response) {
            is ApiResponse.Success -> {
                val usuarioResponse = response.data
                val usuario = Usuario(
                    id = usuarioResponse.id,
                    nome = usuarioResponse.nome,
                    login = usuarioResponse.login,
                    email = usuarioResponse.email,
                    telefone = usuarioResponse.telefone ?: ""
                )
                emit(ApiResponse.Success(usuario))
            }
            is ApiResponse.Error -> {
                emit(ApiResponse.Error(response.message, response.code))
            }
            is ApiResponse.Loading -> {
                // Já emitido
            }
        }
    }

    fun logout() {
        preferencesManager.clearToken()
        preferencesManager.clearUser()
    }

    fun isLoggedIn(): Boolean = preferencesManager.isLoggedIn()

    fun getCurrentUser(): Usuario? = preferencesManager.getUser()
}