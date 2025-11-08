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
            
            Log.d(TAG, "Conectando ao servidor: $serverHost:$serverPort")
            
            // Conecta ao servidor TCP
            socket = Socket(serverHost, serverPort).apply {
                // CRÍTICO: Desabilita algoritmo de Nagle para envio imediato
                tcpNoDelay = true
                // Define timeout para evitar travamentos
                soTimeout = 5000
                // Mantém conexão ativa
                keepAlive = true
            }
            
            // SEM BufferedOutputStream - stream direto para envio imediato
            outputStream = DataOutputStream(socket!!.getOutputStream())
            inputStream = DataInputStream(socket!!.getInputStream())
            
            Log.d(TAG, "Socket configurado: tcpNoDelay=true")
            
            // Registra cliente no servidor
            registrarCliente()
            
            // Inicializa componentes de áudio
            inicializarAudio()
            
            emChamada = true
            
            // Inicia worker de envio serializado
            iniciarSender()
            
            // Inicia captura e reprodução
            iniciarCaptura()
            iniciarReproducao()
            
            withContext(Dispatchers.Main) {
                onConexaoEstabelecida?.invoke()
            }
            
            Log.d(TAG, "Chamada iniciada com sucesso")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar chamada", e)
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
        
        Log.d(TAG, "Cliente registrado: usuarioId=$usuarioId (9 bytes enviados)")
    }
    
    /**
     * Inicializa AudioRecord e AudioTrack
     */
    private fun inicializarAudio() {
        val minBufferSizeRecord = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            maxOf(minBufferSizeRecord, BUFFER_SIZE * 2)
        )
        
        val minBufferSizeTrack = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT
        )
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE,
            CHANNEL_OUT,
            AUDIO_FORMAT,
            maxOf(minBufferSizeTrack, BUFFER_SIZE * 4),
            AudioTrack.MODE_STREAM
        )
        
        Log.d(TAG, "AudioRecord e AudioTrack inicializados")
    }
    
    /**
     * Inicia worker que processa fila de envio de forma serializada
     */
    private fun iniciarSender() {
        senderJob = scope.launch {
            try {
                Log.d(TAG, "Worker de envio iniciado")
                
                var pacotesEnviados = 0
                
                for (packet in filaEnvio) {
                    try {
                        // Envia pacote de forma thread-safe e com flush imediato
                        envioMutex.withLock {
                            outputStream?.write(packet)
                            outputStream?.flush() // FLUSH IMEDIATO
                        }
                        
                        pacotesEnviados++
                        
                        if (pacotesEnviados % 50 == 0) {
                            Log.d(TAG, "Pacotes enviados: $pacotesEnviados, último tamanho: ${packet.size} bytes")
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao enviar pacote da fila", e)
                        // Continua processando próximos pacotes
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
                audioRecord?.startRecording()
                Log.d(TAG, "Captura de áudio iniciada")
                
                val buffer = ByteArray(BUFFER_SIZE)
                var pacotesCapturados = 0
                
                while (emChamada && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    
                    if (bytesRead > 0) {
                        pacotesCapturados++
                        
                        // Cria cópia do buffer antes de enfileirar
                        val audioCopy = buffer.copyOf(bytesRead)
                        enviarAudio(audioCopy, bytesRead)
                        
                        if (pacotesCapturados % 50 == 0) {
                            Log.d(TAG, "Pacotes capturados: $pacotesCapturados, último: $bytesRead bytes")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na captura de áudio", e)
            } finally {
                audioRecord?.stop()
                Log.d(TAG, "Captura de áudio finalizada")
            }
        }
    }
    
    /**
     * Envia pacote de áudio para o servidor via fila
     * Formato: [tamanho][tipo=1 (1 byte)][chamadaId (4 bytes)][dados de áudio]
     * Todos os inteiros em LITTLE_ENDIAN
     */
    private suspend fun enviarAudio(audioData: ByteArray, size: Int) {
        try {
            // Valida tamanho
            if (size <= 0 || size > BUFFER_SIZE) {
                Log.w(TAG, "Tamanho de áudio inválido: $size bytes, ignorando")
                return
            }
            
            // Monta payload: [tipo][chamadaId][áudio]
            val payloadSize = 1 + 4 + size // tipo + chamadaId + áudio
            val payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            payload.put(PACKET_TYPE_AUDIO)
            payload.putInt(chamadaId)
            payload.put(audioData, 0, size)
            
            // Monta pacote completo: [tamanho][payload]
            val packet = ByteBuffer.allocate(4 + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(payloadSize)
            packet.put(payload.array())
            
            // Enfileira para envio serializado
            val enviado = filaEnvio.trySend(packet.array()).isSuccess
            
            if (!enviado) {
                Log.w(TAG, "Fila de envio cheia, pacote descartado")
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
                audioTrack?.play()
                Log.d(TAG, "Reprodução de áudio iniciada")
                
                while (emChamada && isActive) {
                    receberEReproducirAudio()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na reprodução de áudio", e)
            } finally {
                audioTrack?.stop()
                Log.d(TAG, "Reprodução de áudio finalizada")
            }
        }
    }
    
    /**
     * Recebe áudio do servidor e reproduz
     * Servidor envia: [tamanho (4 bytes)][clientId (4 bytes)][dados de áudio]
     * Todos os inteiros em LITTLE_ENDIAN (padrão Delphi)
     * 
     * Para chamadas em grupo, faz mixing de múltiplos streams
     */
    private suspend fun receberEReproducirAudio() {
        try {
            // Lê tamanho total do pacote (4 bytes, LITTLE_ENDIAN)
            val tamanhoBytes = ByteArray(4)
            inputStream?.readFully(tamanhoBytes)
            val tamanho = ByteBuffer.wrap(tamanhoBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            if (tamanho <= 4) {
                Log.w(TAG, "Tamanho de pacote inválido: $tamanho bytes")
                return
            }
            
            // Lê ID do cliente que enviou o áudio (4 bytes, LITTLE_ENDIAN)
            val clientIdBytes = ByteArray(4)
            inputStream?.readFully(clientIdBytes)
            val clientId = ByteBuffer.wrap(clientIdBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            // Calcula tamanho do áudio
            val audioSize = tamanho - 4 // tamanho total - clientId
            
            if (audioSize <= 0 || audioSize > BUFFER_SIZE * 4) {
                Log.w(TAG, "Tamanho de áudio inválido: $audioSize bytes (clientId=$clientId)")
                return
            }
            
            // Lê dados de áudio (bloqueante)
            val audioData = ByteArray(audioSize)
            inputStream?.readFully(audioData)
            
            Log.d(TAG, "Áudio recebido: clientId=$clientId, tamanho=$audioSize bytes")
            
            // Converte bytes para shorts (PCM 16-bit)
            val audioShorts = bytesToShorts(audioData)
            
            // Adiciona ao buffer de mixing do participante
            adicionarAoMixing(clientId, audioShorts)
            
            // Faz mixing e reproduz
            val mixedAudio = mixar()
            
            if (mixedAudio.isNotEmpty()) {
                reproduzirAudio(mixedAudio)
            }
            
        } catch (e: EOFException) {
            Log.e(TAG, "Conexão encerrada pelo servidor")
            finalizarChamada()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber/reproduzir áudio", e)
        }
    }
    
    /**
     * Adiciona áudio ao buffer de mixing de um participante
     */
    private fun adicionarAoMixing(clientId: Int, audioData: ShortArray) {
        if (!mixingBuffers.containsKey(clientId)) {
            mixingBuffers[clientId] = LinkedBlockingQueue(100)
        }
        
        // Remove buffers antigos se fila estiver cheia
        val queue = mixingBuffers[clientId]!!
        if (queue.size >= 100) {
            queue.poll()
        }
        
        queue.offer(audioData)
    }
    
    /**
     * Mixa áudios de todos os participantes
     * Soma os samples e aplica clipping para evitar distorção
     */
    private fun mixar(): ShortArray {
        if (mixingBuffers.isEmpty()) {
            return ShortArray(0)
        }
        
        val mixedBuffer = ShortArray(BUFFER_SIZE / 2) // PCM 16-bit = 2 bytes por sample
        var hasData = false
        
        // Soma samples de todos os participantes
        mixingBuffers.forEach { (_, queue) ->
            queue.poll()?.let { buffer ->
                hasData = true
                for (i in 0 until minOf(buffer.size, mixedBuffer.size)) {
                    // Soma com clipping
                    val mixed = mixedBuffer[i].toInt() + buffer[i].toInt()
                    mixedBuffer[i] = mixed.coerceIn(
                        Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }
        
        return if (hasData) mixedBuffer else ShortArray(0)
    }
    
    /**
     * Reproduz áudio mixado
     */
    private fun reproduzirAudio(audioData: ShortArray) {
        if (audioData.isEmpty()) return
        
        try {
            // Converte shorts para bytes
            val audioBytes = shortsToBytes(audioData)
            audioTrack?.write(audioBytes, 0, audioBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir áudio", e)
        }
    }
    
    /**
     * Converte array de bytes para shorts (PCM 16-bit little-endian)
     */
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }
    
    /**
     * Converte array de shorts para bytes (PCM 16-bit little-endian)
     */
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
    
    /**
     * Finaliza a chamada
     */
    fun finalizarChamada() {
        Log.d(TAG, "Finalizando chamada")
        
        emChamada = false
        
        // Cancela jobs
        captureJob?.cancel()
        playbackJob?.cancel()
        senderJob?.cancel()
        
        // Fecha canal de envio
        filaEnvio.close()
        
        // Para e libera recursos de áudio
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar recursos de áudio", e)
        }
        
        // Fecha conexão TCP
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
        
        // Limpa buffers de mixing
        mixingBuffers.clear()
        
        onChamadaFinalizada?.invoke()
        
        Log.d(TAG, "Chamada finalizada")
    }
    
    /**
     * Libera todos os recursos
     */
    fun cleanup() {
        finalizarChamada()
        scope.cancel()
    }
}
