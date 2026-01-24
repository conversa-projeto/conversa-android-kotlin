package com.conversa.conversa.ui.chamada

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.service.ChamadaRingtoneManager
import com.conversa.conversa.service.ChamadaService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity de chamada usando Compose e vinculada ao ChamadaService.
 *
 * Responsabilidades:
 * - Configuração de telas bloqueadas
 * - Permissão de áudio
 * - Sensor de proximidade
 * - Bind ao ChamadaService
 * - Renderizar ChamadaScreen
 */
class ChamadaActivity : ComponentActivity(), SensorEventListener {

    companion object {
        private const val TAG = "ChamadaActivity"
        private const val REQUEST_RECORD_AUDIO = 201

        // Extras
        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_IS_INCOMING = "is_incoming"
        const val EXTRA_AUTO_ANSWER = "auto_answer"
    }

    // Serviço de chamada
    private var chamadaService: ChamadaService? = null
    private var bound by mutableStateOf(false)

    // Sensor de proximidade
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var proximityWakeLock: PowerManager.WakeLock

    // Extras do Intent
    private var chamadaIdFromIntent: Int = 0
    private var isIncoming: Boolean = false
    private var autoAnswer: Boolean = false

    // Controle de UI
    private var permissoesVerificadas: Boolean = false

    // ServiceConnection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service conectado")
            chamadaService = (binder as ChamadaService.LocalBinder).getService()
            bound = true

            // Auto-answer se necessário
            if (autoAnswer && isIncoming) {
                lifecycleScope.launch {
                    Log.d(TAG, "✅ AUTO-ANSWER: aceitando chamada automaticamente")
                    delay(500) // Aguarda UI renderizar
                    chamadaService?.aceitarChamada()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service desconectado")
            chamadaService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate")

        // Configurar telas bloqueadas
        configurarTelasBloqueadas()

        // Para ringtone
        ChamadaRingtoneManager.getInstance(this).parar()

        // Cancela notificação
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(1002)

        // Extrai extras
        chamadaIdFromIntent = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        autoAnswer = intent.getBooleanExtra(EXTRA_AUTO_ANSWER, false)

        Log.d(TAG, "Extras: chamadaId=$chamadaIdFromIntent, isIncoming=$isIncoming, autoAnswer=$autoAnswer")

        if (chamadaIdFromIntent == -1) {
            Toast.makeText(this, "Erro: chamada inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inicializar sensor de proximidade
        inicializarSensorProximidade()

        // Verificar permissão de áudio
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermissaoAudio()
        } else {
            permissoesVerificadas = true
            inicializarUI()
        }
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

        Log.d(TAG, "Sensor de proximidade inicializado")
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

            // Só ativa WakeLock se chamada estiver em andamento
            val service = chamadaService
            if (service != null) {
                val estado = service.estadoFlow.value
                val emChamada = estado == com.conversa.conversa.service.EstadoChamadaService.EM_CHAMADA

                if (emChamada) {
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
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não utilizado
    }

    private fun solicitarPermissaoAudio() {
        Log.d(TAG, "Solicitando permissão de áudio")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
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
                    inicializarUI()
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

    private fun inicializarUI() {
        Log.d(TAG, "Inicializando UI")

        // Vincular ao ChamadaService
        val intent = Intent(this, ChamadaService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        // Ativar sensor de proximidade
        ativarSensorProximidade()

        setContent {
            MaterialTheme {
                val service = chamadaService

                if (service != null && bound) {
                    ChamadaScreen(
                        chamadaService = service,
                        onFinish = {
                            Log.d(TAG, "onFinish chamado - encerrando Activity")
                            finish()
                        }
                    )
                } else {
                    // Loading enquanto conecta ao service
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        // Desvincula do service
        if (bound) {
            try {
                unbindService(connection)
                bound = false
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao desvincular service", e)
            }
        }

        // Desativa sensor de proximidade
        desativarSensorProximidade()

        // Para ringtone
        ChamadaRingtoneManager.getInstance(this).parar()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Não permite voltar durante chamada
        Log.d(TAG, "onBackPressed - bloqueado")
    }
}
