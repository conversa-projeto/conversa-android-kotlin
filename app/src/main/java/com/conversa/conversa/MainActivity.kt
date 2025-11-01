package com.conversa.conversa

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.conversa.conversa.adapter.ContatosAdapter
import com.conversa.conversa.adapter.ConversasAdapter
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.AdicionarUsuarioRequest
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.CriarConversaRequest
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityMainBinding
import com.conversa.conversa.databinding.ContentMainBinding
import com.conversa.conversa.databinding.DialogContatosBinding
import com.conversa.conversa.ui.chat.ChatActivity
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private var isFirstLoad = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var contentBinding: ContentMainBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var conversasAdapter: ConversasAdapter
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        contentBinding = ContentMainBinding.bind(binding.contentMain.root)
        userPreferences = UserPreferences(this)

        setSupportActionBar(binding.toolbar)
        
        // Tratamento moderno do botão voltar
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
        
        setupNavigationDrawer()
        setupRecyclerView()
        setupListeners()
        
        lifecycleScope.launch {
            carregarConfiguracoes()
            carregarConversas()
        }

        binding.fab.setOnClickListener {
            val intent = Intent(this, ContatosActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        binding.navView.setNavigationItemSelectedListener(this)
        
        // Atualizar header com dados do usuário
        lifecycleScope.launch {
            val headerView = binding.navView.getHeaderView(0)
            val tvNome = headerView.findViewById<TextView>(R.id.tvNomeUsuario)
            val tvStatus = headerView.findViewById<TextView>(R.id.tvStatusUsuario)
            
            val userName = userPreferences.userName.first()
            tvNome.text = userName ?: "Usuário"
            tvStatus.text = "Online"
        }
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_contatos -> {
                val intent = Intent(this, ContatosActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_criar_grupo -> {
                val intent = Intent(this, CriarGrupoActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_configuracoes -> {
                val intent = Intent(this, ConfigApiActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_sair -> {
                realizarLogout()
            }
        }
        
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
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
            lifecycleScope.launch {
                carregarConversas()
            }
        }
    }
    
    private suspend fun carregarConfiguracoes() {
        val apiUrl = userPreferences.apiUrl.first()
        if (!apiUrl.isNullOrEmpty()) {
            RetrofitClient.setBaseUrl(apiUrl)
        }
        
        val userName = userPreferences.userName.first()
        supportActionBar?.title = "Conversa"
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
                val conversas = response.body()!!
                
                if (conversas.isEmpty()) {
                    mostrarVazio()
                } else {
                    mostrarConversas(conversas)
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> {
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
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
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
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("conversa_id", conversa.id)
        intent.putExtra("conversa_nome", conversa.nome ?: conversa.descricao)
        intent.putExtra("conversa_tipo", conversa.tipo)
        startActivity(intent)
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
                carregarConversas()
            }
        }
        isFirstLoad = false
    }
}
