package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.conversa.conversa.adapter.ConversasAdapter
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityMainBinding
import com.conversa.conversa.databinding.ContentMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var isFirstLoad = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var contentBinding: ContentMainBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var conversasAdapter: ConversasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Bind do content_main
        contentBinding = ContentMainBinding.bind(binding.contentMain.root)
        
        userPreferences = UserPreferences(this)

        setSupportActionBar(binding.toolbar)
        
        setupRecyclerView()
        setupListeners()
        
        // Carrega configurações e conversas
        lifecycleScope.launch {
            carregarConfiguracoes()
            carregarConversas()
        }

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Nova conversa em desenvolvimento", Snackbar.LENGTH_LONG)
                .setAction("OK", null)
                .setAnchorView(R.id.fab).show()
        }
    }
    
    private fun setupRecyclerView() {
        conversasAdapter = ConversasAdapter { conversa ->
            abrirConversa(conversa)
        }
        
        contentBinding.rvConversas.apply {
            adapter = conversasAdapter
            addItemDecoration(
                DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
            )
        }
    }
    
    private fun setupListeners() {
        contentBinding.btnTentarNovamente.setOnClickListener {
//            lifecycleScope.launch {
////                carregarConversas()
//            }
        }
    }
    
    private suspend fun carregarConfiguracoes() {
        // Carrega URL da API
        val apiUrl = userPreferences.apiUrl.first()
        if (!apiUrl.isNullOrEmpty()) {
            RetrofitClient.setBaseUrl(apiUrl)
        }
        
        // Carrega nome do usuário
        val userName = userPreferences.userName.first()
        supportActionBar?.title = "Olá, $userName!"
    }
    
    private suspend fun carregarConversas() {
        try {
            mostrarLoading(true)
            
            val token = userPreferences.authToken.first()
            
            if (token.isNullOrEmpty()) {
                mostrarErro("Token não encontrado. Faça login novamente.")
                return
            }
            
            val response = RetrofitClient.api.listarConversas("Bearer $token")
            
            if (response.isSuccessful && response.body() != null) {
                // val conversas = response.body()!!.conversas
                val conversas = response.body()!!
                
                if (conversas.isEmpty()) {
                    mostrarVazio()
                } else {
                    mostrarConversas(conversas)

                    Toast.makeText(
                        this@MainActivity,
                        conversas.count().toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> {
                        // Token expirado
                        Toast.makeText(this, "Sessão expirada", Toast.LENGTH_SHORT).show()
                        realizarLogout()
                        "Sessão expirada"
                    }
                    404 -> "Endpoint não encontrado"
                    500 -> "Erro no servidor"
                    else -> "Erro ao carregar conversas: ${response.code()}"
                }
                mostrarErro(errorMessage)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            mostrarErro("Erro de conexão: ${e.message}")

            Toast.makeText(
                this@MainActivity,
                e.message,
                Toast.LENGTH_LONG
            ).show()
        } finally {
            mostrarLoading(false)
        }
    }
    
    private fun mostrarLoading(mostrar: Boolean) {
        contentBinding.apply {
            progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
            rvConversas.visibility = if (mostrar) View.GONE else View.VISIBLE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.GONE
        }
    }
    
    private fun mostrarConversas(conversas: List<Conversa>) {
        contentBinding.apply {
            progressBar.visibility = View.GONE
            rvConversas.visibility = View.VISIBLE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.GONE
        }
        
        conversasAdapter.submitList(conversas)
    }
    
    private fun mostrarVazio() {
        contentBinding.apply {
            progressBar.visibility = View.GONE
            rvConversas.visibility = View.GONE
            layoutVazio.visibility = View.VISIBLE
            layoutErro.visibility = View.GONE
        }
    }
    
    private fun mostrarErro(mensagem: String) {
        contentBinding.apply {
            progressBar.visibility = View.GONE
            rvConversas.visibility = View.GONE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.VISIBLE
            tvMensagemErro.text = mensagem
        }
    }
    
    private fun abrirConversa(conversa: Conversa) {
        Toast.makeText(
            this,
            "Abrindo conversa: ${conversa.nome}",
            Toast.LENGTH_SHORT
        ).show()
        
        // TODO: Implementar tela de chat
        // val intent = Intent(this, ChatActivity::class.java)
        // intent.putExtra("conversa_id", conversa.id)
        // intent.putExtra("conversa_nome", conversa.nome)
        // startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, ConfigApiActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_logout -> {
                realizarLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun realizarLogout() {
        lifecycleScope.launch {
            userPreferences.clear()
            
            val intent = Intent(this@MainActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            lifecycleScope.launch {
                carregarConversas() // ✅ Só recarrega da segunda vez em diante
            }
        }
        isFirstLoad = false
    }
}
