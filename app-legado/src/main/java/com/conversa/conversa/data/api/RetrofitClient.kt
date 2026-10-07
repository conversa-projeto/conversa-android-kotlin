package com.conversa.conversa.data.api

import UtcToLocalDateTimeDeserializer
import com.conversa.conversa.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {
    
    private var currentBaseUrl: String = BuildConfig.API_URL
    private var retrofit: Retrofit? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAll, SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.build()
    }
    
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
