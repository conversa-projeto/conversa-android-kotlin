package com.example.conversaandroid.presentation.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.seudominio.conversa.databinding.ActivityLoginBinding
import com.seudominio.conversa.presentation.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()

        // Verificar se já está logado
        if (viewModel.isLoggedIn()) {
            navigateToMain()
        }
    }

    private fun setupViews() {
        binding.apply {
            btnLogin.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val senha = etSenha.text.toString()

                if (validateInputs(email, senha)) {
                    viewModel.login(email, senha, cbManterConectado.isChecked)
                }
            }

            tvCriarConta.setOnClickListener {
                toggleRegisterMode()
            }

            btnRegistrar.setOnClickListener {
                val nome = etNome.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val telefone = etTelefone.text.toString().trim()
                val senha = etSenha.text.toString()
                val confirmarSenha = etConfirmarSenha.text.toString()

                if (validateRegisterInputs(nome, email, telefone, senha, confirmarSenha)) {
                    viewModel.registrar(nome, email, telefone, senha)
                }
            }

            tvVoltar.setOnClickListener {
                toggleLoginMode()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Loading -> {
                        showLoading(true)
                    }
                    is LoginState.Success -> {
                        showLoading(false)
                        navigateToMain()
                    }
                    is LoginState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is LoginState.Idle -> {
                        showLoading(false)
                    }
                }
            }
        }
    }

    private fun validateInputs(email: String, senha: String): Boolean {
        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "E-mail inválido"
            return false
        }

        if (senha.length < 6) {
            binding.etSenha.error = "Senha deve ter no mínimo 6 caracteres"
            return false
        }

        return true
    }

    private fun validateRegisterInputs(
        nome: String,
        email: String,
        telefone: String,
        senha: String,
        confirmarSenha: String
    ): Boolean {
        if (nome.isEmpty() || email.isEmpty() || senha.isEmpty() || confirmarSenha.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos obrigatórios", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "E-mail inválido"
            return false
        }

        if (senha.length < 6) {
            binding.etSenha.error = "Senha deve ter no mínimo 6 caracteres"
            return false
        }

        if (senha != confirmarSenha) {
            binding.etConfirmarSenha.error = "As senhas não coincidem"
            return false
        }

        return true
    }

    private fun toggleRegisterMode() {
        binding.apply {
            groupLogin.visibility = View.GONE
            groupRegistro.visibility = View.VISIBLE
            tvTitulo.text = "Criar Conta"
            etNome.requestFocus()
        }
    }

    private fun toggleLoginMode() {
        binding.apply {
            groupRegistro.visibility = View.GONE
            groupLogin.visibility = View.VISIBLE
            tvTitulo.text = "Conversa"
            etEmail.requestFocus()

            // Limpar campos de registro
            etNome.text?.clear()
            etTelefone.text?.clear()
            etConfirmarSenha.text?.clear()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !show
            btnRegistrar.isEnabled = !show
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}