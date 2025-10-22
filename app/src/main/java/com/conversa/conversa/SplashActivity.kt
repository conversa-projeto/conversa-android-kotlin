package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.preferences.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        userPreferences = UserPreferences(this)
        
        // Aguarda 2 segundos e verifica configurações
        lifecycleScope.launch {
            delay(2000)
            verificarConfiguracao()
        }
    }
    
    private suspend fun verificarConfiguracao() {
        try {
            // Primeiro verifica se há URL da API configurada
            val apiUrl = userPreferences.apiUrl.first()
            
            if (apiUrl.isNullOrEmpty()) {
                // Não há URL configurada, vai para tela de configuração
                abrirConfigApi()
            } else {
                // Há URL configurada, define no Retrofit
                RetrofitClient.setBaseUrl(apiUrl)
                
                // Agora verifica se há credenciais salvas
                verificarCredenciais()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Erro ao verificar configurações", Toast.LENGTH_SHORT).show()
            }
            abrirConfigApi()
        }
    }
    
    private suspend fun verificarCredenciais() {
        try {
            val savedLogin = userPreferences.savedLogin.first()
            val savedPassword = userPreferences.savedPassword.first()
            
            if (!savedLogin.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                // Tem credenciais salvas, tenta fazer login automático
                runOnUiThread {
                    Toast.makeText(this, "Fazendo login automático...", Toast.LENGTH_SHORT).show()
                }
                
                // Vai para LoginActivity que fará o login automático
                abrirLogin()
            } else {
                // Não tem credenciais, vai para login normal
                abrirLogin()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            abrirLogin()
        }
    }
    
    private fun abrirConfigApi() {
        val intent = Intent(this, ConfigApiActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun abrirLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
