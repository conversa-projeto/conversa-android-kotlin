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
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var userPreferences: UserPreferences
    
    companion object {
        private const val TAG = "ChatActivity"
    }
    private lateinit var mensagensAdapter: MensagensAdapter
    private lateinit var audioPlayerHelper: AudioPlayerHelper
    private lateinit var downloadHelper: DownloadHelper
    private lateinit var uploadHelper: UploadHelper
    private lateinit var audioRecorderHelper: AudioRecorderHelper
    
    private var gravandoAudio = false
    private var audioFile: File? = null
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    
    private var conversaId: Int = 0
    private var conversaNome: String = ""
    private var conversaTipo: Int = 1 // 1 = Chat 1:1, 2 = Grupo
    private var destinatarioId: Int? = null
    private var criarConversa: Boolean = false
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
        audioRecorderHelper = AudioRecorderHelper(this)
        
        // Pega dados da conversa
        conversaId = intent.getIntExtra("conversa_id", 0)
        conversaNome = intent.getStringExtra("conversa_nome") ?: "Chat"
        conversaTipo = intent.getIntExtra("conversa_tipo", 1)
        destinatarioId = intent.getIntExtra("destinatario_id", -1).takeIf { it != -1 }
        criarConversa = intent.getBooleanExtra("criar_conversa", false)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        
        lifecycleScope.launch {
            carregarDadosUsuario()
            
            // Só carrega mensagens se a conversa já existe
            if (conversaId > 0) {
                carregarMensagens()
            }
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

    // Launcher para permissão de microfone
    private val solicitarPermissaoMicrofoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            iniciarGravacaoAudio()
        } else {
            Toast.makeText(
                this,
                "Permissão de microfone negada. Não é possível gravar áudio.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupListeners() {
        binding.btnLigar.setOnClickListener {
            verificarPermissoesEIniciarChamada()
        }
        
        binding.btnEnviar.setOnClickListener {
            enviarMensagem()
        }
        
        binding.btnAnexar.setOnClickListener {
            verificarPermissaoEAbrirGaleria()
        }
        
        binding.btnMicrofone.setOnClickListener {
            verificarPermissaoEGravarAudio()
        }
        
        binding.btnCancelarAudio.setOnClickListener {
            cancelarGravacaoAudio()
        }
        
        binding.btnPararGravacao.setOnClickListener {
            pararEEnviarAudio()
        }
        
        binding.etMensagem.setOnEditorActionListener { _, _, _ ->
            enviarMensagem()
            true
        }
        
        // Mostra/esconde botão de enviar baseado no texto
        binding.etMensagem.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val temTexto = !s.isNullOrEmpty()
                binding.btnEnviar.visibility = if (temTexto) View.VISIBLE else View.GONE
                binding.btnMicrofone.visibility = if (temTexto) View.GONE else View.VISIBLE
            }
        })
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
                // Se precisa criar a conversa primeiro, cria
                if (criarConversa && conversaId == 0 && destinatarioId != null) {
                    val conversaCriada = criarNovaConversa()
                    if (conversaCriada) {
                        criarConversa = false // Marca como criada
                    } else {
                        binding.etMensagem.isEnabled = true
                        binding.btnEnviar.isEnabled = true
                        return@launch
                    }
                }
                
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
    
    private suspend fun criarNovaConversa(): Boolean {
        return try {
            val dataAtual = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
            
            // 1. Cria a conversa
            val request = com.conversa.conversa.data.model.CriarConversaRequest(
                descricao = "",
                tipo = 1,
                inserida = dataAtual
            )
            
            val response = RetrofitClient.api.criarConversa("Bearer $authToken", request)
            
            if (!response.isSuccessful || response.body() == null) {
                Toast.makeText(
                    this,
                    "Erro ao criar conversa",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
            
            val conversaCriada = response.body()!!
            conversaId = conversaCriada.id
            
            // 2. Adiciona o usuário atual
            RetrofitClient.api.adicionarUsuarioConversa(
                "Bearer $authToken",
                com.conversa.conversa.data.model.AdicionarUsuarioRequest(
                    conversa_id = conversaId,
                    usuario_id = usuarioId
                )
            )
            
            // 3. Adiciona o destinatário
            destinatarioId?.let { destId ->
                RetrofitClient.api.adicionarUsuarioConversa(
                    "Bearer $authToken",
                    com.conversa.conversa.data.model.AdicionarUsuarioRequest(
                        conversa_id = conversaId,
                        usuario_id = destId
                    )
                )
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Erro ao criar conversa: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            false
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
        // Libera recursos do gravador
        audioRecorderHelper.release()
        // Para o timer se estiver rodando
        pararTimer()
    }
    
    /**
     * Verifica permissão e inicia gravação de áudio
     */
    private fun verificarPermissaoEGravarAudio() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                iniciarGravacaoAudio()
            }
            else -> {
                solicitarPermissaoMicrofoneLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }
    }
    
    /**
     * Inicia a gravação de áudio
     */
    private fun iniciarGravacaoAudio() {
        audioFile = audioRecorderHelper.startRecording()
        
        if (audioFile == null) {
            Toast.makeText(
                this,
                "Erro ao iniciar gravação de áudio",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        gravandoAudio = true
        
        // Mostra layout de gravação
        binding.layoutInputNormal.visibility = View.GONE
        binding.layoutGravacaoAudio.visibility = View.VISIBLE
        
        // Inicia timer de duração
        iniciarTimer()
    }
    
    /**
     * Cancela a gravação de áudio
     */
    private fun cancelarGravacaoAudio() {
        audioRecorderHelper.cancelRecording()
        pararTimer()
        
        gravandoAudio = false
        audioFile = null
        
        // Volta ao layout normal
        binding.layoutGravacaoAudio.visibility = View.GONE
        binding.layoutInputNormal.visibility = View.VISIBLE
        
        Toast.makeText(
            this,
            "Gravação cancelada",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Para a gravação e envia o áudio
     */
    private fun pararEEnviarAudio() {
        val duracao = audioRecorderHelper.stopRecording()
        pararTimer()
        
        gravandoAudio = false
        
        // Volta ao layout normal
        binding.layoutGravacaoAudio.visibility = View.GONE
        binding.layoutInputNormal.visibility = View.VISIBLE
        
        // Verifica duração mínima (1 segundo)
        if (duracao < 1000) {
            audioFile?.delete()
            audioFile = null
            Toast.makeText(
                this,
                "Áudio muito curto. Grave por pelo menos 1 segundo.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Envia o áudio
        audioFile?.let { file ->
            enviarAudioGravado(file)
        }
    }
    
    /**
     * Envia o áudio gravado
     */
    private fun enviarAudioGravado(audioFile: File) {
        // Desabilita botões
        binding.btnAnexar.isEnabled = false
        binding.btnMicrofone.isEnabled = false
        binding.etMensagem.isEnabled = false
        
        lifecycleScope.launch {
            try {
                mostrarLoading(true)
                
                // 1. Faz upload do áudio
                val uploadResult = uploadHelper.uploadAudio(audioFile, authToken)
                
                uploadResult.onSuccess { identificador ->
                    // 2. Envia mensagem com o identificador do áudio
                    val request = EnviarMensagemRequest(
                        conversaId = conversaId,
                        conteudos = listOf(
                            ConteudoRequest(
                                tipo = 4, // Áudio
                                ordem = 1,
                                conteudo = identificador
                            )
                        )
                    )
                    
                    val response = RetrofitClient.api.enviarMensagem(
                        token = "Bearer $authToken",
                        mensagem = request
                    )
                    
                    if (response.isSuccessful) {
                        // Recarrega mensagens
                        carregarMensagens()
                        
                        Toast.makeText(
                            this@ChatActivity,
                            "Áudio enviado com sucesso!",
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
                binding.btnMicrofone.isEnabled = true
                binding.etMensagem.isEnabled = true
            }
        }
    }
    
    /**
     * Inicia o timer de duração da gravação
     */
    private fun iniciarTimer() {
        timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (gravandoAudio) {
                    val duracao = audioRecorderHelper.getCurrentDuration()
                    binding.tvDuracaoGravacao.text = audioRecorderHelper.formatDuration(duracao)
                    
                    // Limite de 5 minutos
                    if (duracao >= 5 * 60 * 1000) {
                        pararEEnviarAudio()
                        Toast.makeText(
                            this@ChatActivity,
                            "Limite de 5 minutos atingido",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        timerHandler?.postDelayed(this, 100)
                    }
                }
            }
        }
        timerHandler?.post(timerRunnable!!)
    }
    
    /**
     * Para o timer
     */
    private fun pararTimer() {
        timerRunnable?.let {
            timerHandler?.removeCallbacks(it)
        }
        timerHandler = null
        timerRunnable = null
    }
    
    /**
     * Verifica permissões e inicia chamada
     */
    private fun verificarPermissoesEIniciarChamada() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            solicitarPermissaoMicrofoneLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
            // Quando a permissão for concedida, iniciarChamada() será chamado
        } else {
            iniciarChamada()
        }
    }
    
    /**
     * Inicia uma chamada
     */
    private fun iniciarChamada() {
        if (destinatarioId == null || destinatarioId == -1) {
            Toast.makeText(
                this,
                "Não foi possível identificar o destinatário",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@ChatActivity,
                    "Iniciando chamada...",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Usa SocketManager compartilhado do Service
                val socketManager = com.conversa.conversa.ui.chamada.ChamadaIncomingActivity.sharedSocketManager
                
                if (socketManager == null) {
                    Toast.makeText(
                        this@ChatActivity,
                        "Erro: serviço não disponível",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                // Criar repository de chamada
                val repository = com.conversa.conversa.data.repository.ChamadaRepository(
                    context = this@ChatActivity,
                    api = RetrofitClient.api,
                    chamadaManager = com.conversa.conversa.data.chamada.ChamadaManager(this@ChatActivity),
                    socketManager = socketManager,
                    userPreferences = userPreferences
                )
                
                // Iniciar chamada
                val result = repository.iniciarChamada(listOf(destinatarioId!!))
                
                result.onSuccess { chamada ->
                    val intent = android.content.Intent(this@ChatActivity, com.conversa.conversa.ui.chamada.ChamadaActivity::class.java).apply {
                        putExtra(com.conversa.conversa.ui.chamada.ChamadaActivity.EXTRA_CHAMADA_ID, chamada.id)
                        putExtra(com.conversa.conversa.ui.chamada.ChamadaActivity.EXTRA_USUARIO_NOME, conversaNome)
                        putExtra(com.conversa.conversa.ui.chamada.ChamadaActivity.EXTRA_IS_INICIADOR, true)
                    }
                    startActivity(intent)
                }
                
                result.onFailure { erro ->
                    android.util.Log.e(TAG, "Erro ao iniciar chamada", erro)
                    Toast.makeText(
                        this@ChatActivity,
                        "Erro ao iniciar chamada: ${erro.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao iniciar chamada", e)
                Toast.makeText(
                    this@ChatActivity,
                    "Erro ao iniciar chamada: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
