package com.conversa.conversa.ui.chamada

import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private var usuarioNome: String = ""
    private var isIniciador: Boolean = false
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false
    private var chamadaConectada: Boolean = false

    companion object {
        private const val TAG = "ChamadaActivity"
        
        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_IS_INICIADOR = "is_iniciador"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChamadaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obter dados do intent
        chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, 0)
        usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: "Contato"
        isIniciador = intent.getBooleanExtra(EXTRA_IS_INICIADOR, false)

        if (chamadaId == 0) {
            Toast.makeText(this, "Erro: chamada inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inicializar AudioManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Inicializar componentes
        userPreferences = UserPreferences(this)
        
        // Usa o SocketManager compartilhado do Service
        val socketManager = ChamadaIncomingActivity.sharedSocketManager
        
        if (socketManager == null) {
            Log.e(TAG, "SocketManager não disponível - Service não iniciado?")
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
        
        // Configurar callbacks
        setupCallbacks()

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvNomeContato.text = usuarioNome
        binding.tvTimer.text = getString(R.string.conectando)
        
        // Iniciar com speaker desligado e microfone ligado
        atualizarBotaoMute()
        atualizarBotaoSpeaker()
    }

    private fun setupListeners() {
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
        
        // Limpa recursos do repository (fecha conexão TCP)
        if (::repository.isInitialized) {
            repository.cleanup()
        }
        
        // Restaurar configuração de áudio
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onBackPressed() {
        // Não permite voltar - precisa encerrar a chamada
    }
}
