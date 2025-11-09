package com.conversa.conversa.data.chamada

import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

/**
 * Gerenciador de chamadas de áudio
 * Responsável por:
 * - Conectar ao servidor TCP (porta 9090)
 * - Capturar áudio do microfone (AudioRecord)
 * - Enviar áudio para o servidor
 * - Receber áudio do servidor
 * - Reproduzir áudio (AudioTrack)
 * - Fazer mixing de múltiplos streams (para chamadas em grupo)
 *
 *
 * QUando recebe a ligação não está reproduzindo corretamente o áudio no destinatário,
 * parece está capturando, mas não reproduz
 *
 */
class ChamadaManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ChamadaManager"
        
        // Configurações de áudio (conforme documentação)
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 2048 // ~23ms de áudio
        
        // Tipos de pacote TCP
        private const val PACKET_TYPE_REGISTER: Byte = 0
        private const val PACKET_TYPE_AUDIO: Byte = 1
    }
    
    // Estado da chamada
    private var emChamada = false
    private var chamadaId: Int = 0
    private var usuarioId: Int = 0
    
    // Conexão TCP
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    
    // Sincronização thread-safe para envio TCP
    private val envioMutex = Mutex()
    
    // Fila de pacotes para envio serializado
    private val filaEnvio = Channel<ByteArray>(capacity = 100)
    
    // Áudio
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var senderJob: Job? = null
    
    // Mixing (para chamadas em grupo)
    private val mixingBuffers = mutableMapOf<Int, LinkedBlockingQueue<ShortArray>>()
    
    // Callbacks
    var onConexaoEstabelecida: (() -> Unit)? = null
    var onConexaoFalhou: ((String) -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    
    /**
     * Inicia uma nova chamada
     */
    suspend fun iniciarChamada(
        serverHost: String,
        serverPort: Int,
        chamadaId: Int,
        usuarioId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            this@ChamadaManager.chamadaId = chamadaId
            this@ChamadaManager.usuarioId = usuarioId
            
            Log.d(TAG, "=== INICIANDO CHAMADA ===")
            Log.d(TAG, "Servidor: $serverHost:$serverPort")
            Log.d(TAG, "ChamadaId: $chamadaId")
            Log.d(TAG, "UsuarioId: $usuarioId")
            
            // Conecta ao servidor TCP
            socket = Socket(serverHost, serverPort).apply {
                tcpNoDelay = true
                soTimeout = 5000
                keepAlive = true
            }
            
            outputStream = DataOutputStream(socket!!.getOutputStream())
            inputStream = DataInputStream(socket!!.getInputStream())
            
            Log.d(TAG, "Socket TCP conectado")
            
            // Registra cliente no servidor
            registrarCliente()
            
            // Inicializa componentes de áudio
            Log.d(TAG, "Inicializando componentes de áudio...")
            if (!inicializarAudio()) {
                Log.e(TAG, "❌ Falha ao inicializar áudio")
                withContext(Dispatchers.Main) {
                    onConexaoFalhou?.invoke("Falha ao inicializar componentes de áudio")
                }
                finalizarChamada()
                return@withContext false
            }
            Log.d(TAG, "✅ Áudio inicializado com sucesso")
            
            emChamada = true
            
            // Inicia worker de envio serializado
            iniciarSender()
            
            // Inicia captura e reprodução
            Log.d(TAG, "Iniciando captura e reprodução...")
            iniciarCaptura()
            iniciarReproducao()
            
            // CRÍTICO: Dispara callback AQUI, após tudo inicializado
            withContext(Dispatchers.Main) {
                onConexaoEstabelecida?.invoke()
            }
            
            Log.d(TAG, "✅ Chamada iniciada completamente")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar chamada", e)
            withContext(Dispatchers.Main) {
                onConexaoFalhou?.invoke(e.message ?: "Erro desconhecido")
            }
            finalizarChamada()
            false
        }
    }
    
    /**
     * Registra o cliente no servidor TCP
     * Envia: [tamanho (4 bytes)][tipo=0 (1 byte)][usuarioId (4 bytes)]
     * Total: 9 bytes (protocolo do servidor)
     */
    private suspend fun registrarCliente() {
        // Monta payload: [tipo][usuarioId]
        val payload = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        payload.put(PACKET_TYPE_REGISTER)
        payload.putInt(usuarioId)
        
        // Monta pacote completo: [tamanho][payload]
        val packet = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(5) // tamanho do payload
        packet.put(payload.array())
        
        envioMutex.withLock {
            outputStream?.write(packet.array())
            outputStream?.flush()
        }
        
        Log.d(TAG, "Cliente registrado: usuarioId=$usuarioId")
    }
    
    /**
     * Inicializa AudioRecord e AudioTrack
     * Retorna true se bem-sucedido, false caso contrário
     */
    private fun inicializarAudio(): Boolean {
        return try {
            Log.d(TAG, "→ Verificando suporte de áudio...")
            
            // Calcula buffer mínimo para AudioRecord
            val minBufferSizeRecord = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT
            )
            
            if (minBufferSizeRecord == AudioRecord.ERROR ||
                minBufferSizeRecord == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Erro ao calcular buffer AudioRecord: $minBufferSizeRecord")
                return false
            }
            
            Log.d(TAG, "→ Buffer AudioRecord: $minBufferSizeRecord bytes")
            
            // Tenta criar AudioRecord com diferentes fontes se VOICE_COMMUNICATION falhar
            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )
            
            var recordCriado = false
            for (source in audioSources) {
                try {
                    Log.d(TAG, "→ Tentando AudioSource: $source")
                    
                    audioRecord = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        AUDIO_FORMAT,
                        maxOf(minBufferSizeRecord, BUFFER_SIZE * 2)
                    )
                    
                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "✅ AudioRecord criado com source=$source, estado=${audioRecord?.state}")
                        recordCriado = true
                        break
                    } else {
                        Log.w(TAG, "⚠️ AudioRecord falhou com source=$source, estado=${audioRecord?.state}")
                        audioRecord?.release()
                        audioRecord = null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Exceção ao tentar source=$source: ${e.message}")
                    audioRecord?.release()
                    audioRecord = null
                }
            }
            
            if (!recordCriado) {
                Log.e(TAG, "❌ Não foi possível criar AudioRecord com nenhuma fonte")
                return false
            }
            
            // Calcula buffer mínimo para AudioTrack
            val minBufferSizeTrack = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT
            )
            
            if (minBufferSizeTrack == AudioTrack.ERROR ||
                minBufferSizeTrack == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Erro ao calcular buffer AudioTrack: $minBufferSizeTrack")
                return false
            }
            
            Log.d(TAG, "→ Buffer AudioTrack: $minBufferSizeTrack bytes")
            
            // Cria AudioTrack
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_OUT,
                AUDIO_FORMAT,
                maxOf(minBufferSizeTrack, BUFFER_SIZE * 4),
                AudioTrack.MODE_STREAM
            )
            
            // Verifica estado do AudioTrack
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioTrack não inicializado. Estado: ${audioTrack?.state}")
                audioTrack?.release()
                audioTrack = null
                return false
            }
            
            Log.d(TAG, "✅ AudioTrack criado. Estado: ${audioTrack?.state}")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao inicializar áudio", e)
            false
        }
    }
    
    /**
     * Inicia worker que processa fila de envio de forma serializada
     */
    private fun iniciarSender() {
        senderJob = scope.launch {
            try {
                Log.d(TAG, "Worker de envio iniciado")
                
                var pacotesEnviados = 0
                var bytesTotais = 0L
                
                for (packet in filaEnvio) {
                    try {
                        envioMutex.withLock {
                            outputStream?.write(packet)
                            outputStream?.flush()
                        }
                        
                        pacotesEnviados++
                        bytesTotais += packet.size
                        
                        if (pacotesEnviados % 50 == 0) {
                            Log.d(TAG, "📤 Enviados: $pacotesEnviados pacotes, ${bytesTotais / 1024}KB")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao enviar pacote", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no worker de envio", e)
            } finally {
                Log.d(TAG, "Worker de envio finalizado")
            }
        }
    }
    
    /**
     * Inicia captura de áudio do microfone e envio para servidor
     */
    private fun iniciarCaptura() {
        captureJob = scope.launch {
            try {
                val record = audioRecord
                if (record == null) {
                    Log.e(TAG, "❌ AudioRecord é null")
                    return@launch
                }
                
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioRecord não inicializado. Estado: ${record.state}")
                    return@launch
                }
                
                Log.d(TAG, "🎤 Iniciando gravação...")
                record.startRecording()
                
                delay(50) // Pequeno delay para garantir start
                
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.e(TAG, "❌ AudioRecord NÃO está gravando. Estado: ${record.recordingState}")
                    return@launch
                }
                
                Log.d(TAG, "✅ 🎤 Gravação iniciada com sucesso!")
                
                val buffer = ByteArray(BUFFER_SIZE)
                var pacotesCapturados = 0
                var bytesCapturados = 0L
                var errosConsecutivos = 0
                
                while (emChamada && isActive) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    
                    when {
                        bytesRead > 0 -> {
                            errosConsecutivos = 0
                            pacotesCapturados++
                            bytesCapturados += bytesRead
                            
                            val audioCopy = buffer.copyOf(bytesRead)
                            enviarAudio(audioCopy, bytesRead)
                            
                            if (pacotesCapturados == 1) {
                                Log.d(TAG, "🎤 PRIMEIRO pacote capturado: $bytesRead bytes")
                            }
                            
                            if (pacotesCapturados % 50 == 0) {
                                Log.d(TAG, "🎤 Capturados: $pacotesCapturados pacotes, ${bytesCapturados / 1024}KB")
                            }
                        }
                        
                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "❌ AudioRecord: operação inválida")
                            errosConsecutivos++
                        }
                        
                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "❌ AudioRecord: valor inválido")
                            errosConsecutivos++
                        }
                        
                        bytesRead == AudioRecord.ERROR_DEAD_OBJECT -> {
                            Log.e(TAG, "❌ AudioRecord: objeto morto")
                            break
                        }
                        
                        else -> {
                            Log.w(TAG, "⚠️ AudioRecord retorno inesperado: $bytesRead")
                            errosConsecutivos++
                        }
                    }
                    
                    if (errosConsecutivos >= 10) {
                        Log.e(TAG, "❌ Muitos erros consecutivos ($errosConsecutivos), parando")
                        break
                    }
                }
                
                Log.d(TAG, "🎤 Captura finalizada. Total: $pacotesCapturados pacotes, ${bytesCapturados / 1024}KB")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na captura de áudio", e)
            } finally {
                try {
                    audioRecord?.stop()
                    Log.d(TAG, "AudioRecord parado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioRecord", e)
                }
            }
        }
    }
    
    /**
     * Envia pacote de áudio para o servidor via fila
     */
    private suspend fun enviarAudio(audioData: ByteArray, size: Int) {
        try {
            if (size <= 0 || size > BUFFER_SIZE) {
                Log.w(TAG, "Tamanho inválido: $size bytes")
                return
            }
            
            val payloadSize = 1 + 4 + size
            val payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            payload.put(PACKET_TYPE_AUDIO)
            payload.putInt(chamadaId)
            payload.put(audioData, 0, size)
            
            val packet = ByteBuffer.allocate(4 + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(payloadSize)
            packet.put(payload.array())
            
            val enviado = filaEnvio.trySend(packet.array()).isSuccess
            
            if (!enviado) {
                Log.w(TAG, "Fila cheia, pacote descartado")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enfileirar áudio", e)
        }
    }
    
    /**
     * Inicia reprodução de áudio recebido do servidor
     */
    private fun iniciarReproducao() {
        playbackJob = scope.launch {
            try {
                val track = audioTrack
                if (track == null) {
                    Log.e(TAG, "❌ AudioTrack é null")
                    return@launch
                }
                
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioTrack não inicializado. Estado: ${track.state}")
                    return@launch
                }
                
                Log.d(TAG, "🔊 Iniciando reprodução...")
                track.play()
                
                delay(50)
                
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.e(TAG, "❌ AudioTrack NÃO está reproduzindo. Estado: ${track.playState}")
                    return@launch
                }
                
                Log.d(TAG, "✅ 🔊 Reprodução iniciada com sucesso!")
                
                var pacotesRecebidos = 0
                
                while (emChamada && isActive) {
                    receberEReproducirAudio()
                    pacotesRecebidos++
                    
                    if (pacotesRecebidos == 1) {
                        Log.d(TAG, "🔊 PRIMEIRO pacote recebido")
                    }
                    
                    if (pacotesRecebidos % 50 == 0) {
                        Log.d(TAG, "🔊 Recebidos: $pacotesRecebidos pacotes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na reprodução", e)
            } finally {
                try {
                    audioTrack?.stop()
                    Log.d(TAG, "AudioTrack parado")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioTrack", e)
                }
            }
        }
    }
    
    /**
     * Recebe áudio do servidor e reproduz
     */
    private suspend fun receberEReproducirAudio() {
        try {
            val tamanhoBytes = ByteArray(4)
            inputStream?.readFully(tamanhoBytes)
            val tamanho = ByteBuffer.wrap(tamanhoBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            if (tamanho <= 4) {
                Log.w(TAG, "Pacote inválido: $tamanho bytes")
                return
            }
            
            val clientIdBytes = ByteArray(4)
            inputStream?.readFully(clientIdBytes)
            val clientId = ByteBuffer.wrap(clientIdBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            val audioSize = tamanho - 4
            
            if (audioSize <= 0 || audioSize > BUFFER_SIZE * 4) {
                Log.w(TAG, "Áudio inválido: $audioSize bytes (client=$clientId)")
                return
            }
            
            val audioData = ByteArray(audioSize)
            inputStream?.readFully(audioData)
            
            val audioShorts = bytesToShorts(audioData)
            adicionarAoMixing(clientId, audioShorts)
            
            val mixedAudio = mixar()
            if (mixedAudio.isNotEmpty()) {
                reproduzirAudio(mixedAudio)
            }
            
        } catch (e: EOFException) {
            Log.e(TAG, "Conexão encerrada pelo servidor")
            finalizarChamada()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber/reproduzir", e)
        }
    }
    
    private fun adicionarAoMixing(clientId: Int, audioData: ShortArray) {
        if (!mixingBuffers.containsKey(clientId)) {
            mixingBuffers[clientId] = LinkedBlockingQueue(100)
        }
        
        val queue = mixingBuffers[clientId]!!
        if (queue.size >= 100) {
            queue.poll()
        }
        
        queue.offer(audioData)
    }
    
    private fun mixar(): ShortArray {
        if (mixingBuffers.isEmpty()) {
            return ShortArray(0)
        }
        
        val mixedBuffer = ShortArray(BUFFER_SIZE / 2)
        var hasData = false
        
        mixingBuffers.forEach { (_, queue) ->
            queue.poll()?.let { buffer ->
                hasData = true
                for (i in 0 until minOf(buffer.size, mixedBuffer.size)) {
                    val mixed = mixedBuffer[i].toInt() + buffer[i].toInt()
                    mixedBuffer[i] = mixed.coerceIn(
                        Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }
        
        return if (hasData) mixedBuffer else ShortArray(0)
    }
    
    private fun reproduzirAudio(audioData: ShortArray) {
        if (audioData.isEmpty()) return
        
        try {
            val audioBytes = shortsToBytes(audioData)
            audioTrack?.write(audioBytes, 0, audioBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir", e)
        }
    }
    
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }
    
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
    
    fun finalizarChamada() {
        Log.d(TAG, "Finalizando chamada")
        
        emChamada = false
        
        captureJob?.cancel()
        playbackJob?.cancel()
        senderJob?.cancel()
        
        filaEnvio.close()
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar áudio", e)
        }
        
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar conexão", e)
        }
        
        socket = null
        outputStream = null
        inputStream = null
        
        mixingBuffers.clear()
        
        onChamadaFinalizada?.invoke()
        
        Log.d(TAG, "Chamada finalizada")
    }
    
    fun cleanup() {
        finalizarChamada()
        scope.cancel()
    }
}
