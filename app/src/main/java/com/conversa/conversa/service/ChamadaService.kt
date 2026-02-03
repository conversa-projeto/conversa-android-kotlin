package com.conversa.conversa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.conversa.conversa.R
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.*
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.ui.chamada.ChamadaActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import com.conversa.conversa.service.NotificationConstants.CHANNEL_ID_CHAMADAS
import com.conversa.conversa.service.NotificationConstants.NOTIFICATION_ID_CHAMADA_FOREGROUND

/**
 * Service dedicado para gerenciar chamadas de voz.
 *
 * Responsabilidades:
 * - Gerenciar estado completo da chamada
 * - Conexão TCP para transmissão de áudio (porta 9090)
 * - Captura e reprodução de áudio (AudioRecord/AudioTrack)
 * - Mixing de múltiplos streams (chamadas em grupo)
 * - Notificações (recebida, em andamento, perdida)
 * - Ringtone e vibração
 * - Timer da chamada
 * - Sensor de proximidade (via WakeLock)
 *
 * Arquitetura:
 * - Foreground Service com notificação persistente
 * - Comunicação via LocalBinder + StateFlows
 * - Recebe eventos do SocketService via Intents
 */
class ChamadaService : Service() {

    companion object {
        private const val TAG = "ChamadaService"

        // Actions para Intents do SocketService
        const val ACTION_CHAMADA_RECEBIDA = "com.conversa.chamada.RECEBIDA"
        const val ACTION_CHAMADA_FINALIZADA = "com.conversa.chamada.FINALIZADA"
        const val ACTION_USUARIO_ENTROU = "com.conversa.chamada.USUARIO_ENTROU"
        const val ACTION_USUARIO_SAIU = "com.conversa.chamada.USUARIO_SAIU"
        const val ACTION_USUARIO_RECUSOU = "com.conversa.chamada.USUARIO_RECUSOU"

        // Actions para controle da chamada
        const val ACTION_INICIAR_CHAMADA = "com.conversa.chamada.INICIAR_CHAMADA"
        const val ACTION_ACEITAR = "com.conversa.chamada.ACEITAR"
        const val ACTION_RECUSAR = "com.conversa.chamada.RECUSAR"
        const val ACTION_ENCERRAR = "com.conversa.chamada.ENCERRAR"
        const val ACTION_TOGGLE_MUTE = "com.conversa.chamada.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.conversa.chamada.TOGGLE_SPEAKER"

        // Extras
        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_DESTINATARIOS = "destinatarios"

        // Configurações de áudio TCP
        private const val TCP_PORT = 9090
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 2048 // ~23ms de áudio

        // Tipos de pacote TCP
        private const val PACKET_TYPE_REGISTER: Byte = 0
        private const val PACKET_TYPE_AUDIO: Byte = 1

        // Constantes de buffering
        private const val MIN_BUFFER_START = 12288  // 12KB (~140ms) antes de iniciar reprodução
        private const val MIN_BUFFER_RUNNING = 4096 // 4KB (~46ms) mínimo durante reprodução
        private const val MAX_BUFFER_THRESHOLD = 8192 // ~92ms buffer máximo
        private const val PLAY_INTERVAL_MS = 23L // Intervalo entre reproduções (23ms)

        // Constantes para suavização de áudio
        private const val FADE_SAMPLES = 32 // Número de samples para fade in/out

        // Threshold para Voice Activity Detection
        private const val VAD_THRESHOLD = 10
    }

    // Binder para Activities
    inner class LocalBinder : Binder() {
        fun getService(): ChamadaService = this@ChamadaService
    }

    private val binder = LocalBinder()

    // Estado da chamada
    enum class EstadoChamadaService {
        IDLE,                    // Sem chamada ativa
        RECEBENDO_CHAMADA,       // Chamada recebida, aguardando ação do usuário
        INICIANDO_CHAMADA,       // Usuário iniciou chamada, aguardando outros
        CONECTANDO_AUDIO,        // Conectando ao servidor TCP
        EM_CHAMADA,              // Chamada em andamento
        FINALIZANDO              // Encerrando chamada
    }

    // StateFlows públicos
    private val _estadoFlow = MutableStateFlow(EstadoChamadaService.IDLE)
    val estadoFlow: StateFlow<EstadoChamadaService> = _estadoFlow.asStateFlow()

    // Helper para atualizar estado com log
    private fun atualizarEstado(novoEstado: EstadoChamadaService) {
        val estadoAnterior = _estadoFlow.value
        if (estadoAnterior != novoEstado) {
            Log.d(TAG, "📊 Estado: $estadoAnterior → $novoEstado")
            _estadoFlow.value = novoEstado
        }
    }

    private val _chamadaAtualFlow = MutableStateFlow<ChamadaResponse?>(null)
    val chamadaAtualFlow: StateFlow<ChamadaResponse?> = _chamadaAtualFlow.asStateFlow()

    private val _eventosFlow = MutableSharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI>(replay = 0)
    val eventosFlow: SharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI> = _eventosFlow.asSharedFlow()

    private val _timerFlow = MutableStateFlow("00:00")
    val timerFlow: StateFlow<String> = _timerFlow.asStateFlow()

    private val _participantesFlow = MutableStateFlow<List<ParticipanteItem>>(emptyList())
    val participantesFlow: StateFlow<List<ParticipanteItem>> = _participantesFlow.asStateFlow()

    // Estado interno
    private var chamadaAtual: ChamadaResponse? = null
        set(value) {
            field = value
            _chamadaAtualFlow.value = value
            atualizarListaParticipantes()
        }

    private var usuarioIdAtual: Int = 0
    private var chamadaIdAtual: Int = 0
    private var isMutedMicrofone = false
    private var isSpeakerOn = false
    private var primeiroParticipanteEntrou = false

    // Componentes de áudio TCP
    @Volatile
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var audioTrack: AudioTrack? = null

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: DataInputStream? = null

    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var senderJob: Job? = null
    private var receiveJob: Job? = null
    private var mixerJob: Job? = null
    private var timerJob: Job? = null

    // Sincronização
    private val envioMutex = Mutex()
    private var filaEnvio = Channel<ByteArray>(capacity = 100)

    // Mixing
    private val mixingBuffers = mutableMapOf<Int, LinkedBlockingQueue<ShortArray>>()
    private val mixingBufferSizes = mutableMapOf<Int, Int>()
    private val lastAudioTimestamps = mutableMapOf<Int, Long>()
    private val mixedOutputBuffer = LinkedBlockingQueue<ShortArray>(20) // ~460ms máximo

    // Controle de volume e mute por participante
    private val participantesAudio = mutableMapOf<Int, ParticipanteAudio>()

    // Buffer anterior para suavização
    private var lastSample: Short = 0

    // Flags de controle
    private var capturaPausada = false
    private var reproducaoPausada = false
    private var emChamada = false

    // Dependências
    private lateinit var userPreferences: UserPreferences
    private lateinit var api: ConversaApi
    private lateinit var ringtoneManager: ChamadaRingtoneManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager

    // WakeLock para manter CPU ativo
    private var wakeLock: PowerManager.WakeLock? = null

    // Timer
    private var chamadaIniciadaEm: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "📱 ChamadaService CRIADO")
        Log.d(TAG, "════════════════════════════════════════")

        // Inicializa dependências
        userPreferences = UserPreferences(applicationContext)
        api = com.conversa.conversa.data.api.RetrofitClient.api
        ringtoneManager = ChamadaRingtoneManager.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Cria canal de notificação
        criarCanalNotificacao()

        // Adquire WakeLock
        adquirirWakeLock()

        // Inicia foreground com notificação vazia
        startForeground(NOTIFICATION_ID_CHAMADA_FOREGROUND, criarNotificacaoForeground())

        Log.d(TAG, "Service criado com sucesso")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind()")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_CHAMADA_RECEBIDA -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
                val usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: ""

                // Verificação: ignora nova chamada se já houver uma chamada ativa
                if (chamadaIdAtual != 0 && chamadaIdAtual != chamadaId) {
                    Log.w(TAG, "⚠️ Ignorando nova chamada $chamadaId - já em chamada $chamadaIdAtual")
                    // TODO: Considerar recusar automaticamente via API
                    return START_NOT_STICKY
                }

                if (chamadaId != -1) {
                    processarChamadaRecebida(chamadaId, usuarioId, usuarioNome)
                }
            }

            ACTION_CHAMADA_FINALIZADA -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                if (chamadaId == chamadaIdAtual) {
                    finalizarChamadaENotificarAPI()
                }
            }

            ACTION_USUARIO_ENTROU -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
                if (chamadaId == chamadaIdAtual) {
                    processarUsuarioEntrou(usuarioId)
                }
            }

            ACTION_USUARIO_SAIU -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
                if (chamadaId == chamadaIdAtual) {
                    processarUsuarioSaiu(usuarioId)
                }
            }

            ACTION_USUARIO_RECUSOU -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
                if (chamadaId == chamadaIdAtual) {
                    processarUsuarioRecusou(usuarioId)
                }
            }

            ACTION_INICIAR_CHAMADA -> {
                val destinatariosIds = intent.getIntArrayExtra(EXTRA_DESTINATARIOS)?.toList() ?: emptyList()
                if (destinatariosIds.isNotEmpty()) {
                    iniciarChamada(destinatariosIds)
                }
            }
            ACTION_ACEITAR -> aceitarChamada()
            ACTION_RECUSAR -> recusarChamada()
            ACTION_ENCERRAR -> encerrarChamada()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_TOGGLE_SPEAKER -> toggleSpeaker()
        }

        // START_NOT_STICKY: não reinicia o serviço automaticamente se for morto
        // Chamadas serão tratadas por novos startService quando necessário
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "💀 ChamadaService DESTRUÍDO")
        Log.d(TAG, "════════════════════════════════════════")

        // Garante que flag seja resetada mesmo se finalizarChamadaInterno falhar
        SocketService.chamadaServiceAtivo = false

        finalizarChamadaInterno()
        liberarWakeLock()
        scope.cancel()

        super.onDestroy()
    }

    // ==================== MÉTODOS PÚBLICOS ====================

    /**
     * Inicia uma nova chamada (quem liga)
     */
    fun iniciarChamada(destinatariosIds: List<Int>) {
        scope.launch {
            try {
                // Atualiza flag para indicar que ChamadaService está ativo
                SocketService.chamadaServiceAtivo = true

                atualizarEstado(EstadoChamadaService.INICIANDO_CHAMADA)
                primeiroParticipanteEntrou = false

                val token = userPreferences.authToken.first() ?: run {
                    Log.e(TAG, "Token não disponível")
                    SocketService.chamadaServiceAtivo = false  // Reseta em caso de erro
                    return@launch
                }

                usuarioIdAtual = userPreferences.userId.first() ?: 0
                val apiUrl = userPreferences.apiUrl.first() ?: ""
                val tcpHost = extrairHost(apiUrl)

                Log.d(TAG, "=== INICIANDO CHAMADA ===")
                Log.d(TAG, "UsuarioId: $usuarioIdAtual")
                Log.d(TAG, "Destinatarios: $destinatariosIds")

                val todosUsuarios = destinatariosIds.toMutableList()
                if (usuarioIdAtual !in todosUsuarios) {
                    todosUsuarios.add(0, usuarioIdAtual)
                }

                val tipo = if (todosUsuarios.size == 2) 1 else 2
                val request = IniciarChamadaRequest(
                    tipo = tipo,
                    usuarios = todosUsuarios.map { UsuarioIdDto(it) }
                )

                val response = api.iniciarChamada("Bearer $token", request)

                if (response.isSuccessful) {
                    val chamada = response.body()!!
                    Log.d(TAG, "✅ Chamada criada via API: ${chamada.id}")

                    chamadaIdAtual = chamada.id
                    chamadaAtual = chamada

                    delay(200) // Aguarda servidor processar

                    conectarAudioTcp(tcpHost, chamada.id)
                } else {
                    Log.e(TAG, "❌ Erro API: ${response.code()}")
                    SocketService.chamadaServiceAtivo = false  // Reseta em caso de erro
                    atualizarEstado(EstadoChamadaService.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao iniciar chamada", e)
                SocketService.chamadaServiceAtivo = false  // Reseta em caso de erro
                atualizarEstado(EstadoChamadaService.IDLE)
            }
        }
    }

    /**
     * Aceita chamada recebida
     */
    fun aceitarChamada() {
        scope.launch {
            try {
                if (chamadaIdAtual == 0) {
                    Log.e(TAG, "Nenhuma chamada para aceitar")
                    return@launch
                }

                val token = userPreferences.authToken.first() ?: run {
                    Log.e(TAG, "Token não disponível")
                    return@launch
                }

                usuarioIdAtual = userPreferences.userId.first() ?: 0
                val apiUrl = userPreferences.apiUrl.first() ?: ""
                val tcpHost = extrairHost(apiUrl)

                Log.d(TAG, "=== ACEITANDO CHAMADA ===")
                Log.d(TAG, "ChamadaId: $chamadaIdAtual")

                // Para ringtone
                ringtoneManager.parar()

                // Cancela notificação de chamada recebida e move para em andamento
                notificationManager.cancel(NotificationConstants.getNotificationIdIncoming(chamadaIdAtual))
                NotificationConstants.moverParaEmAndamento(chamadaIdAtual)

                // Mostra notificação de chamada em andamento imediatamente
                // para evitar gap sem notificação (importante para foreground service)
                atualizarEstado(EstadoChamadaService.CONECTANDO_AUDIO)
                mostrarNotificacaoEmAndamento()

                // Busca dados completos da chamada
                val dadosResult = obterDadosChamada(chamadaIdAtual)
                if (dadosResult.isSuccess) {
                    chamadaAtual = dadosResult.getOrNull()
                }

                // Notifica API
                val response = api.entrarChamada("Bearer $token", ChamadaIdRequest(chamadaIdAtual))

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ API notificada: entrou na chamada")

                    delay(500) // Aguarda servidor processar

                    // Conecta ao TCP
                    conectarAudioTcp(tcpHost, chamadaIdAtual)

                    // Verifica se já tem outros participantes
                    val outrosParticipantes = chamadaAtual?.usuarios?.filter {
                        it.usuarioId != usuarioIdAtual
                    } ?: emptyList()

                    if (outrosParticipantes.isNotEmpty()) {
                        Log.d(TAG, "⚡ Já existem ${outrosParticipantes.size} participante(s)")
                        primeiroParticipanteEntrou = true
                        iniciarCapturaEReproducao()
                    } else {
                        Log.d(TAG, "⏳ Aguardando outros participantes")
                    }
                } else {
                    Log.e(TAG, "❌ Erro ao aceitar: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao aceitar chamada", e)
            }
        }
    }

    /**
     * Recusa chamada recebida
     */
    fun recusarChamada() {
        scope.launch {
            try {
                if (chamadaIdAtual == 0) {
                    Log.e(TAG, "Nenhuma chamada para recusar")
                    return@launch
                }

                val token = userPreferences.authToken.first() ?: return@launch

                Log.d(TAG, "Recusando chamada $chamadaIdAtual")

                ringtoneManager.parar()
                notificationManager.cancel(NotificationConstants.getNotificationIdIncoming(chamadaIdAtual))
                NotificationConstants.limparChamada(chamadaIdAtual)

                val response = api.recusarChamada("Bearer $token", ChamadaIdRequest(chamadaIdAtual))

                if (response.isSuccessful) {
                    Log.d(TAG, "Chamada recusada")
                } else {
                    Log.e(TAG, "Erro ao recusar: ${response.code()}")
                }

                chamadaIdAtual = 0
                chamadaAtual = null
                atualizarEstado(EstadoChamadaService.IDLE)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao recusar chamada", e)
            }
        }
    }

    /**
     * Encerra chamada em andamento
     */
    fun encerrarChamada() {
        finalizarChamadaENotificarAPI()
    }

    /**
     * Alterna mute do microfone
     */
    fun toggleMute() {
        isMutedMicrofone = !isMutedMicrofone

        if (isMutedMicrofone) {
            capturaPausada = true
            Log.d(TAG, "🔇 Microfone MUTADO")
        } else {
            capturaPausada = false
            Log.d(TAG, "🔊 Microfone ATIVO")
        }

        // Atualiza notificação
        if (_estadoFlow.value == EstadoChamadaService.EM_CHAMADA) {
            atualizarNotificacaoEmAndamento()
        }
    }

    /**
     * Define estado de mute do microfone
     */
    fun toggleMute(muted: Boolean) {
        isMutedMicrofone = muted

        if (isMutedMicrofone) {
            capturaPausada = true
            Log.d(TAG, "🔇 Microfone MUTADO")
        } else {
            capturaPausada = false
            Log.d(TAG, "🔊 Microfone ATIVO")
        }

        // Atualiza notificação
        if (_estadoFlow.value == EstadoChamadaService.EM_CHAMADA) {
            atualizarNotificacaoEmAndamento()
        }
    }

    /**
     * Configura o speakerphone de forma compatível com diferentes versões do Android
     */
    @Suppress("DEPRECATION")
    private fun setSpeakerphoneOn(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+): usar setCommunicationDevice
            val devices = audioManager.availableCommunicationDevices
            val targetDevice = if (on) {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            } else {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            }

            targetDevice?.let {
                audioManager.setCommunicationDevice(it)
            } ?: run {
                // Fallback para método deprecado se dispositivo não encontrado
                audioManager.isSpeakerphoneOn = on
            }
        } else {
            // Android 11 e anterior: usar método deprecado
            audioManager.isSpeakerphoneOn = on
        }
    }

    /**
     * Alterna entre earpiece e speakerphone
     */
    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(isSpeakerOn)

        if (isSpeakerOn) {
            Log.d(TAG, "📢 Alto-falante ATIVADO")
        } else {
            Log.d(TAG, "📱 Earpiece ATIVADO")
        }

        // Atualiza notificação
        if (_estadoFlow.value == EstadoChamadaService.EM_CHAMADA) {
            atualizarNotificacaoEmAndamento()
        }
    }

    /**
     * Define estado do speaker
     */
    fun toggleSpeaker(speakerOn: Boolean) {
        isSpeakerOn = speakerOn

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(isSpeakerOn)

        if (isSpeakerOn) {
            Log.d(TAG, "📢 Alto-falante ATIVADO")
        } else {
            Log.d(TAG, "📱 Earpiece ATIVADO")
        }

        // Atualiza notificação
        if (_estadoFlow.value == EstadoChamadaService.EM_CHAMADA) {
            atualizarNotificacaoEmAndamento()
        }
    }

    /**
     * Define volume de um participante (0-100)
     */
    fun setVolumeParticipante(usuarioId: Int, volume: Int) {
        val volumeClampado = volume.coerceIn(0, 100)
        participantesAudio.getOrPut(usuarioId) {
            ParticipanteAudio(usuarioId)
        }.volume = volumeClampado
        Log.d(TAG, "Volume de $usuarioId: $volumeClampado%")
    }

    /**
     * Muta/desmuta um participante
     */
    fun setMuteParticipante(usuarioId: Int, muted: Boolean) {
        participantesAudio.getOrPut(usuarioId) {
            ParticipanteAudio(usuarioId)
        }.isMutado = muted
        Log.d(TAG, "Mute de $usuarioId: $muted")
        atualizarListaParticipantes()
    }

    /**
     * Verifica se participante está falando (VAD)
     */
    fun isFalandoParticipante(usuarioId: Int): Boolean {
        return participantesAudio[usuarioId]?.isFalando ?: false
    }

    /**
     * Retorna lista de participantes para UI (excluindo o usuário local)
     */
    fun getParticipantes(): List<com.conversa.conversa.ui.chamada.ParticipanteUI> {
        return _participantesFlow.value
            .filter { it.usuarioId != usuarioIdAtual }  // Excluir usuário local
            .map { item ->
                com.conversa.conversa.ui.chamada.ParticipanteUI(
                    id = item.usuarioId,
                    nome = item.nome,
                    fotoUrl = null,
                    isFalando = item.isFalando,
                    status = if (item.isFalando || item.volume > 0) "Conectado" else "Aguardando",
                    mutadoLocalmente = item.isMutado,
                    volume = item.volume
                )
            }
    }

    /**
     * Retorna o ID do usuário atual (para filtrar participantes na UI)
     */
    fun getUsuarioIdAtual(): Int = usuarioIdAtual

    /**
     * Verifica se microfone está mutado
     */
    fun isMuted(): Boolean {
        return isMutedMicrofone
    }

    /**
     * Verifica se speaker está ativo
     */
    fun isSpeakerOn(): Boolean {
        return isSpeakerOn
    }

    /**
     * Finaliza chamada (alias para encerrarChamada)
     */
    fun finalizarChamada() {
        encerrarChamada()
    }

    // ==================== PROCESSAMENTO DE EVENTOS ====================

    private fun processarChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        scope.launch {
            try {
                Log.d(TAG, "Processando chamada recebida: $chamadaId de usuarioId=$usuarioId")

                // Atualiza flag para indicar que ChamadaService está ativo
                // Isso impede que eventos de outras chamadas iniciem este serviço
                SocketService.chamadaServiceAtivo = true

                // Inicializa usuarioIdAtual para filtrar participantes corretamente
                usuarioIdAtual = userPreferences.userId.first() ?: 0

                chamadaIdAtual = chamadaId

                // Busca dados da chamada PRIMEIRO para obter o nome correto
                val resultado = obterDadosChamada(chamadaId)
                if (resultado.isSuccess) {
                    chamadaAtual = resultado.getOrNull()
                    Log.d(TAG, "Dados carregados: ${chamadaAtual?.usuarios?.size} participantes")
                }

                // Atualiza estado DEPOIS de carregar os dados para UI mostrar nome correto
                atualizarEstado(EstadoChamadaService.RECEBENDO_CHAMADA)

                // Extrai o nome do usuário dos dados da chamada
                // Prioriza dados da API, usa fallback do WebSocket se não encontrar
                val nomeParaExibir = chamadaAtual?.usuarios
                    ?.find { it.usuarioId == usuarioId }
                    ?.usuarioNome
                    ?: usuarioNome.ifEmpty { "Desconhecido" }

                Log.d(TAG, "Nome do chamador: $nomeParaExibir")

                // Inicia ringtone
                ringtoneManager.iniciar()

                // Mostra notificação com nome correto dos dados da chamada
                mostrarNotificacaoChamadaRecebida(chamadaId, nomeParaExibir)

                // Emite evento com nome correto
                _eventosFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_RECEBIDA,
                        chamadaId = chamadaId,
                        participanteId = usuarioId,
                        participanteNome = nomeParaExibir
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar chamada recebida", e)
            }
        }
    }

    private fun processarUsuarioEntrou(usuarioId: Int) {
        scope.launch {
            try {
                // Atualiza dados da chamada
                val resultado = obterDadosChamada(chamadaIdAtual)
                if (resultado.isSuccess) {
                    chamadaAtual = resultado.getOrNull()
                }

                val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == usuarioId }
                val participantesAtivos = chamadaAtual?.usuarios?.filter {
                    it.status == 3 && it.usuarioId != usuarioIdAtual
                } ?: emptyList()

                Log.d(TAG, "👤 PARTICIPANTE ENTROU: id=$usuarioId, nome=${usuario?.usuarioNome}")
                Log.d(TAG, "   Total participantes ativos (exceto eu): ${participantesAtivos.size}")

                // Se é o primeiro participante diferente, inicia áudio
                if (!primeiroParticipanteEntrou && usuarioId != usuarioIdAtual) {
                    primeiroParticipanteEntrou = true
                    Log.d(TAG, "⚡ Primeiro participante entrou, iniciando áudio")
                    iniciarCapturaEReproducao()
                }

                _eventosFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.PARTICIPANTE_ENTROU,
                        chamadaId = chamadaIdAtual,
                        participanteId = usuarioId,
                        participanteNome = usuario?.usuarioNome
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar usuário entrou", e)
            }
        }
    }

    private fun processarUsuarioSaiu(usuarioId: Int) {
        scope.launch {
            try {
                // Atualiza dados
                val resultado = obterDadosChamada(chamadaIdAtual)
                if (resultado.isSuccess) {
                    chamadaAtual = resultado.getOrNull()
                }

                val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == usuarioId }

                // Verifica quantos participantes ativos restam (status 3 = ENTROU)
                val participantesAtivos = chamadaAtual?.usuarios?.filter {
                    it.status == 3 && it.usuarioId != usuarioIdAtual
                } ?: emptyList()

                Log.d(TAG, "👤 PARTICIPANTE SAIU: id=$usuarioId, nome=${usuario?.usuarioNome}")
                Log.d(TAG, "   Participantes restantes (exceto eu): ${participantesAtivos.map { "${it.usuarioId}:${it.usuarioNome}" }}")

                // Se não restam outros participantes, finaliza a chamada automaticamente
                if (participantesAtivos.isEmpty()) {
                    Log.d(TAG, "📞 Único participante saiu - finalizando chamada automaticamente")
                    finalizarChamadaENotificarAPI()
                    return@launch
                }

                // Emite evento normalmente
                _eventosFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.PARTICIPANTE_SAIU,
                        chamadaId = chamadaIdAtual,
                        participanteId = usuarioId,
                        participanteNome = usuario?.usuarioNome
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar usuário saiu", e)
            }
        }
    }

    private fun processarUsuarioRecusou(usuarioId: Int) {
        scope.launch {
            try {
                Log.d(TAG, "Usuário $usuarioId recusou a chamada")

                // Atualiza dados
                val resultado = obterDadosChamada(chamadaIdAtual)
                if (resultado.isSuccess) {
                    chamadaAtual = resultado.getOrNull()
                }

                val usuario = chamadaAtual?.usuarios?.find { it.usuarioId == usuarioId }
                _eventosFlow.emit(
                    com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                        tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_RECUSADA,
                        chamadaId = chamadaIdAtual,
                        participanteId = usuarioId,
                        participanteNome = usuario?.usuarioNome
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar usuário recusou", e)
            }
        }
    }

    // ==================== ÁUDIO TCP ====================

    private suspend fun conectarAudioTcp(serverHost: String, chamadaId: Int) = withContext(Dispatchers.IO) {
        try {
            atualizarEstado(EstadoChamadaService.CONECTANDO_AUDIO)

            Log.d(TAG, "🔌 TCP: Conectando a $serverHost:$TCP_PORT")
            Log.d(TAG, "   ChamadaId: $chamadaId, UsuarioId: $usuarioIdAtual")

            // Recria fila de envio
            recriarFilaEnvio()

            // Conecta ao servidor TCP
            socket = Socket(serverHost, TCP_PORT).apply {
                tcpNoDelay = true
                soTimeout = 0
                keepAlive = true
                receiveBufferSize = BUFFER_SIZE * 8
                trafficClass = 0xB8  // DSCP EF - Prioridade VoIP
            }

            outputStream = DataOutputStream(socket!!.getOutputStream())
            inputStream = DataInputStream(socket!!.getInputStream())

            Log.d(TAG, "🔌 TCP: Socket conectado")

            // Registra cliente
            registrarCliente()

            // Inicializa áudio
            if (!inicializarAudio()) {
                Log.e(TAG, "❌ Falha ao inicializar áudio")
                finalizarChamadaInterno()
                return@withContext
            }

            Log.d(TAG, "✅ Áudio inicializado")

            // Configura AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphoneOn(isSpeakerOn)

            emChamada = true

            // Inicia worker de envio
            iniciarSender()

            // Muda para estado EM_CHAMADA
            atualizarEstado(EstadoChamadaService.EM_CHAMADA)

            // Inicia timer
            iniciarTimer()

            // Mostra notificação de chamada em andamento
            mostrarNotificacaoEmAndamento()

            // Emite evento
            _eventosFlow.emit(
                com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                    tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_CONECTADA,
                    chamadaId = chamadaId
                )
            )

            Log.d(TAG, "🔌 TCP: Conexão estabelecida com sucesso")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao conectar TCP", e)
            finalizarChamadaInterno()
        }
    }

    private suspend fun registrarCliente() {
        val payload = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        payload.put(PACKET_TYPE_REGISTER)
        payload.putInt(usuarioIdAtual)

        val packet = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(5)
        packet.put(payload.array())

        envioMutex.withLock {
            outputStream?.write(packet.array())
            outputStream?.flush()
        }

        Log.d(TAG, "🔌 TCP: Cliente registrado (usuarioId=$usuarioIdAtual)")
    }

    private fun inicializarAudio(): Boolean {
        return try {
            Log.d(TAG, "Inicializando áudio...")

            // AudioRecord
            val minBufferSizeRecord = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
            if (minBufferSizeRecord == AudioRecord.ERROR || minBufferSizeRecord == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Erro ao calcular buffer AudioRecord")
                return false
            }

            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )

            var recordCriado = false
            for (source in audioSources) {
                try {
                    val recordBufferSize = maxOf(minBufferSizeRecord, BUFFER_SIZE)
                    audioRecord = AudioRecord(source, SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, recordBufferSize)

                    if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "✅ AudioRecord criado com source=$source")
                        recordCriado = true
                        break
                    } else {
                        audioRecord?.release()
                        audioRecord = null
                    }
                } catch (e: Exception) {
                    audioRecord?.release()
                    audioRecord = null
                }
            }

            if (!recordCriado) {
                Log.e(TAG, "❌ Não foi possível criar AudioRecord")
                return false
            }

            // AudioTrack
            val minBufferSizeTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            if (minBufferSizeTrack == AudioTrack.ERROR || minBufferSizeTrack == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Erro ao calcular buffer AudioTrack")
                return false
            }

            val trackBufferSize = maxOf(minBufferSizeTrack, BUFFER_SIZE * 6)

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

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioTrack não inicializado")
                audioTrack?.release()
                audioTrack = null
                return false
            }

            Log.d(TAG, "✅ AudioTrack criado")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao inicializar áudio", e)
            false
        }
    }

    private fun recriarFilaEnvio() {
        try {
            filaEnvio.close()
        } catch (e: Exception) {
            // Ignora
        }
        filaEnvio = Channel(capacity = 100)
        Log.d(TAG, "Canal de envio recriado")
    }

    private fun iniciarSender() {
        senderJob = scope.launch {
            try {
                Log.d(TAG, "Worker de envio iniciado")
                var pacotesEnviados = 0

                for (packet in filaEnvio) {
                    try {
                        envioMutex.withLock {
                            outputStream?.write(packet)
                            outputStream?.flush()
                        }
                        pacotesEnviados++
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

    private fun iniciarCapturaEReproducao() {
        scope.launch {
            Log.d(TAG, "=== INICIANDO CAPTURA E REPRODUÇÃO ===")

            if (audioRecord == null || audioTrack == null) {
                Log.e(TAG, "❌ Componentes de áudio NULL")
                return@launch
            }

            iniciarCaptura()
            iniciarRecepcaoTCP()
            iniciarMixer()
            iniciarReproducao()

            Log.d(TAG, "✅ Todas as coroutines de áudio iniciadas")
        }
    }

    private fun iniciarCaptura() {
        captureJob = scope.launch {
            try {
                val record = audioRecord ?: return@launch

                record.startRecording()
                Log.d(TAG, "Captura iniciada")

                val buffer = ByteArray(BUFFER_SIZE)

                while (emChamada && isActive) {
                    try {
                        val bytesRead = record.read(buffer, 0, buffer.size)

                        when {
                            bytesRead > 0 && !capturaPausada -> {
                                val audioCopy = buffer.copyOf(bytesRead)
                                enviarAudio(audioCopy, bytesRead)
                            }
                            bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                                Log.e(TAG, "❌ AudioRecord em estado inválido")
                                delay(100)
                            }
                            capturaPausada -> delay(10)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na captura", e)
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro fatal na captura", e)
            } finally {
                try {
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord?.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioRecord", e)
                }
                Log.d(TAG, "Captura finalizada")
            }
        }
    }

    private suspend fun enviarAudio(audioData: ByteArray, size: Int) {
        try {
            if (size <= 0 || size > BUFFER_SIZE) return

            val payloadSize = 1 + 4 + size
            val payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            payload.put(PACKET_TYPE_AUDIO)
            payload.putInt(chamadaIdAtual)
            payload.put(audioData, 0, size)

            val packet = ByteBuffer.allocate(4 + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(payloadSize)
            packet.put(payload.array())

            filaEnvio.trySend(packet.array())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enfileirar áudio", e)
        }
    }

    private fun iniciarRecepcaoTCP() {
        receiveJob = scope.launch {
            try {
                Log.d(TAG, "Recepção TCP iniciada")

                while (emChamada && isActive) {
                    try {
                        receberAudioDoServidor()
                    } catch (e: EOFException) {
                        Log.e(TAG, "Conexão encerrada")
                        finalizarChamadaENotificarAPI()
                        break
                    } catch (e: SocketTimeoutException) {
                        delay(10)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao receber TCP", e)
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na recepção TCP", e)
            }
        }
    }

    private suspend fun receberAudioDoServidor() {
        try {
            val stream = inputStream ?: return

            // Lê tamanho
            val tamanhoBytes = ByteArray(4)
            stream.readFully(tamanhoBytes)
            val tamanho = ByteBuffer.wrap(tamanhoBytes).order(ByteOrder.LITTLE_ENDIAN).int

            if (tamanho <= 4 || tamanho > (BUFFER_SIZE * 4 + 4)) return

            // Lê ID do cliente
            val clientIdBytes = ByteArray(4)
            stream.readFully(clientIdBytes)
            val clientId = ByteBuffer.wrap(clientIdBytes).order(ByteOrder.LITTLE_ENDIAN).int

            val audioSize = tamanho - 4
            if (audioSize <= 0 || audioSize > BUFFER_SIZE * 4) return

            // Lê áudio
            val audioData = ByteArray(audioSize)
            stream.readFully(audioData)

            val audioShorts = bytesToShorts(audioData)
            adicionarAoMixing(clientId, audioShorts)

        } catch (e: EOFException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao receber áudio", e)
            throw e
        }
    }

    private fun iniciarMixer() {
        mixerJob = scope.launch {
            try {
                Log.d(TAG, "Mixer iniciado")
                var proximoTempo = System.currentTimeMillis() + PLAY_INTERVAL_MS

                while (emChamada && isActive) {
                    val agoraAtual = System.currentTimeMillis()

                    if (agoraAtual >= proximoTempo) {
                        // Mixer sempre tenta produzir saída - função mixar() já retorna
                        // ShortArray(0) quando não há dados, evitando underrun
                        val buffersDisponiveis = mixingBuffers.mapValues { it.value.size }
                        val bufferSizes = mixingBufferSizes.toMap()
                        val mixedAudio = mixar()

                        Log.d(TAG, "🎛️ Mixer: buffers=$buffersDisponiveis, sizes=$bufferSizes, " +
                            "output=${mixedAudio.size} samples, outputQueue=${mixedOutputBuffer.size}")

                        if (mixedAudio.isNotEmpty()) {
                            if (!mixedOutputBuffer.offer(mixedAudio)) {
                                Log.w(TAG, "🎛️ Mixer: buffer cheio, descartando mais antigo")
                                mixedOutputBuffer.poll()
                                mixedOutputBuffer.offer(mixedAudio)
                            }
                        }

                        proximoTempo += PLAY_INTERVAL_MS

                        if (agoraAtual > proximoTempo + 100) {
                            proximoTempo = agoraAtual + PLAY_INTERVAL_MS
                        }
                    }

                    val tempoEspera = proximoTempo - System.currentTimeMillis()
                    if (tempoEspera > 0) {
                        delay(minOf(tempoEspera, 50))
                    } else {
                        delay(1)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no mixer", e)
            }
        }
    }

    private fun iniciarReproducao() {
        playbackJob = scope.launch {
            try {
                val track = audioTrack ?: return@launch

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioTrack não inicializado")
                    return@launch
                }

                if (!emChamada || !isActive) return@launch

                track.play()

                delay(50)

                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.e(TAG, "❌ AudioTrack não está reproduzindo")
                    return@launch
                }

                Log.d(TAG, "✅ Reprodução iniciada")

                var proximoTempo = System.currentTimeMillis() + PLAY_INTERVAL_MS

                while (emChamada && isActive) {
                    try {
                        val agoraAtual = System.currentTimeMillis()

                        if (agoraAtual >= proximoTempo) {
                            Log.d(TAG, "🔊 Reprodução: Obtendo dados")
                            val mixedAudio = mixedOutputBuffer.poll()
                            val queueSize = mixedOutputBuffer.size

                            if (!reproducaoPausada) {
                                if (mixedAudio != null) {
                                    Log.d(TAG, "🔊 Reprodução: ${mixedAudio.size} samples, fila=$queueSize")
                                    reproduzirAudio(mixedAudio)
                                } else {
                                    // Alimenta silêncio para evitar underrun no AudioTrack
                                    Log.w(TAG, "🔇 Reprodução: buffer vazio, alimentando silêncio")
                                    reproduzirAudio(ShortArray(BUFFER_SIZE / 2))
                                }
                            }

                            proximoTempo += PLAY_INTERVAL_MS

                            if (agoraAtual > proximoTempo + 100) {
                                proximoTempo = agoraAtual + PLAY_INTERVAL_MS
                            }
                        }

                        val tempoEspera = proximoTempo - System.currentTimeMillis()
                        if (tempoEspera > 0) {
                            delay(minOf(tempoEspera, 50))
                        } else {
                            delay(1)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no loop de reprodução", e)
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na reprodução", e)
            } finally {
                try {
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao parar AudioTrack", e)
                }
            }
        }
    }

    private fun todosParticipantesProntos(): Boolean {
        if (mixingBuffers.isEmpty()) return false
        if (mixingBuffers.size == 1) return true
        return mixingBuffers.all { (_, queue) -> queue.isNotEmpty() }
    }

    private fun algumBufferCheio(): Boolean {
        val halfThreshold = MAX_BUFFER_THRESHOLD / 2
        return mixingBufferSizes.any { (_, size) -> size > halfThreshold }
    }

    private fun adicionarAoMixing(clientId: Int, audioData: ShortArray) {
        // Obter ou criar participante
        val participante = participantesAudio.getOrPut(clientId) {
            ParticipanteAudio(usuarioId = clientId)
        }

        // Se mutado, ignorar
        if (participante.isMutado) {
            return
        }

        // Detectar voz
        participante.isFalando = detectarAtividadeVoz(audioData)

        // Atualizar timestamp se falando
        if (participante.isFalando) {
            lastAudioTimestamps[clientId] = System.currentTimeMillis()
            atualizarListaParticipantes()
        }

        // Se não está falando, não adiciona
        if (!participante.isFalando) {
            return
        }

        // Adicionar ao buffer
        if (!mixingBuffers.containsKey(clientId)) {
            mixingBuffers[clientId] = LinkedBlockingQueue(10)
            mixingBufferSizes[clientId] = 0
        }

        val queue = mixingBuffers[clientId]!!
        var currentSize = mixingBufferSizes[clientId] ?: 0
        val audioBytes = audioData.size * 2

        // Controle de overflow
        while (currentSize > MAX_BUFFER_THRESHOLD) {
            val discarded = queue.poll()
            if (discarded != null) {
                currentSize = (currentSize - (discarded.size * 2)).coerceAtLeast(0)
                mixingBufferSizes[clientId] = currentSize
            } else {
                break
            }
        }

        if (queue.offer(audioData)) {
            mixingBufferSizes[clientId] = (mixingBufferSizes[clientId] ?: 0) + audioBytes
        }
    }

    private fun detectarAtividadeVoz(audioData: ShortArray): Boolean {
        if (audioData.isEmpty()) return false
        val amplitudeMedia = audioData.map { kotlin.math.abs(it.toInt()) }.average()
        return amplitudeMedia > VAD_THRESHOLD
    }

    private fun mixar(): ShortArray {
        if (mixingBuffers.isEmpty()) {
            Log.d(TAG, "🎚️ mixar(): mixingBuffers vazio, retornando silêncio")
            return ShortArray(0)
        }

        val mixedBuffer = ShortArray(BUFFER_SIZE / 2)
        val buffersToMix = mutableListOf<Pair<Int, ShortArray>>()
        val detalhesColeta = mutableListOf<String>()

        // Coletar buffers de participantes NÃO mutados
        mixingBuffers.forEach { (clientId, queue) ->
            val participante = participantesAudio[clientId]
            val queueSize = queue.size

            if (participante?.isMutado == true) {
                queue.poll()
                detalhesColeta.add("p$clientId: MUTADO (descartado)")
                return@forEach
            }

            queue.poll()?.let { buffer ->
                buffersToMix.add(clientId to buffer)
                detalhesColeta.add("p$clientId: ${buffer.size} samples (fila=$queueSize)")

                val currentSize = mixingBufferSizes[clientId] ?: 0
                mixingBufferSizes[clientId] = (currentSize - buffer.size * 2).coerceAtLeast(0)
            } ?: run {
                detalhesColeta.add("p$clientId: SEM DADOS (fila vazia)")
            }
        }

        Log.d(TAG, "🎚️ mixar() coleta: ${detalhesColeta.joinToString(", ")}")

        if (buffersToMix.isEmpty()) {
            Log.d(TAG, "🎚️ mixar(): nenhum buffer coletado, retornando silêncio")
            return ShortArray(0)
        }

        // Aplicar volume e verificar VAD
        val detalhesVAD = mutableListOf<String>()
        val buffersComVolume = buffersToMix.mapNotNull { (clientId, buffer) ->
            val participante = participantesAudio[clientId] ?: return@mapNotNull null
            val bufferComVolume = aplicarVolume(buffer, participante.volume)
            val amplitudeMedia = buffer.map { kotlin.math.abs(it.toInt()) }.average().toInt()
            val temDados = detectarAtividadeVoz(bufferComVolume)

            if (temDados) {
                detalhesVAD.add("p$clientId: VOZ (amp=$amplitudeMedia, vol=${participante.volume}%)")
                bufferComVolume
            } else {
                detalhesVAD.add("p$clientId: SILÊNCIO (amp=$amplitudeMedia)")
                null
            }
        }

        Log.d(TAG, "🎚️ mixar() VAD: ${detalhesVAD.joinToString(", ")}")

        if (buffersComVolume.isEmpty()) {
            Log.d(TAG, "🎚️ mixar(): todos silenciosos após VAD, retornando silêncio")
            return ShortArray(0)
        }

        val numStreamsAtivos = buffersComVolume.size

        // Mixing com divisão
        for (i in mixedBuffer.indices) {
            var mixedSample = 0

            for (buffer in buffersComVolume) {
                if (i < buffer.size) {
                    mixedSample += buffer[i].toInt()
                }
            }

            mixedSample /= numStreamsAtivos

            mixedBuffer[i] = mixedSample.coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            ).toShort()
        }

        val amplitudeSaida = mixedBuffer.map { kotlin.math.abs(it.toInt()) }.average().toInt()
        Log.d(TAG, "🎚️ mixar() SAÍDA: ${mixedBuffer.size} samples, $numStreamsAtivos streams, amp=$amplitudeSaida")

        return mixedBuffer
    }

    private fun aplicarVolume(audioData: ShortArray, volumePercent: Int): ShortArray {
        if (volumePercent == 100) return audioData
        if (volumePercent == 0) return ShortArray(audioData.size)

        val fator = volumePercent / 100.0f

        return ShortArray(audioData.size) { i ->
            (audioData[i] * fator).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun reproduzirAudio(audioData: ShortArray) {
        if (audioData.isEmpty()) return

        try {
            val smoothedData = aplicarSuavizacao(audioData)
            val audioBytes = shortsToBytes(smoothedData)
            audioTrack?.write(audioBytes, 0, audioBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir", e)
        }
    }

    private fun aplicarSuavizacao(audioData: ShortArray): ShortArray {
        if (audioData.isEmpty()) return audioData

        val smoothed = audioData.copyOf()
        val fadeLength = minOf(FADE_SAMPLES, smoothed.size)

        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            val currentValue = smoothed[i].toFloat()
            val previousValue = lastSample.toFloat()

            smoothed[i] = (previousValue * (1f - factor) + currentValue * factor).toInt().toShort()
        }

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

    // ==================== TIMER ====================

    private fun iniciarTimer() {
        chamadaIniciadaEm = System.currentTimeMillis()

        timerJob = scope.launch {
            while (emChamada && isActive) {
                val decorridos = (System.currentTimeMillis() - chamadaIniciadaEm) / 1000
                val minutos = decorridos / 60
                val segundos = decorridos % 60
                _timerFlow.value = String.format("%02d:%02d", minutos, segundos)

                // Atualiza notificação a cada segundo
                atualizarNotificacaoEmAndamento()

                delay(1000)
            }
        }
    }

    // ==================== FINALIZAÇÃO ====================

    private fun finalizarChamadaENotificarAPI() {
        scope.launch {
            try {
                val token = userPreferences.authToken.first()

                if (chamadaIdAtual != 0 && token != null) {
                    try {
                        api.sairChamada("Bearer $token", ChamadaIdRequest(chamadaIdAtual))
                        Log.d(TAG, "API notificada sobre saída")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao notificar API", e)
                    }
                }

                finalizarChamadaInterno()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao finalizar chamada", e)
            }
        }
    }

    private fun finalizarChamadaInterno() {
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "🔚 FINALIZANDO CHAMADA")
        Log.d(TAG, "════════════════════════════════════════")

        // Atualiza flag para indicar que ChamadaService não está mais ativo
        // Isso permite que eventos de socket sejam ignorados se não houver chamada ativa
        SocketService.chamadaServiceAtivo = false

        emChamada = false
        primeiroParticipanteEntrou = false

        // Cancela jobs
        Log.d(TAG, "   ⏹️ Cancelando jobs de áudio...")
        captureJob?.cancel()
        playbackJob?.cancel()
        senderJob?.cancel()
        receiveJob?.cancel()
        mixerJob?.cancel()
        timerJob?.cancel()

        captureJob = null
        playbackJob = null
        senderJob = null
        receiveJob = null
        mixerJob = null
        timerJob = null

        // Fecha canal
        try {
            filaEnvio.close()
        } catch (e: Exception) {
            // Ignora
        }

        Thread.sleep(100)

        // Para e libera áudio
        Log.d(TAG, "   🎤 Liberando AudioRecord...")
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null

            Log.d(TAG, "   🔊 Liberando AudioTrack...")
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar áudio", e)
        }

        // Fecha socket
        Log.d(TAG, "   🔌 Fechando socket TCP...")
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar socket", e)
        }

        socket = null
        outputStream = null
        inputStream = null

        // Limpa buffers
        mixingBuffers.clear()
        mixingBufferSizes.clear()
        mixedOutputBuffer.clear()
        lastAudioTimestamps.clear()
        participantesAudio.clear()

        lastSample = 0

        // Para ringtone
        ringtoneManager.parar()

        // Guarda o ID antes de resetar para cancelar notificações corretamente
        val chamadaIdParaLimpar = chamadaIdAtual

        // Cancela notificações usando ID dinâmico
        notificationManager.cancel(NotificationConstants.getNotificationIdIncoming(chamadaIdParaLimpar))
        notificationManager.cancel(NotificationConstants.getNotificationIdOngoing(chamadaIdParaLimpar))
        NotificationConstants.limparChamada(chamadaIdParaLimpar)

        // Para o serviço foreground e remove notificação
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // Reseta estado
        atualizarEstado(EstadoChamadaService.IDLE)
        chamadaIdAtual = 0
        chamadaAtual = null
        isMutedMicrofone = false
        isSpeakerOn = false

        // Emite evento
        scope.launch {
            _eventosFlow.emit(
                com.conversa.conversa.data.chamada.model.EventoChamadaUI(
                    tipo = com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI.CHAMADA_FINALIZADA,
                    chamadaId = 0
                )
            )
        }

        Log.d(TAG, "   ✅ Chamada finalizada completamente")
        Log.d(TAG, "   🛑 Chamando stopSelf()...")

        // Para o serviço completamente
        stopSelf()
    }

    // ==================== API ====================

    private suspend fun obterDadosChamada(chamadaId: Int): Result<ChamadaResponse> {
        return try {
            val token = userPreferences.authToken.first() ?: return Result.failure(Exception("Token não disponível"))

            val response = api.obterDadosChamada("Bearer $token", chamadaId)

            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Erro: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter dados", e)
            Result.failure(e)
        }
    }

    private fun extrairHost(url: String): String {
        return try {
            val semProtocolo = url.replace("http://", "").replace("https://", "")
            semProtocolo.split(":").first().split("/").first()
        } catch (e: Exception) {
            "localhost"
        }
    }

    // ==================== PARTICIPANTES ====================

    private fun atualizarListaParticipantes() {
        val participantes = chamadaAtual?.usuarios?.map { usuario ->
            val participante = participantesAudio[usuario.usuarioId]
            ParticipanteItem(
                usuarioId = usuario.usuarioId,
                nome = usuario.usuarioNome,
                isFalando = participante?.isFalando ?: false,
                isMutado = participante?.isMutado ?: false,
                volume = participante?.volume ?: 100
            )
        } ?: emptyList()

        _participantesFlow.value = participantes
    }

    // ==================== NOTIFICAÇÕES ====================

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CHAMADAS,
                "Chamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas de voz"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun criarNotificacaoForeground(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Conversa")
            .setContentText("Serviço de chamadas ativo")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun mostrarNotificacaoChamadaRecebida(chamadaId: Int, usuarioNome: String) {
        // Registra a chamada como recebendo
        NotificationConstants.adicionarChamadaRecebendo(chamadaId)

        val answerIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_ANSWER
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            this, chamadaId, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_DECLINE
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, chamadaId + 1000, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
            putExtra("is_incoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, chamadaId + 5000, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ com CallStyle
            val caller = Person.Builder()
                .setName(usuarioNome)
                .setImportant(true)
                .build()

            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                ))
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(60000)
                .build()
        } else {
            // Android < 12
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(usuarioNome)
                .setContentText("Chamada de voz")
                .addAction(R.drawable.ic_call_end, "Recusar", declinePendingIntent)
                .addAction(R.drawable.ic_call, "Atender", answerPendingIntent)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(60000)
                .build()
        }

        notificationManager.notify(NotificationConstants.getNotificationIdIncoming(chamadaId), notification)
    }

    private fun mostrarNotificacaoEmAndamento() {
        // Intent para abrir a tela de chamada ao clicar na notificação
        val openActivityIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_CHAMADA_ID, chamadaIdAtual)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openActivityPendingIntent = PendingIntent.getActivity(
            this, chamadaIdAtual + 6000, openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, ChamadaService::class.java).apply {
            action = ACTION_ENCERRAR
        }
        val hangupPendingIntent = PendingIntent.getService(
            this, 0, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, ChamadaService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, 1, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakerIntent = Intent(this, ChamadaService::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
        }
        val speakerPendingIntent = PendingIntent.getService(
            this, 2, speakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nomeContato = chamadaAtual?.usuarios?.firstOrNull { it.usuarioId != usuarioIdAtual }?.usuarioNome ?: "Chamada"
        val timerText = _timerFlow.value

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder()
                .setName(nomeContato)
                .setImportant(true)
                .build()

            // CallStyle do Android 12+ não suporta bem addAction() extras
            // Mantemos apenas o botão Encerrar nativo do CallStyle
            // Controles de Mute/Speaker ficam disponíveis na tela da chamada
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(
                    caller,
                    hangupPendingIntent
                ))
                .setContentIntent(openActivityPendingIntent)
                .setContentText(timerText)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("Em chamada com $nomeContato")
                .setContentText(timerText)
                .setContentIntent(openActivityPendingIntent)
                .addAction(
                    if (isMutedMicrofone) R.drawable.ic_mic_off else R.drawable.ic_mic,
                    if (isMutedMicrofone) "Ativar" else "Mutar",
                    mutePendingIntent
                )
                .addAction(
                    if (isSpeakerOn) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                    if (isSpeakerOn) "Desativar" else "Viva-voz",
                    speakerPendingIntent
                )
                .addAction(R.drawable.ic_call_end, "Encerrar", hangupPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        // Calcula o ID da notificação dinamicamente
        val notificationId = NotificationConstants.getNotificationIdOngoing(chamadaIdAtual)

        // Usa startForeground para Android 12+ para garantir que o service continue ativo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: precisa especificar foregroundServiceType
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12-13: startForeground sem tipo específico
            startForeground(notificationId, notification)
        } else {
            // Android < 12: apenas notify
            notificationManager.notify(notificationId, notification)
        }
    }

    private fun atualizarNotificacaoEmAndamento() {
        val estado = _estadoFlow.value
        if (estado == EstadoChamadaService.EM_CHAMADA || estado == EstadoChamadaService.CONECTANDO_AUDIO) {
            mostrarNotificacaoEmAndamento()
        }
    }

    // ==================== WAKELOCK ====================

    private fun adquirirWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ChamadaService:WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutos
        }
        Log.d(TAG, "WakeLock adquirido")
    }

    private fun liberarWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock liberado")
            }
        }
        wakeLock = null
    }
}

// ==================== CLASSES DE DADOS ====================

data class ParticipanteAudio(
    val usuarioId: Int,
    var volume: Int = 100,
    var isMutado: Boolean = false,
    var isFalando: Boolean = false
)

data class ParticipanteItem(
    val usuarioId: Int,
    val nome: String,
    val isFalando: Boolean,
    val isMutado: Boolean,
    val volume: Int
)
