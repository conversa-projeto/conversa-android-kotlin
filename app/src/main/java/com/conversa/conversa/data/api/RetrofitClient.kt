package com.conversa.conversa.data.api

import UtcToLocalDateTimeDeserializer
import com.conversa.conversa.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime

object RetrofitClient {
    
    private var currentBaseUrl: String = BuildConfig.API_URL
    private var retrofit: Retrofit? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            val gson = GsonBuilder()
                .registerTypeAdapter(LocalDateTime::class.java, UtcToLocalDateTimeDeserializer())
                .create()
            retrofit = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        return retrofit!!
    }
    
    fun setBaseUrl(url: String) {
        if (currentBaseUrl != url) {
            currentBaseUrl = url
            retrofit = null // Force rebuild with new URL
        }
    }
    
    val api: ConversaApi
        get() = getRetrofit().create(ConversaApi::class.java)
}
