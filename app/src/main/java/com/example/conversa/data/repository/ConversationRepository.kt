// com.example.conversa.data.repository/ConversationRepository.kt
package com.example.conversa.data.repository

import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.request.CreateConversationRequest
import com.example.conversa.data.response.CreateConversationResponse
import com.example.conversa.model.Conversation

class ConversationRepository(private val apiService: ApiService) {
    suspend fun getConversations(): List<Conversation> {
        return apiService.getConversations()
    }

    suspend fun createConversation(request: CreateConversationRequest): CreateConversationResponse {
        return apiService.createConversation(request)
    }
}
