package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.LoginRequest
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityLoginBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        
        setupListeners()
        
        // Verifica se deve fazer login automático
        lifecycleScope.launch {
            verificarLoginAutomatico()
        }
    }
    
    private suspend fun verificarLoginAutomatico() {
        try {
            // Carrega URL da API
            val apiUrl = userPreferences.apiUrl.first()
            if (!apiUrl.isNullOrEmpty()) {
                RetrofitClient.setBaseUrl(apiUrl)
            }
            
            // Carrega credenciais salvas
            val savedLogin = userPreferences.savedLogin.first()
            val savedPassword = userPreferences.savedPassword.first()
            
            if (!savedLogin.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                // Preenche os campos
                runOnUiThread {
                    binding.etLogin.setText(savedLogin)
                    binding.etSenha.setText(savedPassword)
                    binding.cbLembrar.isChecked = true
                }
                
                // Faz login automático
                realizarLogin(savedLogin, savedPassword)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val login = binding.etLogin.text.toString().trim()
            val senha = binding.etSenha.text.toString().trim()
            
            if (validarCampos(login, senha)) {
                realizarLogin(login, senha)
            }
        }
        
        binding.tvConfigurarServidor.setOnClickListener {
            val intent = Intent(this, ConfigApiActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun validarCampos(login: String, senha: String): Boolean {
        if (login.isEmpty()) {
            binding.etLogin.error = "Campo obrigatório"
            return false
        }
        
        if (senha.isEmpty()) {
            binding.etSenha.error = "Campo obrigatório"
            return false
        }
        
        return true
    }
    
    private fun realizarLogin(login: String, senha: String) {
        lifecycleScope.launch {
            try {
                mostrarLoading(true)
                
                val request = LoginRequest(
                    login = login,
                    senha = senha,
                    dispositivo_id = null
                )
                
                val response = RetrofitClient.api.login(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    
                    // Salva dados do usuário
                    userPreferences.saveUserData(
                        token = loginResponse.token,
                        userId = loginResponse.id,
                        userName = loginResponse.nome
                    )
                    
                    // Salva credenciais se checkbox marcado
                    if (binding.cbLembrar.isChecked) {
                        userPreferences.saveLoginCredentials(login, senha)
                    } else {
                        userPreferences.clearLoginCredentials()
                    }
                    
                    // Mostra mensagem de sucesso
                    Toast.makeText(
                        this@LoginActivity,
                        "Login realizado com sucesso!\nBem-vindo, ${loginResponse.nome}",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Aguarda 1 segundo e vai para MainActivity
                    kotlinx.coroutines.delay(1000)
                    abrirMainActivity()
                    
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Usuário ou senha incorretos"
                        404 -> "Usuário não encontrado"
                        else -> "Erro no login: ${response.code()}"
                    }
                    Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
                
                mostrarLoading(false)
                
            } catch (e: Exception) {
                mostrarLoading(false)
                e.printStackTrace()
                Toast.makeText(
                    this@LoginActivity,
                    "Erro ao conectar com servidor: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun mostrarLoading(mostrar: Boolean) {
        binding.progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !mostrar
        binding.etLogin.isEnabled = !mostrar
        binding.etSenha.isEnabled = !mostrar
        binding.cbLembrar.isEnabled = !mostrar
        binding.tvConfigurarServidor.isEnabled = !mostrar
    }
    
    private fun abrirMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
