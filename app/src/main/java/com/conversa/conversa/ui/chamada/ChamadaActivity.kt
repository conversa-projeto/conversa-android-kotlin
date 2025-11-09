package com.conversa.conversa.ui.chamada

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.ChamadaRepository
import com.conversa.conversa.databinding.ActivityChamadaBinding
import kotlinx.coroutines.launch

class ChamadaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChamadaBinding
    private lateinit var repository: ChamadaRepository
    private lateinit var userPreferences: UserPreferences
    private lateinit var audioManager: AudioManager
    
    private var chamadaId: Int = 0
    private var usuarioId: Int = 0
    private var usuarioNome: String = ""
    private var isIncoming: Boolean = false // NOVO: determina se é chamada recebida
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false
    private var chamadaConectada: Boolean = false
    private var permissoesVerificadas: Boolean = false

    companion object {
        private const val TAG = "ChamadaActivity"
        private const val REQUEST_RECORD_AUDIO = 201
        
        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_IS_INCOMING = "is_incoming" // NOVO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChamadaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obter dados do intent
        chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, 0)
        usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, 0)
        usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: "Contato"
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false) // NOVO

        if (chamadaId == 0) {
            Toast.makeText(this, "Erro: chamada inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Chamada: id=$chamadaId, incoming=$isIncoming")

        // Inicializar AudioManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Inicializar componentes
        userPreferences = UserPreferences(this)
        
        // Verificar permissão de áudio
        verificarPermissaoAudio()
    }
    
    private fun verificarPermissaoAudio() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permissão de áudio NÃO concedida, solicitando...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            Log.d(TAG, "Permissão de áudio já concedida")
            permissoesVerificadas = true
            inicializarChamada()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permissão de áudio concedida")
                    permissoesVerificadas = true
                    inicializarChamada()
                } else {
                    Log.e(TAG, "Permissão de áudio NEGADA")
                    Toast.makeText(
                        this,
                        "Permissão de microfone necessária para chamadas",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
    
    private fun inicializarChamada() {
        if (!permissoesVerificadas) {
            Log.e(TAG, "Tentou inicializar sem permissões verificadas")
            return
        }
        
        Log.d(TAG, "Inicializando chamada com permissões OK")
        
        val socketManager = ChamadaIncomingActivity.sharedSocketManager
        
        if (socketManager == null) {
            Log.e(TAG, "SocketManager não disponível")
            Toast.makeText(this, "Erro: serviço não disponível", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        repository = ChamadaRepository(
            context = this@ChamadaActivity,
            api = RetrofitClient.api,
            chamadaManager = ChamadaManager(this@ChamadaActivity),
            socketManager = socketManager,
            userPreferences = userPreferences
        )
        
        setupCallbacks()
        setupUI()
        setupListeners()
        
        // Se é incoming, mostra botões de aceitar/recusar
        // Se não, já inicia conectando
        if (isIncoming) {
            mostrarEstadoIncoming()
        } else {
            mostrarEstadoAtivo()
        }
    }

    private fun setupUI() {
        binding.tvNomeContato.text = usuarioNome
        binding.tvTimer.text = if (isIncoming) getString(R.string.chamada_recebida) else getString(R.string.conectando)
        
        atualizarBotaoMute()
        atualizarBotaoSpeaker()
    }
    
    /**
     * Mostra estado INCOMING: botões aceitar/recusar
     */
    private fun mostrarEstadoIncoming() {
        binding.llBotoesIncoming.visibility = View.VISIBLE
        binding.llControles.visibility = View.GONE
        binding.llBotaoEncerrar.visibility = View.GONE
        
        binding.tvTimer.text = getString(R.string.chamada_recebida)
    }
    
    /**
     * Mostra estado ATIVO: controles mute/speaker/encerrar
     */
    private fun mostrarEstadoAtivo() {
        binding.llBotoesIncoming.visibility = View.GONE
        binding.llControles.visibility = View.VISIBLE
        binding.llBotaoEncerrar.visibility = View.VISIBLE
        
        binding.tvTimer.text = getString(R.string.conectando)
    }

    private fun setupListeners() {
        // Botões INCOMING
        binding.btnAceitar.setOnClickListener {
            aceitarChamada()
        }
        
        binding.btnRecusar.setOnClickListener {
            recusarChamada()
        }
        
        // Botões ATIVOS
        binding.btnMute.setOnClickListener {
            toggleMute()
        }

        binding.btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }

        binding.btnEncerrarChamada.setOnClickListener {
            finalizarChamada()
        }
    }
    
    /**
     * Aceita a chamada recebida
     */
    private fun aceitarChamada() {
        Log.d(TAG, "Aceitando chamada $chamadaId")
        
        binding.btnAceitar.isEnabled = false
        binding.btnRecusar.isEnabled = false
        binding.tvTimer.text = getString(R.string.conectando)
        
        lifecycleScope.launch {
            try {
                val result = repository.aceitarChamada(chamadaId)
                
                result.onSuccess {
                    Log.d(TAG, "Chamada aceita com sucesso")
                    mostrarEstadoAtivo()
                }
                
                result.onFailure { erro ->
                    Log.e(TAG, "Erro ao aceitar chamada", erro)
                    Toast.makeText(
                        this@ChamadaActivity,
                        "Erro ao aceitar: ${erro.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao aceitar", e)
                Toast.makeText(
                    this@ChamadaActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
    
    /**
     * Recusa a chamada recebida
     */
    private fun recusarChamada() {
        Log.d(TAG, "Recusando chamada $chamadaId")
        
        binding.btnAceitar.isEnabled = false
        binding.btnRecusar.isEnabled = false
        
        lifecycleScope.launch {
            try {
                repository.recusarChamada(chamadaId)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao recusar", e)
                finish()
            }
        }
    }

    private fun setupCallbacks() {
        Log.d(TAG, "Configurando callbacks do repository")
        
        repository.onChamadaConectada = {
            Log.d(TAG, "CALLBACK: Chamada conectada!")
            runOnUiThread {
                chamadaConectada = true
                binding.tvTimer.text = "00:00"
                iniciarTimer()
                Toast.makeText(this@ChamadaActivity, "Chamada conectada", Toast.LENGTH_SHORT).show()
            }
        }

        repository.onChamadaFinalizada = {
            Log.d(TAG, "CALLBACK: Chamada finalizada!")
            runOnUiThread {
                chamadaConectada = false
                Toast.makeText(this@ChamadaActivity, "Chamada finalizada", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        repository.onErro = { erro ->
            Log.e(TAG, "CALLBACK: Erro na chamada: $erro")
            runOnUiThread {
                Toast.makeText(
                    this@ChamadaActivity,
                    "Erro: $erro",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun iniciarTimer() {
        val startTime = SystemClock.elapsedRealtime()
        
        lifecycleScope.launch {
            while (chamadaConectada) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val seconds = (elapsed / 1000).toInt()
                val minutes = seconds / 60
                val secs = seconds % 60
                
                runOnUiThread {
                    binding.tvTimer.text = String.format("%02d:%02d", minutes, secs)
                }
                
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        
        // TODO: Implementar lógica de mute no ChamadaManager
        
        atualizarBotaoMute()
        
        Toast.makeText(
            this,
            if (isMuted) "Microfone desligado" else "Microfone ligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeakerOn
        
        atualizarBotaoSpeaker()
        
        Toast.makeText(
            this,
            if (isSpeakerOn) "Alto-falante ligado" else "Alto-falante desligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun atualizarBotaoMute() {
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off)
            binding.tvMuteLabel.text = "Mudo"
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_mic_on)
            binding.tvMuteLabel.text = "Mudo"
        }
    }

    private fun atualizarBotaoSpeaker() {
        if (isSpeakerOn) {
            binding.btnSpeaker.setImageResource(R.drawable.ic_volume_up)
            binding.tvSpeakerLabel.text = getString(R.string.alto_falante)
        } else {
            binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off)
            binding.tvSpeakerLabel.text = getString(R.string.alto_falante)
        }
    }

    private fun finalizarChamada() {
        lifecycleScope.launch {
            try {
                chamadaConectada = false
                binding.tvTimer.text = getString(R.string.chamada_finalizada)
                
                repository.finalizarChamada()
                
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao finalizar chamada", e)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity sendo destruída")
        chamadaConectada = false
        
        if (::repository.isInitialized) {
            repository.cleanup()
        }
        
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onBackPressed() {
        // Não permite voltar
    }
}
