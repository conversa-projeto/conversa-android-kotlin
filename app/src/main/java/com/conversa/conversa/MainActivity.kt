package com.conversa.conversa

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.conversa.conversa.adapter.ContatosAdapter
import com.conversa.conversa.adapter.ContatosSelecionaveisAdapter
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
import com.conversa.conversa.databinding.DialogCriarGrupoBinding
import com.conversa.conversa.databinding.DialogDetalhesGrupoBinding
import com.conversa.conversa.ui.chat.ChatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private var isFirstLoad = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var contentBinding: ContentMainBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var conversasAdapter: ConversasAdapter
    
    private var listaContatos: List<Contato> = emptyList()
    private var contatosSelecionados: List<Contato> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        contentBinding = ContentMainBinding.bind(binding.contentMain.root)
        userPreferences = UserPreferences(this)

        setSupportActionBar(binding.toolbar)
        
        setupRecyclerView()
        setupListeners()
        
        lifecycleScope.launch {
            carregarConfiguracoes()
            carregarConversas()
        }

        binding.fab.setOnClickListener {
            mostrarDialogContatos()
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
    
    private suspend fun carregarContatos(): List<Contato> {
        return try {
            val token = userPreferences.authToken.first() ?: return emptyList()
            val response = RetrofitClient.api.listarContatos("Bearer $token")
            
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // ========== DIALOG CONTATOS (1:1) ==========
    
    private fun mostrarDialogContatos() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogContatosBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
        
        val adapter = ContatosAdapter { contato ->
            dialog.dismiss()
            lifecycleScope.launch {
                iniciarOuAbrirConversa(contato)
            }
        }
        
        dialogBinding.rvContatos.adapter = adapter
        
        lifecycleScope.launch {
            dialogBinding.progressBar.visibility = View.VISIBLE
            listaContatos = carregarContatos()
            
            if (listaContatos.isEmpty()) {
                dialogBinding.progressBar.visibility = View.GONE
                dialogBinding.tvVazio.visibility = View.VISIBLE
            } else {
                dialogBinding.progressBar.visibility = View.GONE
                dialogBinding.rvContatos.visibility = View.VISIBLE
                adapter.setListaCompleta(listaContatos)
            }
        }
        
        dialogBinding.etPesquisar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }
        
        dialogBinding.btnCancelar.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private suspend fun iniciarOuAbrirConversa(contato: Contato) {
        val userId = userPreferences.userId.first() ?: return
        
        // Busca conversa existente
        val conversaExistente = conversasAdapter.currentList.find { conversa ->
            conversa.tipo == 1 && conversa.destinatario_id == contato.id
        }
        
        if (conversaExistente != null) {
            abrirConversa(conversaExistente)
        } else {
            criarNovaConversa(contato, userId)
        }
    }
    
    private suspend fun criarNovaConversa(contato: Contato, userId: Int) {
        try {
            Toast.makeText(this, "Criando conversa...", Toast.LENGTH_SHORT).show()
            
            val token = userPreferences.authToken.first() ?: return
            val dataAtual = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            
            val request = CriarConversaRequest(
                descricao = "",
                tipo = 1,
                inserida = dataAtual
            )
            
            val response = RetrofitClient.api.criarConversa("Bearer $token", request)
            
            if (response.isSuccessful && response.body() != null) {
                val conversaCriada = response.body()!!
                
                // Adiciona usuário atual
                RetrofitClient.api.adicionarUsuarioConversa(
                    "Bearer $token",
                    AdicionarUsuarioRequest(conversaCriada.id, userId)
                )
                
                // Adiciona contato
                RetrofitClient.api.adicionarUsuarioConversa(
                    "Bearer $token",
                    AdicionarUsuarioRequest(conversaCriada.id, contato.id)
                )
                
                // Abre a conversa
                val conversa = Conversa(
                    id = conversaCriada.id,
                    descricao = contato.nome,
                    tipo = 1,
                    inserida = conversaCriada.inserida,
                    nome = contato.nome,
                    destinatario_id = contato.id,
                    mensagem_id = 0,
                    ultima_mensagem = null,
                    ultima_mensagem_texto = null,
                    mensagens_sem_visualizar = 0
                )
                
                abrirConversa(conversa)
                carregarConversas()
            } else {
                Toast.makeText(this, "Erro ao criar conversa", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ========== DIALOG CRIAR GRUPO ==========
    
    private fun mostrarDialogCriarGrupo() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogCriarGrupoBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.75).toInt()
        )
        
        val adapter = ContatosSelecionaveisAdapter { quantidade ->
            dialogBinding.tvContador.text = "$quantidade selecionados"
            dialogBinding.btnAvancar.isEnabled = true //quantidade >= 2
        }
        
        dialogBinding.rvContatos.adapter = adapter
        
        lifecycleScope.launch {
            dialogBinding.progressBar.visibility = View.VISIBLE
            listaContatos = carregarContatos()
            
            if (listaContatos.isEmpty()) {
                dialogBinding.progressBar.visibility = View.GONE
                dialogBinding.tvVazio.visibility = View.VISIBLE
            } else {
                dialogBinding.progressBar.visibility = View.GONE
                dialogBinding.rvContatos.visibility = View.VISIBLE
                adapter.setListaCompleta(listaContatos)
            }
        }
        
        dialogBinding.etPesquisar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }
        
        dialogBinding.btnCancelar.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnAvancar.setOnClickListener {
            contatosSelecionados = adapter.getContatosSelecionados()
            dialog.dismiss()
            mostrarDialogDetalhesGrupo()
        }
        
        dialog.show()
    }
    
    // ========== DIALOG DETALHES GRUPO ==========
    
    private fun mostrarDialogDetalhesGrupo() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogDetalhesGrupoBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.8).toInt(),
            resources.displayMetrics.heightPixels
        )
        
        val nomesMembros = contatosSelecionados.joinToString(", ") { it.nome }
        dialogBinding.tvMembros.text = "Você + ${contatosSelecionados.size} outros\n$nomesMembros"
        
        dialogBinding.etNomeGrupo.addTextChangedListener { text ->
            dialogBinding.btnCriarGrupo.isEnabled = !text.isNullOrEmpty()
        }
        
        dialogBinding.btnVoltar.setOnClickListener {
            dialog.dismiss()
            mostrarDialogCriarGrupo()
        }
        
        dialogBinding.btnCriarGrupo.setOnClickListener {
            val nomeGrupo = dialogBinding.etNomeGrupo.text.toString().trim()
            val descricao = dialogBinding.etDescricaoGrupo.text.toString().trim()
            
            if (nomeGrupo.isEmpty()) {
                Toast.makeText(this, "Digite um nome para o grupo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            dialog.dismiss()
            lifecycleScope.launch {
                criarGrupo(nomeGrupo, descricao)
            }
        }
        
        dialog.show()
    }
    
    private suspend fun criarGrupo(nome: String, descricao: String) {
        try {
            Toast.makeText(this, "Criando grupo...", Toast.LENGTH_SHORT).show()
            
            val token = userPreferences.authToken.first() ?: return
            val userId = userPreferences.userId.first() ?: return
            val dataAtual = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            
            val request = CriarConversaRequest(
                descricao = nome,
                tipo = 2,
                inserida = dataAtual
            )
            
            val response = RetrofitClient.api.criarConversa("Bearer $token", request)
            
            if (response.isSuccessful && response.body() != null) {
                val grupoCriado = response.body()!!
                
                // Adiciona criador
                RetrofitClient.api.adicionarUsuarioConversa(
                    "Bearer $token",
                    AdicionarUsuarioRequest(grupoCriado.id, userId)
                )
                
                // Adiciona membros
                contatosSelecionados.forEach { contato ->
                    RetrofitClient.api.adicionarUsuarioConversa(
                        "Bearer $token",
                        AdicionarUsuarioRequest(grupoCriado.id, contato.id)
                    )
                }
                
                val grupo = Conversa(
                    id = grupoCriado.id,
                    descricao = nome,
                    tipo = 2,
                    inserida = grupoCriado.inserida,
                    nome = nome,
                    destinatario_id = null,
                    mensagem_id = 0,
                    ultima_mensagem = null,
                    ultima_mensagem_texto = null,
                    mensagens_sem_visualizar = 0
                )
                
                Toast.makeText(this, "Grupo criado com sucesso!", Toast.LENGTH_SHORT).show()
                abrirConversa(grupo)
                carregarConversas()
            } else {
                Toast.makeText(this, "Erro ao criar grupo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ========== HELPERS ==========
    
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_contatos -> {
                mostrarDialogContatos()
                true
            }
            R.id.action_novo_grupo -> {
                mostrarDialogCriarGrupo()
                true
            }
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
                carregarConversas()
            }
        }
        isFirstLoad = false
    }
}
