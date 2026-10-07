package com.conversa.conversa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.conversa.conversa.BuildConfig
import com.conversa.conversa.R
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.data.model.EstadoChamadaLocal
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.ChamadaRepository
import com.conversa.conversa.data.socket.SocketManager
import com.conversa.conversa.data.webrtc.WebRTCManager
import com.conversa.conversa.service.NotificationConstants.CHANNEL_ID_CHAMADAS
import com.conversa.conversa.service.NotificationConstants.NOTIFICATION_ID_CHAMADA_FOREGROUND
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.ui.chamada.ParticipanteUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service de chamada — mantem notificacao persistente, wake lock e lifecycle
 * enquanto delega toda a logica de sinalizacao/midia ao ChamadaRepository + WebRTCManager.
 *
 * Estado consumido pela UI via StateFlows; acoes expostas como metodos do Binder.
 */
class ChamadaService : Service() {

    companion object {
        private const val TAG = "ChamadaService"

        const val ACTION_CHAMADA_RECEBIDA = "com.conversa.chamada.RECEBIDA"
        const val ACTION_CHAMADA_FINALIZADA = "com.conversa.chamada.FINALIZADA"
        const val ACTION_USUARIO_ENTROU = "com.conversa.chamada.USUARIO_ENTROU"
        const val ACTION_USUARIO_SAIU = "com.conversa.chamada.USUARIO_SAIU"
        const val ACTION_USUARIO_RECUSOU = "com.conversa.chamada.USUARIO_RECUSOU"

        const val ACTION_INICIAR_CHAMADA = "com.conversa.chamada.INICIAR_CHAMADA"
        const val ACTION_ACEITAR = "com.conversa.chamada.ACEITAR"
        const val ACTION_RECUSAR = "com.conversa.chamada.RECUSAR"
        const val ACTION_ENCERRAR = "com.conversa.chamada.ENCERRAR"
        const val ACTION_TOGGLE_MUTE = "com.conversa.chamada.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.conversa.chamada.TOGGLE_SPEAKER"

        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        const val EXTRA_DESTINATARIOS = "destinatarios"
        const val EXTRA_COM_VIDEO = "com_video"

        @Volatile
        var chamadaServiceAtivo: Boolean = false
    }

    /**
     * Estados expostos a UI. Mapeiam diretamente EstadoChamadaLocal do repository, com
     * CHAMANDO = INICIANDO_CHAMADA enquanto o peer nao entrou, EM_CHAMADA = CHAMADA_EM_ANDAMENTO.
     */
    enum class EstadoChamadaService {
        IDLE,
        RECEBENDO_CHAMADA,
        INICIANDO_CHAMADA,
        CHAMANDO,
        EM_CHAMADA,
        FINALIZANDO,
    }

    inner class LocalBinder : Binder() {
        fun getService(): ChamadaService = this@ChamadaService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // StateFlows publicos
    private val _estadoFlow = MutableStateFlow(EstadoChamadaService.IDLE)
    val estadoFlow: StateFlow<EstadoChamadaService> = _estadoFlow.asStateFlow()

    private val _chamadaAtualFlow = MutableStateFlow<ChamadaResponse?>(null)
    val chamadaAtualFlow: StateFlow<ChamadaResponse?> = _chamadaAtualFlow.asStateFlow()

    private val _timerFlow = MutableStateFlow("00:00")
    val timerFlow: StateFlow<String> = _timerFlow.asStateFlow()

    private val _eventosFlow = MutableSharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI>(replay = 0)
    val eventosFlow: SharedFlow<com.conversa.conversa.data.chamada.model.EventoChamadaUI> = _eventosFlow.asSharedFlow()

    // Dependencias
    private lateinit var userPreferences: UserPreferences
    private lateinit var api: ConversaApi
    private lateinit var ringtoneManager: ChamadaRingtoneManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var socketManager: SocketManager
    private lateinit var webRTCManager: WebRTCManager
    private lateinit var repository: ChamadaRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var chamadaIniciadaEm: Long = 0
    private var foregroundNotificacaoExibida = false
    private var usuarioIdAtual: Int = 0
    private var isMutedMicrofone = false
    private var isSpeakerOn = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ChamadaService onCreate")

        userPreferences = UserPreferences(applicationContext)
        api = RetrofitClient.api
        ringtoneManager = ChamadaRingtoneManager.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        socketManager = SocketService.socketManagerGlobal
            ?: SocketManager(applicationContext).also { SocketService.socketManagerGlobal = it }
        webRTCManager = WebRTCManager(applicationContext, resolverMediaMtxUrl(), BuildConfig.STUN_URL)
        repository = ChamadaRepository(applicationContext, api, webRTCManager, socketManager, userPreferences)

        criarCanalNotificacao()
        adquirirWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_CHAMADA_FOREGROUND,
                criarNotificacaoForeground(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(NOTIFICATION_ID_CHAMADA_FOREGROUND, criarNotificacaoForeground())
        }

        observarRepository()
    }

    private fun observarRepository() {
        scope.launch {
            repository.estadoLocalFlow.collect { estado ->
                val novo = mapearEstado(estado)
                _estadoFlow.value = novo
                when (novo) {
                    EstadoChamadaService.EM_CHAMADA -> {
                        iniciarTimerSeNecessario()
                        mostrarNotificacaoEmAndamento()
                    }
                    EstadoChamadaService.CHAMANDO, EstadoChamadaService.INICIANDO_CHAMADA -> {
                        mostrarNotificacaoEmAndamento()
                    }
                    EstadoChamadaService.FINALIZANDO, EstadoChamadaService.IDLE -> {
                        pararTimer()
                    }
                    else -> {}
                }
            }
        }
        scope.launch {
            repository.chamadaAtualFlow.collect { _chamadaAtualFlow.value = it }
        }
        scope.launch {
            repository.eventosUIFlow.collect { _eventosFlow.emit(it) }
        }
    }

    private fun mapearEstado(estado: EstadoChamadaLocal): EstadoChamadaService = when (estado) {
        EstadoChamadaLocal.DESCONHECIDO -> EstadoChamadaService.IDLE
        EstadoChamadaLocal.INICIANDO_CHAMADA -> EstadoChamadaService.CHAMANDO
        EstadoChamadaLocal.RECEBENDO_CHAMADA -> EstadoChamadaService.RECEBENDO_CHAMADA
        EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO -> EstadoChamadaService.EM_CHAMADA
        EstadoChamadaLocal.CHAMADA_FINALIZADA,
        EstadoChamadaLocal.CHAMADA_PERDIDA,
        EstadoChamadaLocal.RECUSADA -> EstadoChamadaService.FINALIZANDO
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_CHAMADA_RECEBIDA -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
                val usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: ""
                if (chamadaId != -1) {
                    chamadaServiceAtivo = true
                    scope.launch { processarChamadaRecebida(chamadaId, usuarioId, usuarioNome) }
                }
            }
            ACTION_CHAMADA_FINALIZADA -> {
                val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
                if (chamadaId == _chamadaAtualFlow.value?.id) {
                    scope.launch { repository.finalizarChamada() }
                }
            }
            ACTION_INICIAR_CHAMADA -> {
                val destinatarios = intent.getIntArrayExtra(EXTRA_DESTINATARIOS)?.toList() ?: emptyList()
                val comVideo = intent.getBooleanExtra(EXTRA_COM_VIDEO, false)
                if (destinatarios.isNotEmpty()) iniciarChamada(destinatarios, comVideo)
            }
            ACTION_ACEITAR -> aceitarChamada()
            ACTION_RECUSAR -> recusarChamada()
            ACTION_ENCERRAR -> encerrarChamada()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_TOGGLE_SPEAKER -> toggleSpeaker()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "ChamadaService onDestroy")
        chamadaServiceAtivo = false
        pararTimer()
        ringtoneManager.parar()
        repository.cleanup()
        webRTCManager.cleanup()
        liberarWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ═══ Acoes publicas (chamadas por UI/ActionReceiver) ════════════════════

    fun iniciarChamada(destinatariosIds: List<Int>, comVideo: Boolean = false) {
        scope.launch {
            chamadaServiceAtivo = true
            usuarioIdAtual = userPreferences.userId.first() ?: 0
            repository.iniciarChamada(destinatariosIds, comVideo)
                .onFailure { chamadaServiceAtivo = false }
        }
    }

    fun aceitarChamada() {
        scope.launch {
            val chamadaId = _chamadaAtualFlow.value?.id ?: return@launch
            ringtoneManager.parar()
            notificationManager.cancel(NotificationConstants.getNotificationIdIncoming(chamadaId))
            NotificationConstants.moverParaEmAndamento(chamadaId)
            usuarioIdAtual = userPreferences.userId.first() ?: 0
            repository.aceitarChamada(chamadaId)
        }
    }

    fun recusarChamada() {
        scope.launch {
            val chamadaId = _chamadaAtualFlow.value?.id ?: return@launch
            ringtoneManager.parar()
            notificationManager.cancel(NotificationConstants.getNotificationIdIncoming(chamadaId))
            NotificationConstants.limparChamada(chamadaId)
            repository.recusarChamada(chamadaId)
            stopSelfSafe()
        }
    }

    fun encerrarChamada() {
        scope.launch {
            repository.finalizarChamada()
            stopSelfSafe()
        }
    }

    fun finalizarChamada() = encerrarChamada()

    fun toggleMute() = toggleMute(!isMutedMicrofone)

    fun toggleMute(mutado: Boolean) {
        isMutedMicrofone = mutado
        repository.toggleMuteMicrofone(mutado)
        atualizarNotificacaoEmAndamento()
    }

    fun toggleSpeaker() = toggleSpeaker(!isSpeakerOn)

    fun toggleSpeaker(ligado: Boolean) {
        isSpeakerOn = ligado
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(ligado)
        repository.toggleSpeaker(ligado)
        atualizarNotificacaoEmAndamento()
    }

    /** Liga/desliga video local (upgrade audio->video). */
    fun alternarVideo(ativar: Boolean) {
        scope.launch { repository.alternarVideo(ativar) }
    }

    fun isMuted(): Boolean = isMutedMicrofone
    fun isSpeakerOn(): Boolean = isSpeakerOn
    fun getUsuarioIdAtual(): Int = usuarioIdAtual

    /** Lista de outros participantes (exclui eu). */
    fun getParticipantes(): List<ParticipanteUI> {
        val chamada = _chamadaAtualFlow.value ?: return emptyList()
        val peersAtivos = webRTCManager.peers.value.keys
        return chamada.usuarios
            .filter { it.usuarioId != usuarioIdAtual }
            .map { u ->
                ParticipanteUI(
                    id = u.usuarioId,
                    nome = u.usuarioNome,
                    fotoUrl = null,
                    isFalando = false,
                    status = if (u.usuarioId in peersAtivos) "Conectado" else "Aguardando",
                    mutadoLocalmente = false,
                    volume = 100,
                )
            }
    }

    // ═══ Vídeo / render (exposto para a UI Compose) ═════════════════════════
    /** Mapa reativo de peers remotos (id → PeerRemoto com videoTrack). */
    val peersFlow get() = webRTCManager.peers
    /** Track de vídeo local (preview da própria câmera; null = sem vídeo). */
    val localVideoTrackFlow get() = webRTCManager.localVideoTrackFlow
    /** Contexto EGL compartilhado para os SurfaceViewRenderer. */
    val eglBaseContext get() = webRTCManager.eglBaseContext

    // ═══ Internal ═══════════════════════════════════════════════════════════

    /**
     * Deriva a base URL do MediaMTX a partir da apiUrl configurada (DataStore),
     * trocando ".../api/" por ".../webrtc". Fallback: BuildConfig.MEDIAMTX_URL.
     * Bloqueante (runBlocking) — chamado uma vez em onCreate.
     */
    private fun resolverMediaMtxUrl(): String {
        val configurada = kotlinx.coroutines.runBlocking { userPreferences.apiUrl.first() }
        return if (!configurada.isNullOrBlank()) {
            configurada.trimEnd('/').removeSuffix("/api") + "/webrtc"
        } else {
            BuildConfig.MEDIAMTX_URL
        }
    }

    private suspend fun processarChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        usuarioIdAtual = userPreferences.userId.first() ?: 0
        ringtoneManager.iniciar()
        mostrarNotificacaoChamadaRecebida(chamadaId, usuarioNome)
        // O handler do socket é sobrescrito pelo SocketService, então populamos
        // _chamadaAtual aqui via API para que aceitarChamada()/recusarChamada()
        // tenham o id disponível depois.
        repository.processarEventoChamadaRecebida(chamadaId, usuarioId)
    }

    private fun stopSelfSafe() {
        scope.launch {
            delay(500L)
            chamadaServiceAtivo = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()
        }
    }

    // ═══ Timer ══════════════════════════════════════════════════════════════

    private fun iniciarTimerSeNecessario() {
        if (timerJob?.isActive == true) return
        chamadaIniciadaEm = System.currentTimeMillis()
        timerJob = scope.launch {
            while (isActive) {
                val decorridos = (System.currentTimeMillis() - chamadaIniciadaEm) / 1000
                _timerFlow.value = String.format("%02d:%02d", decorridos / 60, decorridos % 60)
                atualizarNotificacaoEmAndamento()
                delay(1000)
            }
        }
    }

    private fun pararTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerFlow.value = "00:00"
    }

    // ═══ Speaker ════════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneOn(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val target = if (on) {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            } else {
                devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            }
            target?.let { audioManager.setCommunicationDevice(it) } ?: run { audioManager.isSpeakerphoneOn = on }
        } else {
            audioManager.isSpeakerphoneOn = on
        }
    }

    // ═══ Notificacoes ═══════════════════════════════════════════════════════

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CHAMADAS,
                "Chamadas",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificacoes de chamadas de voz"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun criarNotificacaoForeground(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Conversa")
            .setContentText("Servico de chamadas ativo")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun mostrarNotificacaoChamadaRecebida(chamadaId: Int, usuarioNome: String) {
        NotificationConstants.adicionarChamadaRecebendo(chamadaId)

        val answerIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_ANSWER
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
        }
        val answerPI = PendingIntent.getBroadcast(
            this, chamadaId, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val declineIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_DECLINE
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
        }
        val declinePI = PendingIntent.getBroadcast(
            this, chamadaId + 1000, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val fsIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
            putExtra("is_incoming", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fsPI = PendingIntent.getActivity(
            this, chamadaId + 5000, fsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder().setName(usuarioNome).setImportant(true).build()
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePI, answerPI))
                .setContentIntent(fsPI)
                .setFullScreenIntent(fsPI, true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(60000)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(usuarioNome)
                .setContentText("Chamada de voz")
                .addAction(R.drawable.ic_call_end, "Recusar", declinePI)
                .addAction(R.drawable.ic_call, "Atender", answerPI)
                .setContentIntent(fsPI)
                .setFullScreenIntent(fsPI, true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setTimeoutAfter(60000)
                .build()
        }

        notificationManager.notify(NotificationConstants.getNotificationIdIncoming(chamadaId), notif)
    }

    private fun mostrarNotificacaoEmAndamento() {
        val chamada = _chamadaAtualFlow.value ?: return
        val chamadaId = chamada.id

        val openIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPI = PendingIntent.getActivity(
            this, chamadaId + 6000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val hangupIntent = Intent(this, ChamadaService::class.java).apply { action = ACTION_ENCERRAR }
        val hangupPI = PendingIntent.getService(
            this, 0, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val nomeContato = chamada.usuarios.firstOrNull { it.usuarioId != usuarioIdAtual }?.usuarioNome ?: "Chamada"
        val timer = _timerFlow.value

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder().setName(nomeContato).setImportant(true).build()
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, hangupPI))
                .setContentIntent(openPI)
                .setContentText(timer)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } else {
            NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("Em chamada com $nomeContato")
                .setContentText(timer)
                .setContentIntent(openPI)
                .addAction(R.drawable.ic_call_end, "Encerrar", hangupPI)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        val notifId = NotificationConstants.getNotificationIdOngoing(chamadaId)
        if (!foregroundNotificacaoExibida) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notifId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                )
            } else {
                startForeground(notifId, notif)
            }
            foregroundNotificacaoExibida = true
        } else {
            notificationManager.notify(notifId, notif)
        }
    }

    private fun atualizarNotificacaoEmAndamento() {
        val estado = _estadoFlow.value
        if (estado == EstadoChamadaService.EM_CHAMADA ||
            estado == EstadoChamadaService.CHAMANDO ||
            estado == EstadoChamadaService.INICIANDO_CHAMADA
        ) {
            mostrarNotificacaoEmAndamento()
        }
    }

    // ═══ WakeLock ═══════════════════════════════════════════════════════════

    private fun adquirirWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChamadaService:WakeLock").apply {
            acquire(30 * 60 * 1000L)
        }
    }

    private fun liberarWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
