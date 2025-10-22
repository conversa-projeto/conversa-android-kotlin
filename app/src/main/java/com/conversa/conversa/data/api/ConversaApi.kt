package com.conversa.conversa.data.api

import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.LoginRequest
import com.conversa.conversa.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ConversaApi {
    
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @GET("conversas")
    suspend fun listarConversas(
        @Header("Authorization") token: String
    ): Response<List<Conversa>>
}
