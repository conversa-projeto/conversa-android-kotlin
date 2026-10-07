package com.conversa.conversa.ui.chat

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Estado do download de áudio
 */
enum class AudioDownloadState {
    NOT_STARTED,    // Não iniciado
    DOWNLOADING,    // Baixando
    READY,          // Pronto para reproduzir
    ERROR           // Erro no download
}

/**
 * Dados do estado do áudio
 */
data class AudioState(
    val downloadState: AudioDownloadState,
    val progress: Int = 0,           // 0-100
    val duration: Long = 0,          // em milissegundos
    val fileSize: Long = 0,          // em bytes
    val errorMessage: String? = null
)

/**
 * Helper para gerenciar download e reprodução de áudios
 */
class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioIdentificador: String? = null

    // Mapa para armazenar o estado de cada áudio
    private val audioStates = mutableMapOf<String, AudioState>()

    // Callbacks para atualizar UI
    private val stateCallbacks = mutableMapOf<String, (AudioState) -> Unit>()

    /**
     * Registra callback para receber atualizações de estado de um áudio
     */
    fun registerStateCallback(identificador: String, callback: (AudioState) -> Unit) {
        stateCallbacks[identificador] = callback

        // Se já tem estado, notifica imediatamente
        audioStates[identificador]?.let { callback(it) }
    }

    /**
     * Remove callback
     */
    fun unregisterStateCallback(identificador: String) {
        stateCallbacks.remove(identificador)
    }

    /**
     * Retorna o estado atual de um áudio
     */
    fun getAudioState(identificador: String): AudioState {
        return audioStates[identificador] ?: AudioState(AudioDownloadState.NOT_STARTED)
    }

    /**
     * Baixa um áudio sem reproduzir
     * Útil para download preventivo ao exibir a mensagem
     */
    fun downloadAudio(
        identificador: String,
        audioUrl: String,
        authToken: String
    ) {
        // Se já está baixado ou baixando, ignora
        val currentState = getAudioState(identificador)
        if (currentState.downloadState == AudioDownloadState.READY ||
            currentState.downloadState == AudioDownloadState.DOWNLOADING) {
            return
        }

        // Atualiza estado para DOWNLOADING
        updateAudioState(identificador, AudioState(AudioDownloadState.DOWNLOADING, progress = 0))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Baixa o áudio
                val audioFile = downloadAudioWithProgress(audioUrl, authToken, identificador) { progress ->
                    updateAudioState(
                        identificador,
                        AudioState(AudioDownloadState.DOWNLOADING, progress = progress)
                    )
                }

                // Obtém duração do áudio
                val duration = getAudioDuration(audioFile)

                // Obtém tamanho do arquivo
                val fileSize = audioFile.length()

                // Atualiza estado para READY
                updateAudioState(
                    identificador,
                    AudioState(
                        downloadState = AudioDownloadState.READY,
                        progress = 100,
                        duration = duration,
                        fileSize = fileSize
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()

                // Atualiza estado para ERROR
                updateAudioState(
                    identificador,
                    AudioState(
                        downloadState = AudioDownloadState.ERROR,
                        errorMessage = "Erro ao baixar: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * Retenta o download de um áudio que falhou
     */
    fun retryDownload(identificador: String, audioUrl: String, authToken: String) {
        // Remove o áudio com erro do cache
        val cacheDir = File(context.cacheDir, "audios")
        val audioFile = File(cacheDir, "audio_${identificador}.mp3")
        if (audioFile.exists()) {
            audioFile.delete()
        }

        // Reinicia o download
        downloadAudio(identificador, audioUrl, authToken)
    }

    /**
     * Reproduz um áudio (que já deve estar baixado)
     */
    fun playAudio(
        identificador: String,
        audioUrl: String,
        authToken: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Verifica se o áudio está pronto
        val state = getAudioState(identificador)
        if (state.downloadState != AudioDownloadState.READY) {
            onError("Áudio ainda não está pronto para reprodução")
            return
        }

        // Se já está tocando o mesmo áudio, pausa
        if (currentAudioIdentificador == identificador && mediaPlayer?.isPlaying == true) {
            pauseAudio()
            return
        }

        // Se está tocando outro áudio, para
        if (mediaPlayer?.isPlaying == true) {
            stopAudio()
        }

        currentAudioIdentificador = identificador

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDir = File(context.cacheDir, "audios")
                val audioFile = File(cacheDir, "audio_${identificador}.mp3")

                if (!audioFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onError("Arquivo de áudio não encontrado")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(audioFile.absolutePath)
                        prepare()

                        setOnCompletionListener {
                            onComplete()
                            stopAudio()
                        }

                        setOnErrorListener { _, what, extra ->
                            onError("Erro ao reproduzir: $what, $extra")
                            stopAudio()
                            true
                        }

                        start()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onError("Erro ao carregar áudio: ${e.message}")
                }
            }
        }
    }

    /**
     * Pausa a reprodução
     */
    fun pauseAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
            }
        }
    }

    /**
     * Resume a reprodução
     */
    fun resumeAudio() {
        mediaPlayer?.apply {
            if (!isPlaying) {
                start()
            }
        }
    }

    /**
     * Para a reprodução e libera recursos
     */
    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        currentAudioIdentificador = null
    }

    /**
     * Retorna se está tocando
     */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    /**
     * Retorna o ID do áudio atual
     */
    fun getCurrentAudioId(): String? = currentAudioIdentificador

    /**
     * Retorna a posição atual da reprodução em milissegundos
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * Retorna a duração total do áudio em reprodução em milissegundos
     */
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    /**
     * Vai para uma posição específica do áudio
     */
    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    /**
     * Atualiza o estado de um áudio e notifica callbacks
     */
    private fun updateAudioState(identificador: String, state: AudioState) {
        audioStates[identificador] = state

        // Notifica callback na thread principal
        CoroutineScope(Dispatchers.Main).launch {
            stateCallbacks[identificador]?.invoke(state)
        }
    }

    /**
     * Baixa o áudio com progresso
     */
    private suspend fun downloadAudioWithProgress(
        audioUrl: String,
        authToken: String,
        audioIdentificador: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "audios")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val audioFile = File(cacheDir, "audio_${audioIdentificador}.mp3")

        // Se já existe, retorna
        if (audioFile.exists()) {
            onProgress(100)
            return@withContext audioFile
        }

        // Cria cliente OkHttp
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Faz o download COM AUTENTICAÇÃO
        val request = okhttp3.Request.Builder()
            .url(audioUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Erro ao baixar áudio: ${response.code} - ${response.message}")
        }

        val responseBody = response.body ?: throw Exception("Resposta vazia do servidor")
        val contentLength = responseBody.contentLength()

        // Salva o arquivo com progresso
        responseBody.byteStream().use { input ->
            FileOutputStream(audioFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Calcula e reporta progresso
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
            }
        }

        onProgress(100)
        audioFile
    }

    /**
     * Obtém a duração de um arquivo de áudio em milissegundos
     */
    private fun getAudioDuration(audioFile: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * Formata duração em milissegundos para string (ex: "1:23")
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    /**
     * Formata tamanho de arquivo em bytes para string legível (ex: "2.5 MB")
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Libera recursos quando não for mais usado
     */
    fun release() {
        stopAudio()
        stateCallbacks.clear()
        audioStates.clear()
    }
}