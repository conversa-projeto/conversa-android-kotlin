// com.example.conversa.ui.login/LoginActivity.kt
package com.example.conversa.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.conversa.databinding.ActivityLoginBinding // Gerado pelo View Binding
import com.example.conversa.main.MainActivity
import com.example.conversa.utils.LoginViewModelFactory // Seu factory criado anteriormente

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding // Para View Binding
    private lateinit var loginViewModel: LoginViewModel // A instância do seu ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o View Binding para acessar os componentes do layout
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa o ViewModel usando o Factory
        // O LoginViewModelFactory precisa do applicationContext para o SharedPreferences
        val factory = LoginViewModelFactory(applicationContext)
        loginViewModel = ViewModelProvider(this, factory).get(LoginViewModel::class.java)

        // 1. Verifica se já está logado
        if (loginViewModel.isLoggedIn()) {
            // Se já estiver logado, redireciona para a MainActivity e finaliza LoginActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return // Importante para sair do onCreate e evitar a exibição da tela de login
        }

        // 2. Observa o estado de login do ViewModel
        loginViewModel.loginState.observe(this) { state ->
            when (state) {
                // Estado de Carregamento: Opcional, mostra um toast ou um spinner
                LoginViewModel.LoginState.Loading -> {
                    binding.buttonLogin.isEnabled = false // Desabilita o botão para evitar múltiplos cliques
                    Toast.makeText(this, "Autenticando...", Toast.LENGTH_SHORT).show()
                }
                // Estado de Sucesso: Login realizado com sucesso
                LoginViewModel.LoginState.Success -> {
                    Toast.makeText(this, "Bem-vindo!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish() // Finaliza LoginActivity para que o usuário não possa voltar a ela
                }
                // Estado de Erro: Ocorreu um problema no login
                is LoginViewModel.LoginState.Error -> {
                    binding.buttonLogin.isEnabled = true // Reabilita o botão
                    // Exibe a mensagem de erro que veio do ViewModel
                    Toast.makeText(this, "Erro: ${state.message}", Toast.LENGTH_LONG).show()
                }
                // Estado Inicial: Sem operação de login em andamento
                LoginViewModel.LoginState.Initial -> {
                    binding.buttonLogin.isEnabled = true // Garante que o botão esteja habilitado
                }
            }
        }

        // 3. Configura o listener para o botão de login
        binding.buttonLogin.setOnClickListener {
            val login = binding.editTextLogin.text.toString().trim()
            val senha = binding.editTextSenha.text.toString().trim()

            if (login.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
            } else {
                loginViewModel.login(login, senha) // Delega a lógica de login ao ViewModel
            }
        }
    }
}