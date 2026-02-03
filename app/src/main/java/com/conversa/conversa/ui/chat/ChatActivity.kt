package com.conversa.conversa.ui.chat

import android.Manifest
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
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
import com.conversa.conversa.service.SocketService
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.ui.chamada.ChamadaNavigator
import com.conversa.conversa.ui.chamada.components.setupCallBanner
import com.conversa.conversa.utils.ChamadaServiceObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatActivity : AppCompatActivity(), SocketService.CallListener {

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

    // Repository de chamada (para limpar quando a chamada terminar)
    private var chamadaRepository: com.conversa.conversa.data.repository.ChamadaRepository? = null

    // Observer para ChamadaService (exibe CallBanner)
    private lateinit var chamadaObserver: ChamadaServiceObserver

    // Conexão com SocketService para receber mensagens em tempo real
    private var socketService: SocketService? = null
    private var socketBound = false
    
    private val socketConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SocketService.LocalBinder
            socketService = binder.getService()
            socketService?.setCallListener(this@ChatActivity)
            socketBound = true
            Log.d(TAG, "SocketService vinculado à ChatActivity")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            socketService = null
            socketBound = false
            Log.d(TAG, "SocketService desvinculado da ChatActivity")
        }
    }

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
        setupCallBanner()
        
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
     * Configura o CallBanner para mostrar quando há chamada ativa
     */
    private fun setupCallBanner() {
        chamadaObserver = ChamadaServiceObserver(this)

        lifecycleScope.launch {
            val meuUsuarioId = userPreferences.userId.first() ?: 0
            binding.callBannerComposeView.setupCallBanner(
                chamadaObserver = chamadaObserver,
                meuUsuarioId = meuUsuarioId,
                onBannerClick = {
                    ChamadaNavigator.voltarParaChamadaAtiva(this@ChatActivity)
                }
            )
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
        
        // Limpa o campo de texto imediatamente
        binding.etMensagem.setText("")
        
        lifecycleScope.launch {
            try {
                // Se precisa criar a conversa primeiro, cria
                if (criarConversa && conversaId == 0 && destinatarioId != null) {
                    val conversaCriada = criarNovaConversa()
                    if (conversaCriada) {
                        criarConversa = false // Marca como criada
                    } else {
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
                    // Busca as mensagens atualizadas sem mostrar loading
                    atualizarMensagensSemLoading()
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
            }
        }
    }
    
    /**
     * Atualiza as mensagens sem mostrar loading (sem piscar a tela)
     */
    private suspend fun atualizarMensagensSemLoading() {
        try {
            val response = RetrofitClient.api.obterMensagens(
                token = "Bearer $authToken",
                conversaId = conversaId,
                mensagemReferencia = 0,
                mensagensPrevias = 50,
                mensagensSeguintes = 0
            )
            
            if (response.isSuccessful && response.body() != null) {
                val mensagens = response.body()!!
                
                withContext(Dispatchers.Main) {
                    // Atualiza a lista e faz scroll após a lista ser atualizada
                    mensagensAdapter.submitList(mensagens) {
                        // Este callback é executado após a lista ser atualizada
                        if (mensagens.isNotEmpty()) {
                            binding.rvMensagens.scrollToPosition(mensagens.size - 1)
                        }
                    }
                }
                
                // Marca mensagens como visualizadas
                marcarMensagensComoVisualizadas(mensagens)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar mensagens", e)
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
                onBackPressedDispatcher.onBackPressed()
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
        // Limpa repository de chamada se existir
        chamadaRepository?.cleanup()
        chamadaRepository = null
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
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@ChatActivity,
                    "Iniciando chamada...",
                    Toast.LENGTH_SHORT
                ).show()

                // Obter token de autenticação
                val token = userPreferences.authToken.first()
                if (token.isNullOrEmpty()) {
                    Toast.makeText(
                        this@ChatActivity,
                        "Erro: usuário não autenticado",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Construir lista de participantes
                val participantesIds = mutableListOf<Int>()

                // Se for conversa em grupo, buscar dados da conversa para obter todos os participantes
                if (conversaId > 0) {
                    android.util.Log.d(TAG, "Buscando dados da conversa $conversaId para obter participantes")

                    val response = RetrofitClient.api.obterDadosConversa(
                        token = "Bearer $token",
                        conversaId = conversaId
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val conversa = response.body()!!
                        android.util.Log.d(TAG, "Conversa obtida: ${conversa.nome}, ${conversa.usuarios.size} usuários")

                        // Adicionar todos os usuários da conversa (exceto o próprio usuário)
                        val meuId = userPreferences.userId.first()
                        conversa.usuarios
                            .filter { it.id != meuId }
                            .forEach { participantesIds.add(it.id) }

                        android.util.Log.d(TAG, "Participantes da chamada em grupo: $participantesIds")
                    } else {
                        android.util.Log.e(TAG, "Erro ao obter dados da conversa: ${response.code()}")
                        Toast.makeText(
                            this@ChatActivity,
                            "Erro ao obter dados da conversa",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                } else if (destinatarioId != null && destinatarioId != -1) {
                    // Se for conversa 1:1, usar apenas o destinatarioId
                    participantesIds.add(destinatarioId!!)
                    android.util.Log.d(TAG, "Chamada 1:1 com destinatário: $destinatarioId")
                }

                // Validar se tem participantes
                if (participantesIds.isEmpty()) {
                    Toast.makeText(
                        this@ChatActivity,
                        "Não foi possível identificar os participantes",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Iniciar ChamadaService e abrir ChamadaActivity
                val intent = android.content.Intent(this@ChatActivity, com.conversa.conversa.service.ChamadaService::class.java).apply {
                    action = com.conversa.conversa.service.ChamadaService.ACTION_INICIAR_CHAMADA
                    putExtra(com.conversa.conversa.service.ChamadaService.EXTRA_DESTINATARIOS, participantesIds.toIntArray())
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                // Abrir ChamadaActivity
                val activityIntent = android.content.Intent(this@ChatActivity, com.conversa.conversa.ui.chamada.ChamadaActivity::class.java).apply {
                    putExtra(com.conversa.conversa.ui.chamada.ChamadaActivity.EXTRA_USUARIO_NOME, conversaNome)
                    putExtra(com.conversa.conversa.ui.chamada.ChamadaActivity.EXTRA_IS_INCOMING, false)
                }
                startActivity(activityIntent)

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
    
    override fun onResume() {
        super.onResume()
        // Limpa repository de chamada quando volta para o chat
        // (a chamada foi encerrada)
        chamadaRepository?.cleanup()
        chamadaRepository = null

        // Vincula ao ChamadaService para exibir CallBanner
        chamadaObserver.bind()
        
        // Vincula ao SocketService para receber mensagens em tempo real
        bindSocketService()
        
        // Limpa notificações desta conversa
        socketService?.limparNotificacoesConversa(conversaId)
    }

    override fun onPause() {
        super.onPause()

        // Desvincula do ChamadaService
        chamadaObserver.unbind()
        
        // Desvincula do SocketService
        unbindSocketService()
    }
    
    /**
     * Vincula ao SocketService para receber mensagens em tempo real
     */
    private fun bindSocketService() {
        if (!socketBound) {
            val intent = android.content.Intent(this, SocketService::class.java)
            bindService(intent, socketConnection, android.content.Context.BIND_AUTO_CREATE)
        }
    }
    
    /**
     * Desvincula do SocketService
     */
    private fun unbindSocketService() {
        if (socketBound) {
            socketService?.setCallListener(null)
            unbindService(socketConnection)
            socketBound = false
        }
    }
    
    // ==== Implementação do SocketService.CallListener ====
    
    override fun onNovaMensagem(
        conversaIdRecebida: Int,
        remetenteId: Int,
        destinatarioId: Int,
        titulo: String,
        mensagem: String,
        tipo: Int
    ) {
        Log.d(TAG, "📨 Nova mensagem recebida via socket - Conversa: $conversaIdRecebida, Esta: $conversaId, Tipo: $tipo")
        
        // Só processa se for uma mensagem desta conversa
        if (conversaIdRecebida != conversaId) {
            Log.d(TAG, "Mensagem ignorada - conversa diferente")
            return
        }
        
        // Ignora mensagens enviadas por mim (já foram adicionadas localmente)
        if (remetenteId == usuarioId) {
            Log.d(TAG, "Mensagem ignorada - enviada por mim")
            return
        }
        
        // Verifica se o adapter foi inicializado
        if (!::mensagensAdapter.isInitialized) {
            Log.w(TAG, "Adapter ainda não inicializado, ignorando mensagem")
            return
        }
        
        // Verifica se temos o token de autenticação
        if (authToken.isEmpty()) {
            Log.w(TAG, "Token vazio, tentando carregar...")
            lifecycleScope.launch {
                authToken = userPreferences.authToken.first() ?: ""
                apiUrl = userPreferences.apiUrl.first() ?: ""
                if (authToken.isNotEmpty()) {
                    buscarEAdicionarNovaMensagem()
                }
            }
            return
        }
        
        buscarEAdicionarNovaMensagem()
    }
    
    /**
     * Busca os dados completos da nova mensagem da API e adiciona à lista
     */
    private fun buscarEAdicionarNovaMensagem() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "📨 Buscando nova mensagem da API com token: ${authToken.take(20)}...")
                
                // Busca as últimas mensagens para pegar a nova com todos os conteúdos
                val response = RetrofitClient.api.obterMensagens(
                    token = "Bearer $authToken",
                    conversaId = conversaId,
                    mensagemReferencia = 0,
                    mensagensPrevias = 5, // Busca algumas mensagens para garantir
                    mensagensSeguintes = 0
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val mensagensRecebidas = response.body()!!
                    
                    // Pega os IDs das mensagens já exibidas
                    val idsExistentes = mensagensAdapter.currentList.map { it.id }.toSet()
                    
                    // Filtra apenas as novas mensagens (que não estão na lista)
                    val novasMensagens = mensagensRecebidas.filter { it.id !in idsExistentes }
                    
                    Log.d(TAG, "📨 Mensagens novas encontradas: ${novasMensagens.size}")
                    
                    withContext(Dispatchers.Main) {
                        novasMensagens.forEach { novaMensagem ->
                            // Log detalhado dos conteúdos
                            novaMensagem.conteudos.forEach { conteudo ->
                                Log.d(TAG, "📨 Conteúdo: tipo=${conteudo.tipo}, id=${conteudo.id}")
                            }
                            
                            Log.d(TAG, "📨 Adicionando mensagem ${novaMensagem.id} com ${novaMensagem.conteudos.size} conteúdos")
                            
                            // Adiciona a nova mensagem à lista
                            mensagensAdapter.addMensagem(novaMensagem)
                            
                            // Marca como visualizada
                            marcarMensagemComoVisualizada(novaMensagem)
                        }
                        
                        // Scroll para a última mensagem se houve novas
                        if (novasMensagens.isNotEmpty()) {
                            binding.rvMensagens.scrollToPosition(mensagensAdapter.itemCount - 1)
                        }
                    }
                } else {
                    Log.e(TAG, "📨 Erro na resposta: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar nova mensagem", e)
            }
        }
    }
    
    /**
     * Marca uma única mensagem como visualizada
     */
    private suspend fun marcarMensagemComoVisualizada(mensagem: Mensagem) {
        try {
            if (mensagem.usuarioId != usuarioId && !mensagem.visualizada) {
                RetrofitClient.api.visualizarMensagem(
                    token = "Bearer $authToken",
                    conversaId = conversaId,
                    mensagemId = mensagem.id
                )
            }
        } catch (e: Exception) {
            // Silenciosamente falha - não é crítico
            Log.w(TAG, "Erro ao marcar mensagem como visualizada", e)
        }
    }
    
    override fun onChamadaRecebida(chamadaId: String, usuarioId: Int, usuarioNome: String?) {
        // Chamadas são tratadas pelo ChamadaService
        Log.d(TAG, "Chamada recebida - tratada pelo ChamadaService")
    }
    
    override fun onSocketConectado() {
        Log.d(TAG, "✅ Socket conectado")
    }
    
    override fun onSocketDesconectado() {
        Log.d(TAG, "❌ Socket desconectado")
    }
    
    override fun onSocketErro(erro: String) {
        Log.e(TAG, "❌ Erro no socket: $erro")
    }
}
