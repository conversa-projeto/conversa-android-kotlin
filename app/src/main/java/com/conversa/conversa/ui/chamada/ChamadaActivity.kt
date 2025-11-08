package com.conversa.conversa.ui.chamada

import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Chronometer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.ChamadaRepository
import com.conversa.conversa.data.socket.SocketManager
import com.conversa.conversa.databinding.ActivityChamadaBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
        
        lifecycleScope.launch {
            val apiUrl = userPreferences.apiUrl.first() ?: ""
            val host = apiUrl.replace("http://", "")
                             .replace("https://", "")
                             .split(":")[0]
            val port = 8090
            
            val socketManager = SocketManager(this@ChamadaActivity)
            
            repository = ChamadaRepository(
                context = this@ChamadaActivity,
                api = RetrofitClient.api,
                chamadaManager = ChamadaManager(this@ChamadaActivity),
                socketManager = socketManager,
                userPreferences = userPreferences
            )
            
            // Conectar ao Socket TCP
            socketManager.conectar(host, port)
            
            // Configurar callbacks
            setupCallbacks()
        }

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
        repository.onChamadaConectada = {
            runOnUiThread {
                chamadaConectada = true
                binding.tvTimer.text = "00:00"
                iniciarTimer()
            }
        }

        repository.onChamadaFinalizada = {
            runOnUiThread {
                finish()
            }
        }

        repository.onErro = { erro ->
            runOnUiThread {
                Log.e(TAG, "Erro na chamada: $erro")
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
        // Converter TextView para Chronometer se necessário
        // Por enquanto, vamos usar um timer simples
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
        // Por enquanto, apenas atualiza UI
        
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
        chamadaConectada = false
        
        // Restaurar configuração de áudio
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onBackPressed() {
        // Não permite voltar - precisa encerrar a chamada
    }
}
