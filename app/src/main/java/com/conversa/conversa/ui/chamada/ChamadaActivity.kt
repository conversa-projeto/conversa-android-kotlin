package com.conversa.conversa.ui.chamada

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.chamada.model.EventoChamadaUI
import com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI
import com.conversa.conversa.data.model.UsuarioChamadaStatus
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.ChamadaRepository
import com.conversa.conversa.service.ChamadaRingtoneManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChamadaActivity : AppCompatActivity(),
    SensorEventListener,
    IncomingCallFragment.IncomingCallListener,
    SimpleCallFragment.SimpleCallListener,
    GroupCallFragment.GroupCallListener {

    private lateinit var repository: ChamadaRepository
    private lateinit var userPreferences: UserPreferences
    private lateinit var audioManager: AudioManager

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var proximityWakeLock: PowerManager.WakeLock

    private var chamadaId: Int = 0
    private var usuarioId: Int = 0
    private var usuarioNome: String = ""
    private var isIncoming: Boolean = false
    private var autoAnswer: Boolean = false
    private var isMuted: Boolean = false
    private var isSpeakerOn: Boolean = false
    private var chamadaConectada: Boolean = false
    private var permissoesVerificadas: Boolean = false
    private var isChamadaGrupo: Boolean = false

    // Mapa de participantes mutados localmente
    private val participantesMutados = mutableSetOf<Int>()

    private var timerStartTime: Long = 0
    private var timerText: String = "00:00"

    private var currentFragment: Fragment? = null

    companion object {
        private const val TAG = "ChamadaActivity"
        private const val REQUEST_RECORD_AUDIO = 201

        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_IS_INCOMING = "is_incoming"

        var sharedSocketManager: com.conversa.conversa.data.socket.SocketManager? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chamada)

        configurarTelasBloqueadas()

        // IMPORTANTE: Para ringtone quando a activity abre
        ChamadaRingtoneManager.getInstance(this).parar()

        // Remove notificação de chamada (se houver)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1002)

        chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, 0)
        usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, 0)
        usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: "Contato"
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        autoAnswer = intent.getBooleanExtra("auto_answer", false)

        Log.d(TAG, "📞 onCreate - Extras recebidos:")
        Log.d(TAG, "   chamadaId=$chamadaId")
        Log.d(TAG, "   usuarioId=$usuarioId")
        Log.d(TAG, "   isIncoming=$isIncoming")
        Log.d(TAG, "   autoAnswer=$autoAnswer")
        Log.d(TAG, "   usuarioNome=$usuarioNome")

        if (chamadaId == 0) {
            Toast.makeText(this, "Erro: chamada inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "Chamada: id=$chamadaId, incoming=$isIncoming, usuario=$usuarioNome")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        userPreferences = UserPreferences(this)

        inicializarSensorProximidade()
        verificarPermissaoAudio()
    }
    
    private fun configurarTelasBloqueadas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun inicializarSensorProximidade() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Conversa::ProximityWakeLock"
        )
    }

    private fun ativarSensorProximidade() {
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Sensor de proximidade ativado")
        }
    }

    private fun desativarSensorProximidade() {
        sensorManager.unregisterListener(this)
        if (proximityWakeLock.isHeld) {
            proximityWakeLock.release()
        }
        Log.d(TAG, "Sensor de proximidade desativado")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = event.sensor.maximumRange
            val isNear = distance < 5f && distance < maxRange

            if (chamadaConectada) {
                if (isNear) {
                    if (!proximityWakeLock.isHeld) {
                        proximityWakeLock.acquire(10 * 60 * 1000L)
                    }
                } else {
                    if (proximityWakeLock.isHeld) {
                        proximityWakeLock.release()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun verificarPermissaoAudio() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
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
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    permissoesVerificadas = true
                    inicializarChamada()
                } else {
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
        if (!permissoesVerificadas) return

        val socketManager = sharedSocketManager
        if (socketManager == null) {
            Toast.makeText(this, "Erro: serviço não disponível", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        repository = ChamadaRepository(
            context = this,
            api = RetrofitClient.api,
            chamadaManager = ChamadaManager(this),
            socketManager = socketManager,
            userPreferences = userPreferences
        )

        setupCallbacks()
        setupAudio()
        observarFlows()

        if (isIncoming) {
            if (autoAnswer) {
                Log.d(TAG, "✅ AUTO-ANSWER ativado - aceitando chamada automaticamente")
                onAceitarChamada()
            } else {
                Log.d(TAG, "🔔 Mostrando tela de chamada recebida")
                mostrarIncomingFragment()
            }
        } else {
            Log.d(TAG, "📞 Chamada sainte - indo direto para tela de chamada")
            mostrarCallFragment()
        }
    }

    private fun setupCallbacks() {
        repository.onErro = { erro ->
            runOnUiThread {
                Toast.makeText(this, "Erro: $erro", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupAudio() {
        isMuted = false
        isSpeakerOn = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun observarFlows() {
        lifecycleScope.launch {
            repository.eventosUIFlow.collectLatest { evento ->
                processarEvento(evento)
            }
        }

        lifecycleScope.launch {
            repository.chamadaAtualFlow.collectLatest { chamada ->
                chamada?.let {
                    isChamadaGrupo = it.tipo == 2
                    atualizarFragmentSeNecessario()
                    atualizarListaParticipantes()
                }
            }
        }
    }

    private fun processarEvento(evento: EventoChamadaUI) {
        when (evento.tipo) {
            TipoEventoChamadaUI.PARTICIPANTE_ENTROU -> {
                atualizarListaParticipantes()
                evento.participanteNome?.let { nome ->
                    Toast.makeText(this, "$nome entrou", Toast.LENGTH_SHORT).show()
                }
            }

            TipoEventoChamadaUI.PARTICIPANTE_SAIU -> {
                atualizarListaParticipantes()
                evento.participanteNome?.let { nome ->
                    Toast.makeText(this, "$nome saiu da chamada", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(2000)
                        finish()
                    }
                }
            }

            TipoEventoChamadaUI.CHAMADA_RECUSADA -> {
                Toast.makeText(this, "Chamada recusada", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(2000)
                    finish()
                }
            }

            TipoEventoChamadaUI.CHAMADA_REALMENTE_INICIADA -> {
                chamadaConectada = true
                timerStartTime = SystemClock.elapsedRealtime()
                iniciarTimer()
                ativarSensorProximidade()
                atualizarBotoes()
                Toast.makeText(this, "Chamada iniciada", Toast.LENGTH_SHORT).show()
            }

            TipoEventoChamadaUI.CHAMADA_FINALIZADA -> {
                chamadaConectada = false
                desativarSensorProximidade()
                finish()
            }

            else -> {}
        }
    }

    private fun atualizarFragmentSeNecessario() {
        if (!chamadaConectada) return

        val fragmentoEsperado = if (isChamadaGrupo) {
            GroupCallFragment::class.java
        } else {
            SimpleCallFragment::class.java
        }

        if (currentFragment?.javaClass != fragmentoEsperado) {
            mostrarCallFragment()
        }
    }

    private fun mostrarIncomingFragment() {
        val fragment = IncomingCallFragment()
        replaceFragment(fragment)
    }

    private fun mostrarCallFragment() {
        val fragment = if (isChamadaGrupo) {
            GroupCallFragment()
        } else {
            SimpleCallFragment()
        }
        replaceFragment(fragment)
    }

    private fun replaceFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun iniciarTimer() {
        lifecycleScope.launch {
            while (chamadaConectada) {
                val elapsed = SystemClock.elapsedRealtime() - timerStartTime
                val seconds = (elapsed / 1000).toInt()
                val minutes = seconds / 60
                val secs = seconds % 60

                timerText = String.format("%02d:%02d", minutes, secs)
                atualizarTimer()

                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun atualizarTimer() {
        (currentFragment as? SimpleCallFragment)?.atualizarTimer()
        (currentFragment as? GroupCallFragment)?.atualizarTimer()
    }

    private fun atualizarBotoes() {
        (currentFragment as? SimpleCallFragment)?.atualizarBotoes()
        (currentFragment as? GroupCallFragment)?.atualizarBotoes()
    }

    private fun atualizarListaParticipantes() {
        (currentFragment as? GroupCallFragment)?.atualizarListaParticipantes()
    }

    // IncomingCallListener
    override fun onAceitarChamada() {
        (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

        lifecycleScope.launch {
            try {
                val result = repository.aceitarChamada(chamadaId)

                result.onSuccess {
                    atualizarNotificacaoParaEmAndamento()
                    mostrarCallFragment()
                }

                result.onFailure { erro ->
                    Toast.makeText(
                        this@ChamadaActivity,
                        "Erro ao aceitar: ${erro.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChamadaActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onRecusarChamada() {
        (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

        lifecycleScope.launch {
            try {
                repository.recusarChamada(chamadaId)
                removerNotificacaoChamada()
                finish()
            } catch (e: Exception) {
                removerNotificacaoChamada()
                finish()
            }
        }
    }

    // SimpleCallListener & GroupCallListener
    override fun onEncerrarChamada() {
        lifecycleScope.launch {
            try {
                chamadaConectada = false
                desativarSensorProximidade()
                repository.finalizarChamada()
                removerNotificacaoChamada()
                finish()
            } catch (e: Exception) {
                removerNotificacaoChamada()
                finish()
            }
        }
    }

    override fun onToggleMute() {
        isMuted = !isMuted

        if (::repository.isInitialized) {
            if (isMuted) {
                repository.pausarCaptura()
            } else {
                repository.retormarCaptura()
            }
        }

        Toast.makeText(
            this,
            if (isMuted) "Microfone desligado" else "Microfone ligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onToggleSpeaker() {
        isSpeakerOn = !isSpeakerOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeakerOn

        Toast.makeText(
            this,
            if (isSpeakerOn) "Alto-falante ligado" else "Alto-falante desligado",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun getNomeContato(): String = usuarioNome
    override fun getNomeGrupo(): String = usuarioNome
    override fun getTimerText(): String = timerText
    override fun isMuted(): Boolean = isMuted
    override fun isSpeakerOn(): Boolean = isSpeakerOn
    override fun isChamadaConectada(): Boolean = chamadaConectada

    override fun getParticipantes(): List<ParticipanteItem> {
        val chamada = repository.chamadaAtual ?: return emptyList()

        return chamada.usuarios.map { usuario ->
            val status = when (usuario.status) {
                UsuarioChamadaStatus.ENTROU.valor -> "Conectado"
                UsuarioChamadaStatus.PENDENTE.valor -> "Aguardando..."
                UsuarioChamadaStatus.RECUSADO.valor -> "Recusou"
                UsuarioChamadaStatus.SAIU.valor -> "Saiu"
                else -> "Desconhecido"
            }

            ParticipanteItem(
                id = usuario.usuarioId,
                nome = usuario.usuarioNome,
                fotoUrl = null,
                audioAtivo = usuario.status == UsuarioChamadaStatus.ENTROU.valor,
                status = status,
                mutadoLocalmente = participantesMutados.contains(usuario.usuarioId)
            )
        }
    }

    override fun onMutarParticipante(participante: ParticipanteItem) {
        if (participantesMutados.contains(participante.id)) {
            participantesMutados.remove(participante.id)
            Toast.makeText(this, "Ouvindo ${participante.nome}", Toast.LENGTH_SHORT).show()
        } else {
            participantesMutados.add(participante.id)
            Toast.makeText(this, "${participante.nome} mutado", Toast.LENGTH_SHORT).show()
        }

        // Atualiza o ChamadaManager com a lista de mutados
        if (::repository.isInitialized) {
            repository.atualizarParticipantesMutados(participantesMutados)
        }

        // Atualiza a lista para refletir a mudança
        atualizarListaParticipantes()
    }

    private fun atualizarNotificacaoParaEmAndamento() {
        try {
            val intent = android.content.Intent(this, com.conversa.conversa.service.SocketService::class.java)
            intent.action = "UPDATE_NOTIFICATION"
            intent.putExtra("chamada_id", chamadaId)
            intent.putExtra("usuario_nome", usuarioNome)
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar notificação", e)
        }
    }
    
    private fun removerNotificacaoChamada() {
        try {
            val intent = android.content.Intent(this, com.conversa.conversa.service.SocketService::class.java)
            intent.action = "REMOVE_NOTIFICATION"
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover notificação", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chamadaConectada = false

        desativarSensorProximidade()

        if (::repository.isInitialized) {
            repository.cleanup()
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onBackPressed() {
        // Não permite voltar durante chamada
    }
}
