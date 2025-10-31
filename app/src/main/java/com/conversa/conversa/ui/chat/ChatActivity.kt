package com.conversa.conversa.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.conversa.conversa.data.api.DownloadHelper
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.api.UploadHelper
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
    private lateinit var audioPlayerHelper: AudioPlayerHelper
    private lateinit var downloadHelper: DownloadHelper
    private lateinit var uploadHelper: UploadHelper
    
    private var conversaId: Int = 0
    private var conversaNome: String = ""
    private var conversaTipo: Int = 1 // 1 = Chat 1:1, 2 = Grupo
    private var usuarioId: Int = 0
    private var authToken: String = ""
    private var apiUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        audioPlayerHelper = AudioPlayerHelper(this)
        downloadHelper = DownloadHelper(this)
        uploadHelper = UploadHelper(this)
        
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
            setDisplayShowTitleEnabled(false)
            binding.tvNomeUsuario.text = conversaNome
            binding.tvStatusUsuario.text = "online"
        }
    }

    private fun onDownloadClick(conteudoId: String, nomeArquivo: String, extensao: String) {
        if (authToken.isEmpty()) {
            Toast.makeText(this, "Erro: Token não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            val result = downloadHelper.downloadAnexo(
                conteudoId = conteudoId,
                nomeArquivo = nomeArquivo,
                extensao = extensao,
                authToken = authToken
            )
            
            result.onSuccess { file ->
                Toast.makeText(
                    this@ChatActivity,
                    "Download concluído: $nomeArquivo.$extensao",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { exception ->
                Toast.makeText(
                    this@ChatActivity,
                    "Erro ao fazer download: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupRecyclerView() {
        // Será reinicializado após carregar dados do usuário
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        
        binding.rvMensagens.layoutManager = layoutManager
    }

    // Launcher para seleção de imagem
    private val selecionarImagemLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            enviarImagemSelecionada(it)
        }
    }

    // Launcher para permissão de galeria (Android 13+)
    private val solicitarPermissaoGaleriaLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            abrirSeletorImagem()
        } else {
            Toast.makeText(
                this,
                "Permissão negada. Não é possível acessar a galeria.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupListeners() {
        binding.btnEnviar.setOnClickListener {
            enviarMensagem()
        }
        
        binding.btnAnexar.setOnClickListener {
            verificarPermissaoEAbrirGaleria()
        }
        
        binding.etMensagem.setOnEditorActionListener { _, _, _ ->
            enviarMensagem()
            true
        }
    }

    /**
     * Verifica permissão e abre seletor de imagem
     */
    private fun verificarPermissaoEAbrirGaleria() {
        when {
            // Android 13+ requer READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        abrirSeletorImagem()
                    }
                    else -> {
                        solicitarPermissaoGaleriaLauncher.launch(
                            Manifest.permission.READ_MEDIA_IMAGES
                        )
                    }
                }
            }
            // Android 10-12 requer READ_EXTERNAL_STORAGE
            else -> {
                when {
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        abrirSeletorImagem()
                    }
                    else -> {
                        solicitarPermissaoGaleriaLauncher.launch(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                    }
                }
            }
        }
    }

    /**
     * Abre o seletor de imagem
     */
    private fun abrirSeletorImagem() {
        selecionarImagemLauncher.launch("image/*")
    }

    /**
     * Envia a imagem selecionada
     */
    private fun enviarImagemSelecionada(uri: Uri) {
        // Desabilita botões enquanto processa
        binding.btnAnexar.isEnabled = false
        binding.btnEnviar.isEnabled = false
        binding.etMensagem.isEnabled = false

        lifecycleScope.launch {
            try {
                mostrarLoading(true)

                // 1. Faz upload da imagem
                val uploadResult = uploadHelper.uploadImagem(uri, authToken)

                uploadResult.onSuccess { identificador ->
                    // 2. Envia mensagem com o identificador da imagem
                    val textoMensagem = binding.etMensagem.text.toString().trim()
                    val conteudos = mutableListOf<ConteudoRequest>()

                    // Adiciona imagem
                    conteudos.add(
                        ConteudoRequest(
                            tipo = 2, // Imagem
                            ordem = 1,
                            conteudo = identificador
                        )
                    )

                    // Adiciona texto se houver
                    if (textoMensagem.isNotEmpty()) {
                        conteudos.add(
                            ConteudoRequest(
                                tipo = 1, // Texto
                                ordem = 2,
                                conteudo = textoMensagem
                            )
                        )
                    }

                    val request = EnviarMensagemRequest(
                        conversaId = conversaId,
                        conteudos = conteudos
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

                        Toast.makeText(
                            this@ChatActivity,
                            "Imagem enviada com sucesso!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@ChatActivity,
                            "Erro ao enviar mensagem: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure { exception ->
                    Toast.makeText(
                        this@ChatActivity,
                        "Erro ao fazer upload: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ChatActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                mostrarLoading(false)
                // Reabilita botões
                binding.btnAnexar.isEnabled = true
                binding.btnEnviar.isEnabled = true
                binding.etMensagem.isEnabled = true
            }
        }
    }

    private suspend fun carregarDadosUsuario() {
        usuarioId = userPreferences.userId.first() ?: 0
        authToken = userPreferences.authToken.first() ?: ""
        apiUrl = userPreferences.apiUrl.first() ?: ""

        // Atualiza adapter com os dados corretos
        val isGrupo = conversaTipo == 2
        mensagensAdapter = MensagensAdapter(
            usuarioId = usuarioId,
            isGrupo = isGrupo,
            apiUrl = apiUrl,
            authToken = authToken,
            audioPlayerHelper = audioPlayerHelper,
            onDownloadClick = ::onDownloadClick
        )
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

    override fun onDestroy() {
        super.onDestroy()
        // Libera recursos do player de áudio
        audioPlayerHelper.release()
    }
}
