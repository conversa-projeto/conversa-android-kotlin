// com.example.conversa.utils/ApiConfig.kt
package com.example.conversa.utils

import android.content.Context
import com.example.conversa.data.remote.ApiService
import com.example.conversa.data.remote.AuthInterceptor // <-- NOVO: Importe o Interceptor
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit // Para timeouts

object ApiConfig {

    lateinit var apiService: ApiService
        private set

    // A instância do OkHttpClient precisa ser criada uma vez.
    private lateinit var okHttpClient: OkHttpClient

    fun init(context: Context) {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        // Configura o OkHttpClient com o Interceptor
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context)) // <-- Adiciona o Interceptor aqui
            .connectTimeout(30, TimeUnit.SECONDS) // Exemplo: Timeout de conexão
            .readTimeout(30, TimeUnit.SECONDS)    // Exemplo: Timeout de leitura
            .writeTimeout(30, TimeUnit.SECONDS)   // Exemplo: Timeout de escrita
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient) // <-- Define o OkHttpClient customizado
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}