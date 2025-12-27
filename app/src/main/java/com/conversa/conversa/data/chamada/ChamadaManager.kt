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
import java.net.SocketTimeoutException
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
    
    // ID único da instância para debug
    private val instanceId = System.currentTimeMillis().toString().takeLast(6)
    private val identityCode = System.identityHashCode(this)
    
    init {
        Log.d(TAG, "[🆕 NOVA INSTÂNCIA] id=$instanceId, identityCode=$identityCode")
    }
    
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
    
    // Estado da chamada - @Volatile para garantir visibilidade entre threads
    @Volatile
    private var emChamada = false
        set(value) {
            Log.d(TAG, "[$instanceId] \u26a0\ufe0f emChamada mudando de $field para $value")
            Log.d(TAG, "[$instanceId]    Thread: ${Thread.currentThread().name}")
            field = value
        }
    private var chamadaId: Int = 0
    private var usuarioId: Int = 0
    private var capturaPausada: Boolean = false
    private var reproducaoPausada: Boolean = false
    
    // Conexão TCP
    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null
    
    // Sincronização thread-safe para envio TCP
    private val envioMutex = Mutex()

    // Fila de pacotes para envio serializado
    private var filaEnvio = Channel<ByteArray>(capacity = 100)
    
    // Áudio - @Volatile para garantir visibilidade entre threads
    @Volatile
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var audioTrack: AudioTrack? = null
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var senderJob: Job? = null
    
    // Mixing (para chamadas em grupo)
    private val mixingBuffers = mutableMapOf<Int, LinkedBlockingQueue<ShortArray>>()
    
    // Participantes mutados localmente
    private val participantesMutados = mutableSetOf<Int>()
    
    // Callbacks
    var onConexaoTcpEstabelecida: (() -> Unit)? = null
    var onConexaoEstabelecida: (() -> Unit)? = null
    var onConexaoFalhou: ((String) -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    
    /**
     * Recria o canal de envio (necessário após fechamento)
     */
    private fun recriarFilaEnvio() {
        try {
            filaEnvio.close()
        } catch (e: Exception) {
            // Ignora erro se já estiver fechado
        }
        filaEnvio = Channel(capacity = 100)
        Log.d(TAG, "[$instanceId] Canal de envio recriado")
    }

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

            Log.d(TAG, "[$instanceId] === INICIANDO CHAMADA ===")
            Log.d(TAG, "Servidor: $serverHost:$serverPort")
            Log.d(TAG, "ChamadaId: $chamadaId")
            Log.d(TAG, "UsuarioId: $usuarioId")

            // Recria o canal de envio
            recriarFilaEnvio()
            
            // Conecta ao servidor TCP
            socket = Socket(serverHost, serverPort).apply {
                tcpNoDelay = true
                soTimeout = 0  // 0 = sem timeout, aguarda indefinidamente (mas não trava a thread)
                keepAlive = true
                receiveBufferSize = BUFFER_SIZE * 8
            }
            
            outputStream = DataOutputStream(socket!!.getOutputStream())
            inputStream = DataInputStream(socket!!.getInputStream())
            
            Log.d(TAG, "Socket TCP conectado")
            
            // Dispara callback de conexão TCP estabelecida
            withContext(Dispatchers.Main) {
                onConexaoTcpEstabelecida?.invoke()
            }
            
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
            Log.d(TAG, "   - audioRecord: ${if (audioRecord == null) "NULL" else "OK (estado=${audioRecord?.state})" }")
            Log.d(TAG, "   - audioTrack: ${if (audioTrack == null) "NULL" else "OK (estado=${audioTrack?.state})" }")

            // IMPORTANTE: Seta emChamada para true ANTES de iniciar áudio
            Log.d(TAG, "[$instanceId] Setando emChamada = true")
            emChamada = true
            Log.d(TAG, "[$instanceId] emChamada agora é: $emChamada")

            // Inicia worker de envio serializado
            iniciarSender()

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
            Log.d(TAG, "📦 Retornando TRUE de inicializarAudio()")
            Log.d(TAG, "   - audioRecord final: ${if (audioRecord == null) "NULL" else "INICIALIZADO" }")
            Log.d(TAG, "   - audioTrack final: ${if (audioTrack == null) "NULL" else "INICIALIZADO" }")

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
                try {
                    // Verifica estado antes de parar para evitar IllegalStateException
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord?.stop()
                        Log.d(TAG, "AudioRecord parado")
                    } else {
                        Log.d(TAG, "AudioRecord não estava gravando, estado: ${audioRecord?.recordingState}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioRecord", e)
                }
                Log.d(TAG, "Captura de áudio finalizada")
            }
        }
    }
    
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
        Log.d(TAG, "🔴 iniciarReproducao() chamado")
        Log.d(TAG, "   - audioTrack antes de launch: ${if (audioTrack == null) "NULL" else "OK"}")
        
        playbackJob = scope.launch {
            try {
                Log.d(TAG, "🔵 Dentro da coroutine de reprodução")
                val track = audioTrack
                Log.d(TAG, "   - audioTrack dentro da coroutine: ${if (track == null) "NULL" else "OK (estado=${track.state})" }")
                
                if (track == null) {
                    Log.e(TAG, "❌ AudioTrack é null DENTRO DA COROUTINE")
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
                Log.d(TAG, "   - emChamada: $emChamada")
                Log.d(TAG, "   - isActive: $isActive")
                Log.d(TAG, "   - Condição do loop: ${emChamada && isActive}")
                Log.d(TAG, "   - Entrando no loop de recepção...")
                
                var pacotesRecebidos = 0
                var tentativasVazias = 0
                var iteracoesLoop = 0
                
                while (emChamada && isActive) {
                    iteracoesLoop++
                    
                    if (iteracoesLoop == 1) {
                        Log.d(TAG, "🔵 PRIMEIRA iteração do loop de reprodução")
                    }
                    
                    try {
                        receberEReproducirAudio()
                        pacotesRecebidos++
                        tentativasVazias = 0
                        
                        if (pacotesRecebidos == 1) {
                            Log.d(TAG, "🔊 PRIMEIRO pacote recebido e processado")
                        }
                        
                        if (pacotesRecebidos % 50 == 0) {
                            Log.d(TAG, "🔊 Recebidos: $pacotesRecebidos pacotes")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no loop de reprodução: ${e.message}", e)
                        tentativasVazias++
                        
                        if (tentativasVazias > 100) {
                            Log.e(TAG, "Muitas falhas consecutivas, parando reprodução")
                            break
                        }
                        
                        delay(10)
                    }
                }
                
                Log.d(TAG, "🔊 Loop de reprodução finalizado")
                Log.d(TAG, "   - Total pacotes: $pacotesRecebidos")
                Log.d(TAG, "   - Total iterações: $iteracoesLoop")
                Log.d(TAG, "   - emChamada ao sair: $emChamada")
                Log.d(TAG, "   - isActive ao sair: $isActive")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na reprodução", e)
            } finally {
                try {
                    // Verifica estado antes de parar para evitar IllegalStateException
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.stop()
                        Log.d(TAG, "AudioTrack parado")
                    } else {
                        Log.d(TAG, "AudioTrack não estava reproduzindo, estado: ${audioTrack?.playState}")
                    }
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
//        Log.d(TAG, "🔴 receberEReproducirAudio() CHAMADO")
        
        try {
            // Verifica se há dados disponíveis para leitura
            val stream = inputStream
            if (stream == null) {
                Log.e(TAG, "InputStream é null")
                delay(10)
                return
            }
            
            // Lê tamanho do pacote
            val tamanhoBytes = ByteArray(4)
            stream.readFully(tamanhoBytes)
            val tamanho = ByteBuffer.wrap(tamanhoBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            if (tamanho <= 4 || tamanho > (BUFFER_SIZE * 4 + 4)) {
                Log.w(TAG, "Pacote inválido: $tamanho bytes")
                return
            }
            
            // Lê ID do cliente
            val clientIdBytes = ByteArray(4)
            stream.readFully(clientIdBytes)
            val clientId = ByteBuffer.wrap(clientIdBytes).order(ByteOrder.LITTLE_ENDIAN).int
            
            val audioSize = tamanho - 4
            
            if (audioSize <= 0 || audioSize > BUFFER_SIZE * 4) {
                Log.w(TAG, "Áudio inválido: $audioSize bytes (client=$clientId)")
                return
            }
            
            // Lê dados de áudio
            val audioData = ByteArray(audioSize)
            stream.readFully(audioData)
            
            // LOG: Mostra qual clientId recebeu áudio
//            Log.d(TAG, "📦 Áudio recebido de clientId=$clientId, tamanho=$audioSize bytes")
            
            val audioShorts = bytesToShorts(audioData)
            adicionarAoMixing(clientId, audioShorts)
            
            val mixedAudio = mixar()
            if (mixedAudio.isNotEmpty() && !reproducaoPausada) {
                reproduzirAudio(mixedAudio)
            }
            
        } catch (e: EOFException) {
            Log.e(TAG, "Conexão encerrada pelo servidor")
            withContext(Dispatchers.Main) {
                finalizarChamada()
            }
        } catch (e: SocketTimeoutException) {
            delay(10)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber/reproduzir: ${e.message}", e)
            delay(10)
        }
    }
    
    private fun adicionarAoMixing(clientId: Int, audioData: ShortArray) {
        // Ignora áudio de participantes mutados localmente
        if (isParticipanteMutado(clientId)) {
            Log.d(TAG, "❌ MUTE: Ignorando áudio de clientId=$clientId (mutado localmente)")
            // Não adiciona ao buffer de mixing
            return
        }
        
        if (!mixingBuffers.containsKey(clientId)) {
            mixingBuffers[clientId] = LinkedBlockingQueue(100)
            Log.d(TAG, "✅ Novo buffer criado para clientId=$clientId")
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
        Log.d(TAG, "[$instanceId] Finalizando chamada")

        emChamada = false

        // Cancela jobs de forma explícita e aguarda
        captureJob?.cancel()
        playbackJob?.cancel()
        senderJob?.cancel()

        captureJob = null
        playbackJob = null
        senderJob = null

        // Fecha canal de envio
        try {
            filaEnvio.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar fila de envio: ${e.message}")
        }
        
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
            
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
            }
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
    
    fun pausarCaptura() {
        capturaPausada = true
        Log.d(TAG, "Captura pausada")
    }
    
    fun retormarCaptura() {
        capturaPausada = false
        Log.d(TAG, "Captura retomada")
    }
    
    fun pausarReproducao() {
        reproducaoPausada = true
        Log.d(TAG, "Reprodução pausada")
    }
    
    fun retormarReproducao() {
        reproducaoPausada = false
        Log.d(TAG, "Reprodução retomada")
    }
    
    /**
     * Atualiza a lista de participantes mutados localmente
     */
    fun atualizarParticipantesMutados(mutados: Set<Int>) {
        participantesMutados.clear()
        participantesMutados.addAll(mutados)
        Log.d(TAG, "🔇 Participantes mutados atualizados: $mutados")
        Log.d(TAG, "🔇 Total mutados: ${participantesMutados.size}")
    }
    
    /**
     * Verifica se um participante está mutado localmente
     */
    private fun isParticipanteMutado(clientId: Int): Boolean {
        val mutado = participantesMutados.contains(clientId)
        if (mutado) {
            Log.d(TAG, "🔇 Verificando clientId=$clientId -> MUTADO")
        }
        return mutado
    }
    
    fun iniciarCapturaEReproducao() {
        Log.d(TAG, "[$instanceId] === INICIANDO CAPTURA E REPRODUÇÃO ===")
        Log.d(TAG, "[$instanceId] Estado emChamada: $emChamada")
        Log.d(TAG, "[$instanceId] Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "[$instanceId] audioRecord: ${if (audioRecord == null) "NULL" else "OK (estado=${audioRecord?.state})" }")
        Log.d(TAG, "[$instanceId] audioTrack: ${if (audioTrack == null) "NULL" else "OK (estado=${audioTrack?.state})" }")
        Log.d(TAG, "captureJob ativo: ${captureJob?.isActive}")
        Log.d(TAG, "playbackJob ativo: ${playbackJob?.isActive}")

        // Verifica se já está capturando/reproduzindo
        if (captureJob?.isActive == true && playbackJob?.isActive == true) {
            Log.w(TAG, "⚠️ Captura e reprodução JÁ ESTÃO ATIVAS! Ignorando chamada duplicada")
            return
        }

        if (audioRecord == null || audioTrack == null) {
            Log.e(TAG, "❌ ERRO CRÍTICO: Componentes de áudio são NULL!")
            Log.e(TAG, "Tentando reinicializar componentes de áudio...")
            if (inicializarAudio()) {
                Log.d(TAG, "✅ Componentes reinicializados com sucesso")
            } else {
                Log.e(TAG, "❌ Falha ao reinicializar componentes")
                return
            }
        }

        iniciarCaptura()
        iniciarReproducao()

        Log.d(TAG, "=== CAPTURA E REPRODUÇÃO DISPARADAS ===")
    }
    
    fun cleanup() {
        finalizarChamada()
        scope.cancel()
    }
}
