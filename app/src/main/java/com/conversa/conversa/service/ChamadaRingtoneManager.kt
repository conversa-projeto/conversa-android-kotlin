package com.conversa.conversa.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Gerenciador dedicado de ringtone para chamadas VoIP.
 * Controla som e vibração de forma independente.
 * Singleton para permitir acesso global.
 */
class ChamadaRingtoneManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ChamadaRingtoneManager"
        // Padrão de vibração: vibra 1s, pausa 0.5s, vibra 1s, pausa 0.5s, repete
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500)

        @Volatile
        private var INSTANCE: ChamadaRingtoneManager? = null

        fun getInstance(context: Context): ChamadaRingtoneManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChamadaRingtoneManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Inicia o ringtone e vibração.
     * Respeita modo silencioso/DND.
     */
    fun iniciar() {
        val instanceId = System.identityHashCode(this)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🎵 iniciar() CHAMADO")
        Log.d(TAG, "   Thread: $threadName")
        Log.d(TAG, "   Instância: @$instanceId")
        Log.d(TAG, "   isPlaying ANTES: $isPlaying")
        Log.d(TAG, "   ringtone: ${ringtone?.hashCode() ?: "null"}")
        Log.d(TAG, "   vibrator: ${vibrator?.hashCode() ?: "null"}")

        // Para qualquer ringtone anterior antes de iniciar um novo
        if (isPlaying) {
            Log.d(TAG, "⚠️ Ringtone já está tocando - parando anterior")
            parar()
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Verifica se o dispositivo não está em modo silencioso
            val ringerMode = audioManager.ringerMode
            val shouldPlaySound = ringerMode == AudioManager.RINGER_MODE_NORMAL
            val shouldVibrate = ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                               ringerMode == AudioManager.RINGER_MODE_NORMAL

            Log.d(TAG, "   RingerMode: $ringerMode (NORMAL=2, VIBRATE=1, SILENT=0)")
            Log.d(TAG, "   shouldPlaySound: $shouldPlaySound")
            Log.d(TAG, "   shouldVibrate: $shouldVibrate")

            // Configurar ringtone
            if (shouldPlaySound) {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, ringtoneUri)

                ringtone?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        it.isLooping = true
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        it.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }

                    it.play()
                    Log.d(TAG, "✅ Ringtone iniciado - isPlaying: ${it.isPlaying}")
                }
            } else {
                Log.d(TAG, "⏭️ Ringtone NÃO iniciado (modo silencioso)")
            }

            // Configurar vibração
            if (shouldVibrate) {
                vibrator?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val vibrationEffect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
                        it.vibrate(vibrationEffect)
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(VIBRATION_PATTERN, 0)
                    }
                    Log.d(TAG, "✅ Vibração iniciada")
                }
            } else {
                Log.d(TAG, "⏭️ Vibração NÃO iniciada (modo silencioso)")
            }

            isPlaying = true
            Log.d(TAG, "   isPlaying DEPOIS: $isPlaying")
            Log.d(TAG, "═══════════════════════════════════════════════")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar ringtone: ${e.message}", e)
        }
    }

    /**
     * Para completamente o ringtone e vibração.
     */
    fun parar() {
        val instanceId = System.identityHashCode(this)
        val threadName = Thread.currentThread().name
        Log.d(TAG, "───────────────────────────────────────────────")
        Log.d(TAG, "🛑 parar() CHAMADO")
        Log.d(TAG, "   Thread: $threadName")
        Log.d(TAG, "   Instância: @$instanceId")
        Log.d(TAG, "   isPlaying ANTES: $isPlaying")

        try {
            // Sempre tenta parar, independente do estado
            ringtone?.let {
                Log.d(TAG, "   Ringtone.isPlaying: ${it.isPlaying}")
                if (it.isPlaying) {
                    it.stop()
                    Log.d(TAG, "✅ Ringtone parado")
                } else {
                    Log.d(TAG, "⏭️ Ringtone já estava parado")
                }
            } ?: Log.d(TAG, "⏭️ Ringtone é null")

            vibrator?.let {
                it.cancel()
                Log.d(TAG, "✅ Vibração cancelada")
            } ?: Log.d(TAG, "⏭️ Vibrator é null")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parar ringtone: ${e.message}", e)
        } finally {
            // SEMPRE reseta o estado, mesmo se não estava tocando
            isPlaying = false
            Log.d(TAG, "   isPlaying DEPOIS: $isPlaying")
            Log.d(TAG, "───────────────────────────────────────────────")
        }
    }

    /**
     * Libera recursos.
     * Deve ser chamado quando não for mais necessário.
     */
    fun release() {
        parar()
        ringtone = null
        vibrator = null
    }

    /**
     * Verifica se está tocando.
     */
    fun isPlaying(): Boolean = isPlaying
}
