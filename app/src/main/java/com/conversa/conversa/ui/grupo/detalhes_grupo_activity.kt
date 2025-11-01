package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.AdicionarUsuarioRequest
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.CriarConversaRequest
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityDetalhesGrupoBinding
import com.conversa.conversa.ui.chat.ChatActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DetalhesGrupoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDetalhesGrupoBinding
    private lateinit var userPreferences: UserPreferences
    private var contatosIds: List<Int> = emptyList()
    private var contatosSelecionados: List<Contato> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityDetalhesGrupoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        
        contatosIds = intent.getIntegerArrayListExtra("contatos_ids") ?: emptyList()
        
        setupToolbar()
        setupListeners()
        
        lifecycleScope.launch {
            carregarContatos()
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            voltarParaSelecao()
        }
    }
    
    private fun setupListeners() {
        binding.etNomeGrupo.addTextChangedListener { text ->
            binding.btnCriarGrupo.isEnabled = !text.isNullOrEmpty()
        }
        
        binding.btnVoltar.setOnClickListener {
            voltarParaSelecao()
        }
        
        binding.btnCriarGrupo.setOnClickListener {
            val nomeGrupo = binding.etNomeGrupo.text.toString().trim()
            val descricao = binding.etDescricaoGrupo.text.toString().trim()
            
            if (nomeGrupo.isEmpty()) {
                Toast.makeText(this, "Digite um nome para o grupo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                criarGrupo(nomeGrupo, descricao)
            }
        }
    }
    
    private suspend fun carregarContatos() {
        try {
            val token = userPreferences.authToken.first() ?: return
            val response = RetrofitClient.api.listarContatos("Bearer $token")
            
            if (response.isSuccessful && response.body() != null) {
                val todosContatos = response.body()!!
                contatosSelecionados = todosContatos.filter { it.id in contatosIds }
                
                atualizarMembros()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun atualizarMembros() {
        val nomesMembros = contatosSelecionados.joinToString(", ") { it.nome }
        binding.tvMembros.text = "Você + ${contatosSelecionados.size} outros\n$nomesMembros"
    }
    
    private suspend fun criarGrupo(nome: String, descricao: String) {
        try {
            Toast.makeText(this, "Criando grupo...", Toast.LENGTH_SHORT).show()
            
            // Desabilita botão para evitar duplo clique
            binding.btnCriarGrupo.isEnabled = false
            
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
                    AdicionarUsuarioRequest(conversa_id = grupoCriado.id, usuario_id = userId)
                )
                
                // Adiciona membros
                contatosSelecionados.forEach { contato ->
                    RetrofitClient.api.adicionarUsuarioConversa(
                        "Bearer $token",
                        AdicionarUsuarioRequest(conversa_id = grupoCriado.id, usuario_id = contato.id)
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
            } else {
                Toast.makeText(this, "Erro ao criar grupo", Toast.LENGTH_SHORT).show()
                binding.btnCriarGrupo.isEnabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.btnCriarGrupo.isEnabled = true
        }
    }
    
    private fun abrirConversa(conversa: Conversa) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("conversa_id", conversa.id)
        intent.putExtra("conversa_nome", conversa.nome ?: conversa.descricao)
        intent.putExtra("conversa_tipo", conversa.tipo)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
    
    private fun voltarParaSelecao() {
        val intent = Intent(this, CriarGrupoActivity::class.java)
        startActivity(intent)
        finish()
    }
}
