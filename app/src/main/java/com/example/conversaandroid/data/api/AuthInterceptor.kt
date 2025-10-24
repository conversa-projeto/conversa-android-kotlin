package com.example.conversaandroid.data.api

import com.seudominio.conversa.data.preferences.PreferencesManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Não adiciona token para rotas públicas
        val publicPaths = listOf("/login", "/status", "/usuario")
        val isPublicPath = publicPaths.any { path ->
            request.url.encodedPath.contains(path) && request.method == "PUT"
        }

        if (isPublicPath) {
            return chain.proceed(request)
        }

        // Adiciona token JWT ao header
        val token = preferencesManager.getToken()
        return if (!token.isNullOrEmpty()) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}