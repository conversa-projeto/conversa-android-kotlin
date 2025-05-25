package com.example.conversa.utils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.conversa.data.remote.ApiService // Mantenha esta importação
import com.example.conversa.data.repository.AuthRepository
import com.example.conversa.ui.login.LoginViewModel
import com.example.conversa.utils.Constants // Importe Constants

class LoginViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            // AQUI ESTÁ A CORREÇÃO: Acessando a propriedade apiService diretamente
            val apiService: ApiService = ApiConfig.apiService // Era ApiConfig.getApiService()

            // Obtenha SharedPreferences usando o context e Constants.AUTH_PREFS
            val sharedPreferences = context.getSharedPreferences(Constants.AUTH_PREFS, Context.MODE_PRIVATE)

            val authRepository = AuthRepository(apiService, sharedPreferences)
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}