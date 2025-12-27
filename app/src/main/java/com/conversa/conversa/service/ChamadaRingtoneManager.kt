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
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 1000) // Vibra 1s, pausa 1s, repete

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
        if (isPlaying) {
            Log.w(TAG, "Ringtone já está tocando")
            return
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Verifica se o dispositivo não está em modo silencioso
            val ringerMode = audioManager.ringerMode
            val shouldPlaySound = ringerMode == AudioManager.RINGER_MODE_NORMAL
            val shouldVibrate = ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                               ringerMode == AudioManager.RINGER_MODE_NORMAL

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
                    Log.d(TAG, "Ringtone iniciado")
                }
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
                    Log.d(TAG, "Vibração iniciada")
                }
            }

            isPlaying = true

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar ringtone: ${e.message}", e)
        }
    }

    /**
     * Para completamente o ringtone e vibração.
     */
    fun parar() {
        if (!isPlaying) {
            return
        }

        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                    Log.d(TAG, "Ringtone parado")
                }
            }

            vibrator?.cancel()
            Log.d(TAG, "Vibração cancelada")

            isPlaying = false

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar ringtone: ${e.message}", e)
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
