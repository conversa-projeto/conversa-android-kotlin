package com.conversa.conversa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.adapter.ContatosAdapter
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.AdicionarUsuarioRequest
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.data.model.CriarConversaRequest
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityContatosBinding
import com.conversa.conversa.service.ChamadaService
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.ui.chat.ChatActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ContatosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContatosBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var adapter: ContatosAdapter
    private var listaContatos: List<Contato> = emptyList()
    private var contatoParaChamar: Contato? = null

    private val solicitarPermissaoMicrofoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contatoParaChamar?.let { iniciarChamada(it) }
        } else {
            Toast.makeText(
                this,
                "Permissão de microfone necessária para chamadas",
                Toast.LENGTH_SHORT
            ).show()
        }
        contatoParaChamar = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContatosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            carregarContatos()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContatosAdapter(
            onChatClick = { contato ->
                lifecycleScope.launch {
                    iniciarConversa(contato)
                }
            },
            onChamadaClick = { contato ->
                verificarPermissoesEIniciarChamada(contato)
            }
        )

        binding.rvContatos.adapter = adapter
    }

    private fun verificarPermissoesEIniciarChamada(contato: Contato) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            contatoParaChamar = contato
            solicitarPermissaoMicrofoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            iniciarChamada(contato)
        }
    }

    private fun iniciarChamada(contato: Contato) {
        Toast.makeText(this, "Iniciando chamada com ${contato.nome}...", Toast.LENGTH_SHORT).show()

        // Iniciar ChamadaService
        val serviceIntent = Intent(this, ChamadaService::class.java).apply {
            action = ChamadaService.ACTION_INICIAR_CHAMADA
            putExtra(ChamadaService.EXTRA_DESTINATARIOS, intArrayOf(contato.id))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Abrir ChamadaActivity
        val activityIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, contato.nome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, false)
        }
        startActivity(activityIntent)
    }

    private fun setupListeners() {
        binding.etPesquisar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }
    }

    private suspend fun carregarContatos() {
        try {
            mostrarLoading(true)

            val token = userPreferences.authToken.first()

            if (token.isNullOrEmpty()) {
                mostrarErro()
                return
            }

            val response = RetrofitClient.api.listarContatos("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                listaContatos = response.body()!!

                if (listaContatos.isEmpty()) {
                    mostrarVazio()
                } else {
                    mostrarContatos()
                }
            } else {
                mostrarErro()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            mostrarErro()
        }
    }

    private suspend fun iniciarConversa(contato: Contato) {
        try {
            val token = userPreferences.authToken.first() ?: return
            val userId = userPreferences.userId.first() ?: return

            // Verifica se já existe conversa com esse contato
            val conversasResponse = RetrofitClient.api.listarConversas("Bearer $token")
            if (conversasResponse.isSuccessful && conversasResponse.body() != null) {
                val conversaExistente = conversasResponse.body()!!.find { conversa ->
                    conversa.tipo == 1 && conversa.destinatario_id == contato.id
                }

                if (conversaExistente != null) {
                    abrirConversa(conversaExistente, contato.id)
                    return
                }
            }

            // Se não existe, não cria ainda - apenas abre o chat com ID temporário
            // O ChatActivity criará a conversa ao enviar a primeira mensagem
            abrirChatTemporario(contato)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirChatTemporario(contato: Contato) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("conversa_id", 0) // ID temporário
        intent.putExtra("conversa_nome", contato.nome)
        intent.putExtra("conversa_tipo", 1)
        intent.putExtra("destinatario_id", contato.id) // Passa ID do contato
        intent.putExtra("criar_conversa", true) // Flag para criar conversa
        startActivity(intent)
        finish()
    }

    private fun abrirConversa(conversa: Conversa, destinatarioId: Int) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("conversa_id", conversa.id)
        intent.putExtra("conversa_nome", conversa.nome ?: conversa.descricao)
        intent.putExtra("conversa_tipo", conversa.tipo)
        intent.putExtra("destinatario_id", destinatarioId)
        startActivity(intent)
        finish()
    }

    private fun mostrarLoading(mostrar: Boolean) {
        binding.progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.GONE
    }

    private fun mostrarContatos() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.VISIBLE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.GONE

        adapter.setListaCompleta(listaContatos)
    }

    private fun mostrarVazio() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.VISIBLE
        binding.tvErro.visibility = View.GONE
    }

    private fun mostrarErro() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.VISIBLE
    }
}
