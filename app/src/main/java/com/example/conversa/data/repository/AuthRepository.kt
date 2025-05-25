package com.example.conversa.data.repository

import android.content.SharedPreferences
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.request.LoginRequest
import com.example.conversa.data.response.LoginResponse
import com.example.conversa.utils.Constants // Importe as constantes

class AuthRepository(
    private val apiService: ApiService,
    private val prefs: SharedPreferences // SharedPreferences será injetado
) {

    suspend fun login(request: LoginRequest): LoginResponse {
        val response = apiService.login(request)
        // Salva o token e dados do usuário após o login
        prefs.edit().apply {
            putInt(Constants.AUTH_USER_ID, response.id)
            putString(Constants.AUTH_TOKEN, response.token)
            putString(Constants.AUTH_USER_NAME, response.nome)
            putString(Constants.AUTH_USER_EMAIL, response.email)
            putString(Constants.AUTH_USER_PHONE, response.telefone)
            apply() // Aplica as mudanças
        }
        return response
    }

    fun getAuthToken(): String? {
        return prefs.getString(Constants.AUTH_TOKEN, null)
    }

    fun getUserId(): Int {
        return prefs.getInt(Constants.AUTH_USER_ID, -1)
    }

    fun clearAuthData() {
        prefs.edit().clear().apply() // Limpa todos os dados de autenticação
    }
}