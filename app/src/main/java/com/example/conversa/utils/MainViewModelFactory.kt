package com.example.conversa.utils

import android.content.Context
import android.content.SharedPreferences // <-- NOVO: Importe SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.repository.AuthRepository // Manter se você usa AuthRepository para outras coisas na MainViewModel, mas não para o construtor dela
import com.example.conversa.data.repository.ContactRepository
import com.example.conversa.data.repository.ConversationRepository
import com.example.conversa.main.MainViewModel
import com.example.conversa.utils.Constants

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val apiService: ApiService = ApiConfig.apiService

            // Obtenha SharedPreferences aqui para passar para os repositórios, se necessário, e para o MainViewModel
            val sharedPreferences = context.getSharedPreferences(Constants.AUTH_PREFS, Context.MODE_PRIVATE)

            // Inicialize os repositórios que o MainViewModel espera
            val contactRepository = ContactRepository(apiService)
            val conversationRepository = ConversationRepository(apiService)

            // Se o AuthRepository for usado em algum método do MainViewModel,
            // mas não no construtor, você ainda pode inicializá-lo aqui.
            // val authRepository = AuthRepository(apiService, sharedPreferences) // <-- Se não for passado para o construtor do MainViewModel

            @Suppress("UNCHECKED_CAST")
            // AQUI ESTÁ A CORREÇÃO: Passando os parâmetros na ordem correta
            return MainViewModel(contactRepository, conversationRepository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}