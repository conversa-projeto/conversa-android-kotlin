package com.conversa.conversa.data.chamada

import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

        // Constantes para suavização de áudio
        private const val FADE_SAMPLES = 32 // Número de samples para fade in/out

        // Constantes de buffering (ajustadas conforme análise Windows/Android)
        private const val MIN_BUFFER_START = 8192  // 8KB (~92ms) antes de começar reprodução (igual Windows)
        private const val MIN_BUFFER_RUNNING = 4096 // 4KB (~46ms) mínimo durante reprodução
        private const val MAX_BUFFER_THRESHOLD = 8192 // ~92ms de buffer máximo
        private const val PLAY_INTERVAL_MS = 23L // Intervalo entre reproduções (23ms)
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

    // Estado de inicialização do áudio (para evitar race conditions)
    private val _audioInicializadoFlow = MutableStateFlow(false)
    val audioInicializado: StateFlow<Boolean> = _audioInicializadoFlow

    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var senderJob: Job? = null
    private var receiveJob: Job? = null  // Nova coroutine para recepção TCP
    private var mixerJob: Job? = null    // Nova coroutine para mixing dedicado

    // Mixing (para chamadas em grupo)
    private val mixingBuffers = mutableMapOf<Int, LinkedBlockingQueue<ShortArray>>()

    // Controle de bytes acumulados por cliente (para gerenciar overflow)
    private val mixingBufferSizes = mutableMapOf<Int, Int>()

    // Timestamp do último pacote de áudio recebido por cliente
    private val lastAudioTimestamps = mutableMapOf<Int, Long>()
    val lastAudioTimestampsMap: Map<Int, Long> get() = lastAudioTimestamps.toMap()

    // Buffer intermediário entre mixer e reprodução
    private val mixedOutputBuffer = LinkedBlockingQueue<ShortArray>(10) // ~230ms máximo

    // Participantes mutados localmente
    private val participantesMutados = mutableSetOf<Int>()

    // Buffer anterior para suavização (evita cliques entre pacotes)
    private var lastSample: Short = 0
    
    // Callbacks
    var onConexaoTcpEstabelecida: (() -> Unit)? = null
    var onConexaoEstabelecida: (() -> Unit)? = null
    var onConexaoFalhou: ((String) -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    var onErroAudio: ((String) -> Unit)? = null
    
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
                trafficClass = 0xB8  // DSCP EF (Expedited Forwarding) - Prioridade VoIP
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

            // Configura AudioManager para usar earpiece por padrão
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false  // FALSE = earpiece (falante de ligação)
            Log.d(TAG, "📱 AudioManager configurado: MODE_IN_COMMUNICATION, earpiece ativo")

            // IMPORTANTE: Seta emChamada para true ANTES de iniciar áudio
            Log.d(TAG, "[$instanceId] Setando emChamada = true")
            emChamada = true
            Log.d(TAG, "[$instanceId] emChamada agora é: $emChamada")

            // Inicia worker de envio serializado
            iniciarSender()

            // Inicia captura e reprodução de áudio
            iniciarCapturaEReproducao()

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
        // Reseta o estado no início
        _audioInicializadoFlow.value = false

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

                    // Usa buffer pequeno para minimizar latência (não mais que BUFFER_SIZE)
                    val recordBufferSize = maxOf(minBufferSizeRecord, BUFFER_SIZE)

                    audioRecord = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        AUDIO_FORMAT,
                        recordBufferSize
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
                _audioInicializadoFlow.value = false
                return false
            }

            // Calcula buffer mínimo para AudioTrack
            val minBufferSizeTrack = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT
            )

            if (minBufferSizeTrack == AudioTrack.ERROR ||
                minBufferSizeTrack == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Erro ao calcular buffer AudioTrack: $minBufferSizeTrack")
                _audioInicializadoFlow.value = false
                return false
            }

            Log.d(TAG, "→ Buffer AudioTrack: $minBufferSizeTrack bytes")

            // Cria AudioTrack com buffer maior e consistente para reduzir underruns
            val trackBufferSize = maxOf(minBufferSizeTrack, BUFFER_SIZE * 6)

            // Configuração para usar earpiece (alto-falante de ligação) por padrão
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                trackBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            // Verifica estado do AudioTrack
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioTrack não inicializado. Estado: ${audioTrack?.state}")
                audioTrack?.release()
                audioTrack = null
                _audioInicializadoFlow.value = false
                return false
            }

            Log.d(TAG, "✅ AudioTrack criado. Estado: ${audioTrack?.state}")
            Log.d(TAG, "📦 Retornando TRUE de inicializarAudio()")
            Log.d(TAG, "   - audioRecord final: ${if (audioRecord == null) "NULL" else "INICIALIZADO" }")
            Log.d(TAG, "   - audioTrack final: ${if (audioTrack == null) "NULL" else "INICIALIZADO" }")

            // Marca como inicializado apenas se AMBOS estiverem OK
            _audioInicializadoFlow.value = true
            Log.d(TAG, "✅ Estado audioInicializado = true")

            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao inicializar áudio", e)
            _audioInicializadoFlow.value = false
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
                val record = audioRecord
                if (record == null) {
                    Log.e(TAG, "❌ AudioRecord é null ao iniciar captura")
                    withContext(Dispatchers.Main) {
                        onErroAudio?.invoke("AudioRecord não inicializado")
                    }
                    return@launch
                }

                try {
                    record.startRecording()
                    Log.d(TAG, "Captura de áudio iniciada")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "❌ Erro ao iniciar gravação (IllegalStateException)", e)
                    withContext(Dispatchers.Main) {
                        onErroAudio?.invoke("Erro ao iniciar gravação: ${e.message}")
                    }
                    return@launch
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var pacotesCapturados = 0
                var errosConsecutivos = 0

                while (emChamada && isActive) {
                    try {
                        val bytesRead = record.read(buffer, 0, buffer.size)

                        when {
                            bytesRead > 0 && !capturaPausada -> {
                                pacotesCapturados++
                                errosConsecutivos = 0 // Reset contador de erros

                                // Cria cópia do buffer antes de enfileirar
                                val audioCopy = buffer.copyOf(bytesRead)
                                enviarAudio(audioCopy, bytesRead)

                                if (pacotesCapturados % 50 == 0) {
                                    Log.d(TAG, "Pacotes capturados: $pacotesCapturados, último: $bytesRead bytes")
                                }
                            }
                            bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                                Log.e(TAG, "❌ Erro: AudioRecord em estado inválido")
                                errosConsecutivos++
                                if (errosConsecutivos > 10) {
                                    Log.e(TAG, "❌ Muitos erros consecutivos, finalizando captura")
                                    withContext(Dispatchers.Main) {
                                        onErroAudio?.invoke("AudioRecord em estado inválido")
                                    }
                                    break
                                }
                                delay(100)
                            }
                            bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                                Log.e(TAG, "❌ Erro: Parâmetros inválidos no AudioRecord")
                                errosConsecutivos++
                                if (errosConsecutivos > 10) {
                                    break
                                }
                                delay(100)
                            }
                            capturaPausada -> {
                                delay(10)
                            }
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "❌ Erro durante leitura do AudioRecord", e)
                        errosConsecutivos++
                        if (errosConsecutivos > 10) {
                            withContext(Dispatchers.Main) {
                                onErroAudio?.invoke("Erro durante captura: ${e.message}")
                            }
                            break
                        }
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro fatal na captura de áudio", e)
                withContext(Dispatchers.Main) {
                    onErroAudio?.invoke("Erro fatal na captura: ${e.message}")
                }
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
     * Inicia recepção TCP de áudio (separada da reprodução)
     */
    private fun iniciarRecepcaoTCP() {
        receiveJob = scope.launch {
            try {
                Log.d(TAG, "🌐 Recepção TCP iniciada")
                var pacotesRecebidos = 0

                while (emChamada && isActive) {
                    try {
                        receberAudioDoServidor()
                        pacotesRecebidos++

                        if (pacotesRecebidos == 1) {
                            Log.d(TAG, "🌐 PRIMEIRO pacote TCP recebido")
                        }

                        if (pacotesRecebidos % 50 == 0) {
                            Log.d(TAG, "🌐 Pacotes TCP recebidos: $pacotesRecebidos")
                        }
                    } catch (e: EOFException) {
                        Log.e(TAG, "Conexão encerrada pelo servidor")
                        withContext(Dispatchers.Main) {
                            finalizarChamada()
                        }
                        break
                    } catch (e: SocketTimeoutException) {
                        delay(10)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao receber TCP: ${e.message}", e)
                        delay(10)
                    }
                }

                Log.d(TAG, "🌐 Recepção TCP finalizada. Total: $pacotesRecebidos pacotes")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na recepção TCP", e)
            }
        }
    }

    /**
     * Recebe áudio do servidor e adiciona ao buffer de mixing
     * (não reproduz diretamente - isso é feito pela coroutine de reprodução)
     */
    private suspend fun receberAudioDoServidor() {
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

            val audioShorts = bytesToShorts(audioData)
            adicionarAoMixing(clientId, audioShorts)

        } catch (e: EOFException) {
            throw e // Propaga para finalizar chamada
        } catch (e: SocketTimeoutException) {
            throw e // Propaga para retry
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber áudio: ${e.message}", e)
            throw e
        }
    }

    /**
     * Inicia mixing dedicado (processa a cada 23ms)
     */
    private fun iniciarMixer() {
        mixerJob = scope.launch {
            try {
                Log.d(TAG, "🎚️ Mixer iniciado")
                var proximoTempo = System.currentTimeMillis() + PLAY_INTERVAL_MS
                var ciclosMixing = 0

                while (emChamada && isActive) {
                    val agoraAtual = System.currentTimeMillis()

                    if (agoraAtual >= proximoTempo) {
                        // Só mixa se todos os participantes tiverem dados OU se algum buffer estiver muito cheio
                        if (todosParticipantesProntos() || algumBufferCheio()) {
                            val mixedAudio = mixar()
                            if (mixedAudio.isNotEmpty()) {
                                // Adiciona ao buffer de saída (não bloqueia se cheio)
                                if (!mixedOutputBuffer.offer(mixedAudio)) {
                                    // Se buffer de saída está cheio, descarta pacote mais antigo
                                    mixedOutputBuffer.poll()
                                    mixedOutputBuffer.offer(mixedAudio)
                                    Log.w(TAG, "⚠️ Buffer de saída cheio, descartando pacote antigo")
                                }

                                ciclosMixing++
                                if (ciclosMixing % 50 == 0) {
                                    Log.d(TAG, "🎚️ Ciclos de mixing: $ciclosMixing")
                                }
                            }
                        }

                        proximoTempo += PLAY_INTERVAL_MS

                        // Ressincronização se atrasou muito (>100ms)
                        if (agoraAtual > proximoTempo + 100) {
                            Log.w(TAG, "⚠️ Drift detectado no mixer, ressincronizando")
                            proximoTempo = agoraAtual + PLAY_INTERVAL_MS
                        }
                    }

                    val tempoEspera = proximoTempo - System.currentTimeMillis()
                    if (tempoEspera > 0) {
                        delay(minOf(tempoEspera, 50))
                    } else {
                        delay(1) // Yield mínimo
                    }
                }

                Log.d(TAG, "🎚️ Mixer finalizado. Total: $ciclosMixing ciclos")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro no mixer", e)
            }
        }
    }

    /**
     * Inicia reprodução de áudio (lê do buffer mixado com temporização)
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

                // Aguarda buffer inicial de MIN_BUFFER_START bytes (~92ms)
                Log.d(TAG, "⏳ Aguardando buffer inicial de $MIN_BUFFER_START bytes...")
                var bufferAcumulado = 0
                while (bufferAcumulado < MIN_BUFFER_START && emChamada && isActive) {
                    bufferAcumulado = mixingBufferSizes.values.sum()
                    if (bufferAcumulado < MIN_BUFFER_START) {
                        delay(10)
                    }
                }

                if (!emChamada || !isActive) {
                    Log.w(TAG, "⚠️ Chamada finalizada antes de atingir buffer inicial")
                    return@launch
                }

                Log.d(TAG, "✅ Buffer inicial atingido: $bufferAcumulado bytes")

                Log.d(TAG, "🔊 Iniciando reprodução...")
                track.play()

                delay(50)

                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.e(TAG, "❌ AudioTrack NÃO está reproduzindo. Estado: ${track.playState}")
                    withContext(Dispatchers.Main) {
                        onErroAudio?.invoke("AudioTrack não está reproduzindo")
                    }
                    return@launch
                }

                Log.d(TAG, "✅ 🔊 Reprodução iniciada com sucesso!")

                var proximoTempo = System.currentTimeMillis() + PLAY_INTERVAL_MS
                var pacotesReproduzidos = 0
                var underrunCount = 0
                var errosConsecutivos = 0

                while (emChamada && isActive) {
                    try {
                        val agoraAtual = System.currentTimeMillis()

                        if (agoraAtual >= proximoTempo) {
                            // Tenta ler áudio mixado do buffer de saída
                            val mixedAudio = mixedOutputBuffer.poll()

                            if (mixedAudio != null && !reproducaoPausada) {
                                try {
                                    reproduzirAudio(mixedAudio)
                                    pacotesReproduzidos++
                                    errosConsecutivos = 0 // Reset contador de erros

                                    if (pacotesReproduzidos == 1) {
                                        Log.d(TAG, "🔊 PRIMEIRO pacote reproduzido")
                                    }

                                    if (pacotesReproduzidos % 50 == 0) {
                                        Log.d(TAG, "🔊 Pacotes reproduzidos: $pacotesReproduzidos")
                                    }
                                } catch (e: IllegalStateException) {
                                    Log.e(TAG, "❌ Erro ao reproduzir (IllegalStateException)", e)
                                    errosConsecutivos++
                                    if (errosConsecutivos > 10) {
                                        Log.e(TAG, "❌ Muitos erros consecutivos, finalizando reprodução")
                                        withContext(Dispatchers.Main) {
                                            onErroAudio?.invoke("Erro crítico na reprodução")
                                        }
                                        break
                                    }
                                }
                            } else if (mixedAudio == null) {
                                // Buffer vazio (underrun)
                                underrunCount++
                                if (underrunCount % 20 == 0) {
                                    Log.w(TAG, "⚠️ Underruns detectados: $underrunCount (buffer de saída vazio)")
                                }
                            }

                            proximoTempo += PLAY_INTERVAL_MS

                            // Ressincronização se atrasou muito (>100ms)
                            if (agoraAtual > proximoTempo + 100) {
                                Log.w(TAG, "⚠️ Drift detectado na reprodução, ressincronizando")
                                proximoTempo = agoraAtual + PLAY_INTERVAL_MS
                            }
                        }

                        val tempoEspera = proximoTempo - System.currentTimeMillis()
                        if (tempoEspera > 0) {
                            delay(minOf(tempoEspera, 50))
                        } else {
                            delay(1) // Yield mínimo
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no loop de reprodução: ${e.message}", e)
                        errosConsecutivos++
                        if (errosConsecutivos > 10) {
                            withContext(Dispatchers.Main) {
                                onErroAudio?.invoke("Erro no loop de reprodução: ${e.message}")
                            }
                            break
                        }
                        delay(10)
                    }
                }

                Log.d(TAG, "🔊 Loop de reprodução finalizado")
                Log.d(TAG, "   - Pacotes reproduzidos: $pacotesReproduzidos")
                Log.d(TAG, "   - Underruns detectados: $underrunCount")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro na reprodução", e)
            } finally {
                try {
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.stop()
                        Log.d(TAG, "AudioTrack parado")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioTrack", e)
                }
            }
        }
    }
    
    /**
     * Verifica se todos os participantes têm pelo menos 1 pacote para mixar
     */
    private fun todosParticipantesProntos(): Boolean {
        if (mixingBuffers.isEmpty()) return false

        // Se tiver apenas 1 participante, sempre está pronto
        if (mixingBuffers.size == 1) return true

        // Todos os participantes devem ter pelo menos 1 pacote
        return mixingBuffers.all { (_, queue) -> queue.isNotEmpty() }
    }

    /**
     * Verifica se algum buffer está acima de 50% do threshold (para evitar delay excessivo)
     */
    private fun algumBufferCheio(): Boolean {
        val halfThreshold = MAX_BUFFER_THRESHOLD / 2
        return mixingBufferSizes.any { (_, size) -> size > halfThreshold }
    }

    private fun adicionarAoMixing(clientId: Int, audioData: ShortArray) {
        // Registra timestamp apenas se amplitude média indicar áudio real
        val amplitudeMedia = audioData.map { kotlin.math.abs(it.toInt()) }.average()
        if (amplitudeMedia > 100) {
            lastAudioTimestamps[clientId] = System.currentTimeMillis()
        }

        // Ignora áudio de participantes mutados localmente
        if (isParticipanteMutado(clientId)) {
            Log.d(TAG, "❌ MUTE: Ignorando áudio de clientId=$clientId (mutado localmente)")
            return
        }

        if (!mixingBuffers.containsKey(clientId)) {
            mixingBuffers[clientId] = LinkedBlockingQueue(10) // Reduzido para 10 pacotes (~230ms máximo)
            mixingBufferSizes[clientId] = 0
            Log.d(TAG, "✅ Novo buffer criado para clientId=$clientId")
        }

        val queue = mixingBuffers[clientId]!!
        var currentSize = mixingBufferSizes[clientId] ?: 0
        val audioBytes = audioData.size * 2 // ShortArray -> bytes

        // Se buffer está muito cheio (>MAX_BUFFER_THRESHOLD), descarta pacotes antigos até ficar abaixo do limite
        while (currentSize > MAX_BUFFER_THRESHOLD) {
            val discarded = queue.poll()
            if (discarded != null) {
                currentSize = (currentSize - (discarded.size * 2)).coerceAtLeast(0)
                mixingBufferSizes[clientId] = currentSize
                Log.d(TAG, "⚠️ Buffer overflow clientId=$clientId, descartando pacote antigo (tamanho buffer: $currentSize bytes)")
            } else {
                break
            }
        }

        // Adiciona novo pacote
        if (queue.offer(audioData)) {
            mixingBufferSizes[clientId] = (mixingBufferSizes[clientId] ?: 0) + audioBytes
        } else {
            Log.w(TAG, "⚠️ Não foi possível adicionar pacote para clientId=$clientId (fila cheia)")
        }
    }
    
    private fun mixar(): ShortArray {
        if (mixingBuffers.isEmpty()) {
            return ShortArray(0)
        }

        val mixedBuffer = ShortArray(BUFFER_SIZE / 2)
        var hasData = false

        // Coleta todos os buffers disponíveis
        val buffersToMix = mutableListOf<ShortArray>()
        mixingBuffers.forEach { (clientId, queue) ->
            queue.poll()?.let { buffer ->
                buffersToMix.add(buffer)
                hasData = true
                // Atualiza tamanho do buffer
                val currentSize = mixingBufferSizes[clientId] ?: 0
                mixingBufferSizes[clientId] = (currentSize - buffer.size * 2).coerceAtLeast(0)
            }
        }

        if (!hasData) {
            return ShortArray(0)
        }

        val numStreams = buffersToMix.size

        // Mixing com média (como no Windows)
        // CRÍTICO: Soma TODOS os samples primeiro, depois divide pelo número de streams
        for (i in 0 until mixedBuffer.size) {
            var mixedSample = 0
            var activeClients = 0

            // Soma todos os samples de todos os clientes nesta posição
            for (buffer in buffersToMix) {
                if (i < buffer.size) {
                    mixedSample += buffer[i].toInt()
                    activeClients++
                }
            }

            // Faz a MÉDIA dividindo pelo número de clientes ativos
            if (activeClients > 0) {
                mixedSample /= activeClients
            }

            // Clamp para evitar clipping
            mixedBuffer[i] = mixedSample.coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            ).toShort()
        }

        return mixedBuffer
    }
    
    private fun reproduzirAudio(audioData: ShortArray) {
        if (audioData.isEmpty()) return

        try {
            // Aplica suavização para evitar cliques entre pacotes
            val smoothedData = aplicarSuavizacao(audioData)
            val audioBytes = shortsToBytes(smoothedData)
            audioTrack?.write(audioBytes, 0, audioBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir", e)
        }
    }

    /**
     * Aplica suavização (crossfade) no início do buffer para evitar cliques
     * Faz transição suave do último sample do pacote anterior
     */
    private fun aplicarSuavizacao(audioData: ShortArray): ShortArray {
        if (audioData.isEmpty()) return audioData

        val smoothed = audioData.copyOf()
        val fadeLength = minOf(FADE_SAMPLES, smoothed.size)

        // Aplica fade-in no início do buffer
        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            val currentValue = smoothed[i].toFloat()
            val previousValue = lastSample.toFloat()

            // Crossfade: transição suave do último sample para o atual
            smoothed[i] = (previousValue * (1f - factor) + currentValue * factor).toInt().toShort()
        }

        // Salva o último sample para o próximo pacote
        lastSample = smoothed[smoothed.size - 1]

        return smoothed
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
        _audioInicializadoFlow.value = false

        // Cancela jobs de forma explícita
        captureJob?.cancel()
        playbackJob?.cancel()
        senderJob?.cancel()
        receiveJob?.cancel()
        mixerJob?.cancel()

        captureJob = null
        playbackJob = null
        senderJob = null
        receiveJob = null
        mixerJob = null

        // Fecha canal de envio
        try {
            filaEnvio.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar fila de envio: ${e.message}")
        }

        // Aguarda um pouco para threads finalizarem
        Thread.sleep(100)

        // Para e libera componentes de áudio
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                    Log.d(TAG, "AudioRecord parado")
                }
                it.release()
                Log.d(TAG, "AudioRecord liberado")
            }
            audioRecord = null

            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                    Log.d(TAG, "AudioTrack parado")
                }
                it.release()
                Log.d(TAG, "AudioTrack liberado")
            }
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

        // Limpa todos os buffers
        mixingBuffers.clear()
        mixingBufferSizes.clear()
        mixedOutputBuffer.clear()
        lastAudioTimestamps.clear()

        // Reseta estado de suavização
        lastSample = 0

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
     * Muta/desmuta o microfone
     */
    fun toggleMuteMicrofone(muted: Boolean) {
        if (muted) {
            pausarCaptura()
            Log.d(TAG, "🔇 Microfone MUTADO")
        } else {
            retormarCaptura()
            Log.d(TAG, "🔊 Microfone ATIVO")
        }
    }

    /**
     * Muta/desmuta o áudio (reprodução)
     */
    fun toggleMuteAudio(muted: Boolean) {
        if (muted) {
            pausarReproducao()
            Log.d(TAG, "🔇 Áudio MUTADO")
        } else {
            retormarReproducao()
            Log.d(TAG, "🔊 Áudio ATIVO")
        }
    }

    /**
     * Alterna entre earpiece (falante de ligação) e speakerphone (alto-falante)
     */
    fun toggleSpeaker(speakerOn: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn

            if (speakerOn) {
                Log.d(TAG, "📢 Alto-falante ATIVADO")
            } else {
                Log.d(TAG, "📱 Earpiece ATIVADO (falante de ligação)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alternar speaker", e)
        }
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
        scope.launch {
            Log.d(TAG, "[$instanceId] === INICIANDO CAPTURA E REPRODUÇÃO ===")
            Log.d(TAG, "[$instanceId] Estado emChamada: $emChamada")
            Log.d(TAG, "[$instanceId] Thread: ${Thread.currentThread().name}")

            // Verifica se já está capturando/reproduzindo
            if (captureJob?.isActive == true && playbackJob?.isActive == true &&
                receiveJob?.isActive == true && mixerJob?.isActive == true) {
                Log.w(TAG, "⚠️ Todas as coroutines JÁ ESTÃO ATIVAS! Ignorando chamada duplicada")
                return@launch
            }

            // ⏳ AGUARDA o áudio estar inicializado (proteção contra race condition)
            Log.d(TAG, "⏳ Aguardando áudio ser inicializado...")
            withTimeoutOrNull(5000) {
                audioInicializado.first { it == true }
            }

            Log.d(TAG, "[$instanceId] audioRecord: ${if (audioRecord == null) "NULL" else "OK (estado=${audioRecord?.state})" }")
            Log.d(TAG, "[$instanceId] audioTrack: ${if (audioTrack == null) "NULL" else "OK (estado=${audioTrack?.state})" }")
            Log.d(TAG, "captureJob ativo: ${captureJob?.isActive}")
            Log.d(TAG, "playbackJob ativo: ${playbackJob?.isActive}")
            Log.d(TAG, "receiveJob ativo: ${receiveJob?.isActive}")
            Log.d(TAG, "mixerJob ativo: ${mixerJob?.isActive}")

            // Verifica novamente se componentes estão OK após aguardar
            if (audioRecord == null || audioTrack == null) {
                Log.e(TAG, "❌ ERRO CRÍTICO: Componentes de áudio AINDA NULL após espera!")
                Log.e(TAG, "Tentando reinicializar componentes de áudio...")
                if (inicializarAudio()) {
                    Log.d(TAG, "✅ Componentes reinicializados com sucesso")
                } else {
                    Log.e(TAG, "❌ Falha ao reinicializar componentes")
                    return@launch
                }
            }

            Log.d(TAG, "✅ Áudio pronto para captura e reprodução")

            // Inicia todas as coroutines na ordem correta
            iniciarCaptura()        // 1. Captura áudio do microfone
            iniciarRecepcaoTCP()    // 2. Recebe áudio TCP e adiciona ao mixing
            iniciarMixer()          // 3. Mixa áudio a cada 23ms
            iniciarReproducao()     // 4. Reproduz áudio mixado a cada 23ms

            Log.d(TAG, "=== TODAS AS COROUTINES DISPARADAS ===")
        }
    }
    
    fun cleanup() {
        finalizarChamada()
        scope.cancel()
    }
}
