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
 * Gerenciador dedicado de ringtone e vibração para chamadas
 * Permite controle programático de início, pausa e parada
 */
class CallRingtoneManager(private val context: Context) {

    companion object {
        private const val TAG = "CallRingtoneManager"

        // Padrão de vibração: [delay, vibrate, sleep, vibrate, ...]
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 500, 1000, 500, 1000)
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    init {
        // Obtém vibrador
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Inicia ringtone e vibração
     * Respeita configurações de modo silencioso/DND
     */
    fun iniciar() {
        if (isPlaying) {
            Log.d(TAG, "Ringtone já está tocando")
            return
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Verifica modo de áudio
            val ringerMode = audioManager.ringerMode

            when (ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> {
                    Log.d(TAG, "Modo silencioso - sem som nem vibração")
                    return
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    Log.d(TAG, "Modo vibração - apenas vibração")
                    iniciarVibração()
                }
                AudioManager.RINGER_MODE_NORMAL -> {
                    Log.d(TAG, "Modo normal - som e vibração")
                    iniciarRingtone()
                    iniciarVibração()
                }
            }

            isPlaying = true
            Log.d(TAG, "Ringtone iniciado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar ringtone", e)
        }
    }

    /**
     * Inicia apenas o ringtone (som)
     */
    private fun iniciarRingtone() {
        try {
            // Obtém ringtone padrão de chamada
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, ringtoneUri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }

            // Configura atributos de áudio
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }

            ringtone?.play()
            Log.d(TAG, "Som de chamada iniciado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar som", e)
        }
    }

    /**
     * Inicia apenas a vibração
     */
    private fun iniciarVibração() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    VIBRATION_PATTERN,
                    0 // Repete a partir do índice 0
                )
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }

            Log.d(TAG, "Vibração iniciada")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar vibração", e)
        }
    }

    /**
     * Pausa ringtone e vibração (pode ser retomado)
     */
    fun pausar() {
        if (!isPlaying) {
            Log.d(TAG, "Ringtone não está tocando")
            return
        }

        try {
            ringtone?.stop()
            vibrator?.cancel()
            isPlaying = false
            Log.d(TAG, "Ringtone pausado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao pausar ringtone", e)
        }
    }

    /**
     * Para ringtone e vibração completamente
     */
    fun parar() {
        try {
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            isPlaying = false
            Log.d(TAG, "Ringtone parado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar ringtone", e)
        }
    }

    /**
     * Retorna se está tocando
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Libera recursos
     */
    fun release() {
        parar()
        vibrator = null
        Log.d(TAG, "Recursos liberados")
    }
}
