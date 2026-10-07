package com.conversa.conversa.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * Helper para gravação de áudio
 */
class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var startTime: Long = 0

    /**
     * Inicia a gravação de áudio
     * @return File do áudio gravado ou null em caso de erro
     */
    fun startRecording(): File? {
        try {
            // Cria arquivo temporário para o áudio
            audioFile = File(
                context.cacheDir,
                "audio_${System.currentTimeMillis()}.m4a"
            )

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile?.absolutePath)

                prepare()
                start()
                
                isRecording = true
                startTime = System.currentTimeMillis()
            }

            return audioFile

        } catch (e: IOException) {
            e.printStackTrace()
            release()
            return null
        }
    }

    /**
     * Para a gravação
     * @return Duração da gravação em milissegundos
     */
    fun stopRecording(): Long {
        val duration = if (isRecording) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }

        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
        }

        return duration
    }

    /**
     * Cancela a gravação e deleta o arquivo
     */
    fun cancelRecording() {
        stopRecording()
        audioFile?.delete()
        audioFile = null
    }

    /**
     * Verifica se está gravando
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Obtém a duração atual da gravação em milissegundos
     */
    fun getCurrentDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }

    /**
     * Libera recursos
     */
    fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
    }

    /**
     * Formata duração em mm:ss
     */
    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
