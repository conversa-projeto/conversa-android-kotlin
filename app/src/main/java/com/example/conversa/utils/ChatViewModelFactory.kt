package com.example.conversa.utils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.conversa.chat.ChatViewModel
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.repository.MessageRepository
import com.example.conversa.data.repository.AuthRepository
import com.example.conversa.utils.Constants // <-- Importe Constants

class ChatViewModelFactory(private val context: Context, private val chatId: Int, private val contactName: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val apiService: ApiService = ApiConfig.apiService

            val messageRepository = MessageRepository(apiService)

            // AQUI ESTÁ A CORREÇÃO: Usando Constants.AUTH_PREFS
            val sharedPreferences = context.getSharedPreferences(Constants.AUTH_PREFS, Context.MODE_PRIVATE)
            val authRepository = AuthRepository(apiService, sharedPreferences)
            val loggedInUserId: Int = authRepository.getUserId()

            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(messageRepository, loggedInUserId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}