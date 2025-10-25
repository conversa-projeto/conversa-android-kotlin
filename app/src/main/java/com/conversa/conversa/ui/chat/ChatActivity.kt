package com.conversa.conversa.ui.chat

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ConteudoRequest
import com.conversa.conversa.data.model.EnviarMensagemRequest
import com.conversa.conversa.data.model.Mensagem
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityChatBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var mensagensAdapter: MensagensAdapter
    
    private var conversaId: Int = 0
    private var conversaNome: String = ""
    private var conversaTipo: Int = 1 // 1 = Chat 1:1, 2 = Grupo
    private var usuarioId: Int = 0
    private var authToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        
        // Pega dados da conversa
        conversaId = intent.getIntExtra("conversa_id", 0)
        conversaNome = intent.getStringExtra("conversa_nome") ?: "Chat"
        conversaTipo = intent.getIntExtra("conversa_tipo", 1)
        
        if (conversaId == 0) {
            Toast.makeText(this, "Erro: ID da conversa inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        
        lifecycleScope.launch {
            carregarDadosUsuario()
            carregarMensagens()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = conversaNome
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false) // Remove título padrão
            binding.tvNomeUsuario.text = conversaNome // ou o objeto que você usa
            binding.tvStatusUsuario.text = "online" // ou destinatario.status
        }
    }

    private fun onDownloadClick(conteudoId: Int, nomeArquivo: String, extensao: String) {
        Toast.makeText(this, "Erro: Token não encontrado", Toast.LENGTH_SHORT).show()
        return
    }

    private fun setupRecyclerView() {
        // Inicializa com 0, será atualizado após carregar dados do usuário
        mensagensAdapter = MensagensAdapter(0, false, ::onDownloadClick)
        
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // Começa do final (mensagens mais recentes)
        
        binding.rvMensagens.apply {
            adapter = mensagensAdapter
            this.layoutManager = layoutManager
        }
    }

    private fun setupListeners() {
        binding.btnEnviar.setOnClickListener {
            enviarMensagem()
        }
        
        binding.etMensagem.setOnEditorActionListener { _, _, _ ->
            enviarMensagem()
            true
        }
    }

    private suspend fun carregarDadosUsuario() {
        usuarioId = userPreferences.userId.first() ?: 0
        authToken = userPreferences.authToken.first() ?: ""

        // Atualiza adapter com o ID correto e tipo de conversa
        val isGrupo = conversaTipo == 2
        mensagensAdapter = MensagensAdapter(usuarioId, isGrupo, ::onDownloadClick)
        binding.rvMensagens.adapter = mensagensAdapter
    }

    private suspend fun carregarMensagens() {
        try {
            mostrarLoading(true)

            if (authToken.isEmpty()) {
                Toast.makeText(this, "Erro: Token não encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            
            val response = RetrofitClient.api.obterMensagens(
                token = "Bearer $authToken",
                conversaId = conversaId,
                mensagemReferencia = 0,
                mensagensPrevias = 50,
                mensagensSeguintes = 0
            )
            
            if (response.isSuccessful && response.body() != null) {
                val mensagens = response.body()!!
                mostrarMensagens(mensagens)
                
                // Marca mensagens como visualizadas
                marcarMensagensComoVisualizadas(mensagens)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Sessão expirada"
                    404 -> "Conversa não encontrada"
                    500 -> "Erro no servidor"
                    else -> "Erro ao carregar mensagens: ${response.code()}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Erro de conexão: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            mostrarLoading(false)
        }
    }

    private fun mostrarLoading(mostrar: Boolean) {
        binding.progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
        binding.rvMensagens.visibility = if (mostrar) View.GONE else View.VISIBLE
    }

    private fun mostrarMensagens(mensagens: List<Mensagem>) {
        mensagensAdapter.submitList(mensagens)
        
        // Scroll para a última mensagem
        if (mensagens.isNotEmpty()) {
            binding.rvMensagens.scrollToPosition(mensagens.size - 1)
        }
    }

    private fun enviarMensagem() {
        val textoMensagem = binding.etMensagem.text.toString().trim()
        
        if (textoMensagem.isEmpty()) {
            return
        }
        
        // Desabilita input enquanto envia
        binding.etMensagem.isEnabled = false
        binding.btnEnviar.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val request = EnviarMensagemRequest(
                    conversaId = conversaId,
                    conteudos = listOf(
                        ConteudoRequest(
                            tipo = 1, // Texto
                            ordem = 1,
                            conteudo = textoMensagem
                        )
                    )
                )
                
                val response = RetrofitClient.api.enviarMensagem(
                    token = "Bearer $authToken",
                    mensagem = request
                )
                
                if (response.isSuccessful) {
                    // Limpa o campo de texto
                    binding.etMensagem.setText("")
                    
                    // Recarrega mensagens
                    carregarMensagens()
                } else {
                    Toast.makeText(
                        this@ChatActivity,
                        "Erro ao enviar mensagem: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ChatActivity,
                    "Erro ao enviar: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                // Reabilita input
                binding.etMensagem.isEnabled = true
                binding.btnEnviar.isEnabled = true
            }
        }
    }

    private suspend fun marcarMensagensComoVisualizadas(mensagens: List<Mensagem>) {
        try {
            // Marca apenas as mensagens não visualizadas de outros usuários
            val mensagensNaoVisualizadas = mensagens.filter { 
                it.usuarioId != usuarioId && !it.visualizada
            }
            
            mensagensNaoVisualizadas.forEach { mensagem ->
                RetrofitClient.api.visualizarMensagem(
                    token = "Bearer $authToken",
                    conversaId = conversaId,
                    mensagemId = mensagem.id
                )
            }
        } catch (e: Exception) {
            // Silenciosamente falha - não é crítico
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
