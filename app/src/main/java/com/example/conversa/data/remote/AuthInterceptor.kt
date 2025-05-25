// com.example.conversa.data.remote/AuthInterceptor.kt
package com.example.conversa.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.conversa.data.repository.AuthRepository
import com.example.conversa.ui.login.LoginActivity
import com.example.conversa.utils.ApiConfig // Para obter a ApiService (se AuthRepository precisar)
import com.example.conversa.utils.Constants // Para acessar as chaves SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Intercepta requisições HTTP para adicionar o token de autenticação
 * e lidar com respostas 401 Unauthorized.
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    // AuthRepository precisa ser obtido sem causar um loop de dependência.
    // Podemos usar lazy para atrasar a inicialização.
    private val authRepository: AuthRepository by lazy {
        val sharedPreferences = context.getSharedPreferences(Constants.AUTH_PREFS, Context.MODE_PRIVATE)
        // Se seu AuthRepository precisa da ApiService no construtor, você pode passar ApiConfig.apiService aqui
        AuthRepository(ApiConfig.apiService, sharedPreferences) // <-- Adapte se o construtor do AuthRepository for diferente
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Adiciona o token de autenticação (se existir)
        val requestBuilder = originalRequest.newBuilder()
        authRepository.getAuthToken()?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
            Log.d("AuthInterceptor", "Token adicionado: Bearer $token")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        // 2. Verifica o código de resposta 401 Unauthorized
        if (response.code == 401) {
            Log.e("AuthInterceptor", "Resposta 401 Unauthorized. Redirecionando para Login.")
            // Limpa os dados de autenticação (token, ID, etc.)
            authRepository.clearAuthData()

            // Redireciona para a tela de login
            // IMPORTANTE: Isso deve ser feito na Thread principal e fora do contexto de Activity,
            // então usamos FLAG_ACTIVITY_NEW_TASK para iniciar uma nova tarefa.
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)

            // Você pode querer lançar uma exceção ou retornar uma resposta vazia para abortar a chamada original
            // return Response.Builder()
            //    .request(request)
            //    .protocol(response.protocol)
            //    .code(401)
            //    .message("Unauthorized - Redirecting to Login")
            //    .build()
        }

        return response
    }
}