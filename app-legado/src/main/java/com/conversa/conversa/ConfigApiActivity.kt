package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityConfigApiBinding
import kotlinx.coroutines.launch

class ConfigApiActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConfigApiBinding
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigApiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        
        supportActionBar?.title = "Configurar Servidor"
        
        setupListeners()
    }
    
    private fun setupListeners() {
        binding.btnSalvar.setOnClickListener {
            val apiUrl = binding.etApiUrl.text.toString().trim()
            
            if (validarUrl(apiUrl)) {
                salvarConfiguracao(apiUrl)
            }
        }
    }
    
    private fun validarUrl(url: String): Boolean {
        if (url.isEmpty()) {
            binding.etApiUrl.error = "Digite a URL do servidor"
            return false
        }
        
        // Verifica se começa com http:// ou https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            binding.etApiUrl.error = "URL deve começar com http:// ou https://"
            return false
        }
        
        // Verifica se termina com /
        if (!url.endsWith("/")) {
            binding.etApiUrl.error = "URL deve terminar com /"
            return false
        }
        
        return true
    }
    
    private fun salvarConfiguracao(apiUrl: String) {
        lifecycleScope.launch {
            try {
                userPreferences.saveApiUrl(apiUrl)
                
                Toast.makeText(
                    this@ConfigApiActivity,
                    "Configuração salva com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Volta para a tela de login
                val intent = Intent(this@ConfigApiActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ConfigApiActivity,
                    "Erro ao salvar configuração: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
