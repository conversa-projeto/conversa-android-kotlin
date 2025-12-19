package com.conversa.conversa

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.conversa.conversa.service.SocketService
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.ui.chat.ChatActivity
import com.conversa.conversa.utils.ChamadaBroadcast
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity(), 
    NavigationView.OnNavigationItemSelectedListener,
    ChamadaBroadcast.ChamadaListener {
    private var isFirstLoad = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var contentBinding: ContentMainBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var conversasAdapter: ConversasAdapter
    private lateinit var toggle: ActionBarDrawerToggle

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 101
        private const val REQUEST_POST_NOTIFICATIONS = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        contentBinding = ContentMainBinding.bind(binding.contentMain.root)
        userPreferences = UserPreferences(this)

        setSupportActionBar(binding.toolbar)
        
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
        
        // CRÍTICO: Solicita permissões necessárias
        verificarPermissoes()
        
        lifecycleScope.launch {
            carregarConfiguracoes()
            inicializarSocket() // Inicia IMEDIATAMENTE
            carregarConversas()
        }

        binding.fab.setOnClickListener {
            val intent = Intent(this, ContatosActivity::class.java)
            startActivity(intent)
        }
    }
    
    /**
     * Verifica e solicita permissões necessárias para chamadas
     */
    private fun verificarPermissoes() {
        val permissoesNecessarias = mutableListOf<String>()
        
        // Android 13+: POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissoesNecessarias.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Android 12+: USE_FULL_SCREEN_INTENT via NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            if (!notificationManager.canUseFullScreenIntent()) {
                android.util.Log.w("MainActivity", "USE_FULL_SCREEN_INTENT não permitido. Solicite ao usuário nas configurações.")
                // Não tem permissão runtime - usuário precisa habilitar manualmente
            }
        }
        
        if (permissoesNecessarias.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissoesNecessarias.toTypedArray(),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    android.util.Log.d("MainActivity", "Permissões de notificação concedidas")
                } else {
                    Toast.makeText(
                        this,
                        "Permissões necessárias para receber chamadas",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
        
        if (conversa.tipo == 1 && conversa.destinatario_id != null) {
            intent.putExtra("destinatario_id", conversa.destinatario_id)
        }
        
        startActivity(intent)
    }
    
    private fun realizarLogout() {
        lifecycleScope.launch {
            SocketService.stop(this@MainActivity)
            
            userPreferences.clear()
            
            val intent = Intent(this@MainActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    /**
     * CRÍTICO: Inicia SocketService assim que MainActivity abre
     * Service roda em background mesmo quando app fecha
     */
    private suspend fun inicializarSocket() {
        try {
            val apiUrl = userPreferences.apiUrl.first()
            
            if (!apiUrl.isNullOrEmpty()) {
                val host = apiUrl.replace("http://", "")
                                 .replace("https://", "")
                                 .split(":")[0]
                val port = 8090

                val token = userPreferences.authToken.first() ?: ""
                
                if (token.isNotEmpty()) {
                    // Inicia SocketService em foreground
                    SocketService.start(this, host, port, token)
                    
                    android.util.Log.d("MainActivity", "✅ SocketService iniciado: $host:$port")
                } else {
                    android.util.Log.e("MainActivity", "❌ Token vazio, não iniciou service")
                }
            } else {
                android.util.Log.e("MainActivity", "❌ API URL vazia, não iniciou service")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Erro ao inicializar SocketService", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Registra listener para receber chamadas quando app está aberto
        ChamadaBroadcast.addListener(this)
        
        if (!isFirstLoad) {
            lifecycleScope.launch {
                carregarConversas()
            }
        }
        isFirstLoad = false
    }
    
    override fun onPause() {
        super.onPause()
        // Remove listener quando sai da tela
        ChamadaBroadcast.removeListener(this)
    }
    
    /**
     * Callback quando chamada é recebida com app ABERTO
     */
    override fun onChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        android.util.Log.d("MainActivity", "📱 Chamada recebida via broadcast: $usuarioNome")
        
        // Abre ChamadaActivity por cima da MainActivity
        val intent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
        }
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // SocketService continua rodando em background
        android.util.Log.d("MainActivity", "MainActivity destruída, Service continua ativo")
    }
}
