package com.conversa.conversa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conversa.conversa.MainActivity
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.socket.SocketManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.utils.AppLifecycleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SocketService : Service() {

    // Interface para callbacks de chamadas
    interface CallListener {
        fun onChamadaRecebida(chamadaId: String, usuarioId: Int, usuarioNome: String?)
        fun onNovaMensagem(titulo: String, mensagem: String)
        fun onSocketConectado()
        fun onSocketDesconectado()
        fun onSocketErro(erro: String)
    }

    // Binder para vinculação com Activities
    inner class LocalBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    private val binder = LocalBinder()
    private var callListener: CallListener? = null
    private var isAppBound = false
    
    companion object {
        private const val TAG = "SocketService"

        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_CHAMADA = 1002
        private const val NOTIFICATION_ID_MENSAGEM = 1003

        private const val CHANNEL_ID_SERVICE = "conversa_service_channel"
        private const val CHANNEL_ID_CHAMADAS = "conversa_chamadas_channel"
        private const val CHANNEL_ID_MENSAGENS = "conversa_mensagens_channel"

        private const val ACTION_ACEITAR_CHAMADA = "com.conversa.ACTION_ACEITAR_CHAMADA"
        private const val ACTION_RECUSAR_CHAMADA = "com.conversa.ACTION_RECUSAR_CHAMADA"
        
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TOKEN = "auth_token"
        
        private var lastValidHost: String? = null
        private var lastValidPort: Int = 0
        private var lastValidToken: String? = null
        
        fun start(context: Context, host: String, port: Int, token: String) {
            val intent = Intent(context, SocketService::class.java).apply {
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_TOKEN, token)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, SocketService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var socketManager: SocketManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isRunning = false
    private var currentHost: String? = null
    private var currentPort: Int = 0
    private var currentToken: String? = null

    private var wasInBackground = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        criarCanaisNotificacao()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Conversa::SocketServiceWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L)

        // Inicia monitoramento do lifecycle do app
        iniciarMonitoramentoLifecycle()
    }

    /**
     * Monitora o lifecycle do app e re-registra listeners quando volta ao foreground
     */
    private fun iniciarMonitoramentoLifecycle() {
        scope.launch {
            while (isActive) {
                delay(500) // Verifica a cada 500ms

                val isInForeground = AppLifecycleManager.isAppInForeground

                // Detecta transição de BACKGROUND -> FOREGROUND
                if (isInForeground && wasInBackground) {
                    Log.d(TAG, "🔄 App voltou ao FOREGROUND - Re-registrando listeners")
                    if (::socketManager.isInitialized) {
                        registrarListenersSocket()
                    }
                }

                wasInBackground = !isInForeground
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service iniciado")
        
        val host = intent?.getStringExtra(EXTRA_HOST) ?: lastValidHost
        val token = intent?.getStringExtra(EXTRA_TOKEN) ?: lastValidToken
        val port = intent?.getIntExtra(EXTRA_PORT, lastValidPort) ?: lastValidPort

        if (host.isNullOrEmpty()) {
            Log.e(TAG, "Host não fornecido, parando service")
            stopSelf()
            return START_NOT_STICKY
        }

        if (token.isNullOrEmpty()) {
            Log.e(TAG, "Token não fornecido, parando service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        lastValidHost = host
        lastValidPort = port
        lastValidToken = token
        
        currentHost = host
        currentPort = port
        currentToken = token
        
        startForeground(NOTIFICATION_ID_SERVICE, criarNotificacaoService())
        
        if (!isRunning) {
            inicializarSocket(host, port, token)
            isRunning = true
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service vinculado")
        isAppBound = true
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service desvinculado")
        isAppBound = false
        // NÃO remove o callListener - permite rebind
        return true // Retorna true para permitir onRebind
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "Service re-vinculado")
        isAppBound = true

        // Re-registra listeners quando reconecta
        if (::socketManager.isInitialized) {
            registrarListenersSocket()
        }
    }

    // Método público para registrar listener
    fun setCallListener(listener: CallListener?) {
        callListener = listener
        Log.d(TAG, "CallListener ${if (listener != null) "registrado" else "removido"}")

        // CRÍTICO: Re-registra os listeners do SocketManager
        // Isso garante que o SocketService sempre receba eventos de chamada,
        // mesmo depois que ChamadaRepository sobrescreve os listeners
        if (listener != null && ::socketManager.isInitialized) {
            registrarListenersSocket()
        }
    }

    /**
     * Registra (ou re-registra) os listeners de eventos do SocketManager
     */
    private fun registrarListenersSocket() {
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "📞 Chamada recebida: chamadaId=$chamadaId, usuarioId=$usuarioId")

            // CRÍTICO: Buscar dados ANTES de mostrar qualquer UI
            scope.launch {
                val chamadaData = SocketServiceHelper.buscarDadosChamada(chamadaId, currentToken ?: "")

                // Formata nome e descrição com dados completos
                val nomeExibicao = SocketServiceHelper.formatarNomeExibicao(chamadaData, usuarioNome)
                val descricao = SocketServiceHelper.formatarTextoNotificacao(chamadaData)

                Log.d(TAG, "✅ Dados obtidos - Nome: $nomeExibicao, Descrição: $descricao")

                // Verifica se o dispositivo está bloqueado
                val isDeviceLocked = isDeviceLocked()
                Log.d(TAG, "Dispositivo bloqueado: $isDeviceLocked")

                if (isDeviceLocked) {
                    // Dispositivo bloqueado: abre tela de chamada fullscreen
                    Log.d(TAG, "Dispositivo bloqueado - Abrindo tela de chamada")
                    mostrarTelaChamadaFullscreen(chamadaId, usuarioId, nomeExibicao, chamadaData)
                } else {
                    // Dispositivo desbloqueado: mostra notificação com botões
                    Log.d(TAG, "Dispositivo desbloqueado - Mostrando notificação")
                    mostrarNotificacaoChamada(chamadaId, usuarioId, nomeExibicao, chamadaData)
                }

                // Notifica o listener se o app estiver conectado
                if (isAppBound && callListener != null) {
                    callListener?.onChamadaRecebida(chamadaId.toString(), usuarioId, nomeExibicao)
                }
            }
        }

        socketManager.onNovaMensagem = { titulo, mensagem ->
            Log.d(TAG, "Nova mensagem: $titulo")

            // Sempre mostra notificação
            mostrarNotificacaoMensagem(titulo, mensagem)

            // Também notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onNovaMensagem(titulo, mensagem)
            }
        }

        Log.d(TAG, "✅ Listeners do SocketManager re-registrados")
    }

    // Método público para obter o SocketManager
    fun getSocketManager(): SocketManager? {
        return if (::socketManager.isInitialized) socketManager else null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destruído")
        
        isRunning = false
        
        ChamadaActivity.sharedSocketManager = null
        
        if (::socketManager.isInitialized) {
            socketManager.desconectar()
        }
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        scope.cancel()
    }
    
    private fun criarCanaisNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Remove canais antigos para recriar com novas configurações
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_ID_SERVICE)
                notificationManager.deleteNotificationChannel(CHANNEL_ID_CHAMADAS)
                notificationManager.deleteNotificationChannel(CHANNEL_ID_MENSAGENS)
                Log.d(TAG, "Canais antigos removidos")
            } catch (e: Exception) {
                Log.d(TAG, "Canais ainda não existiam")
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Serviço Conversa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém conexão ativa para receber notificações"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val chamadasChannel = NotificationChannel(
                CHANNEL_ID_CHAMADAS,
                "Chamadas",
                NotificationManager.IMPORTANCE_MAX  // MAX para fullscreen intent e heads-up
            ).apply {
                description = "Notificações de chamadas recebidas"
                // Som e vibração desabilitados - controlados pelo RingtoneManager
                enableVibration(false)
                setSound(null, null) // Remove som do canal
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                setShowBadge(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            
            val mensagensChannel = NotificationChannel(
                CHANNEL_ID_MENSAGENS,
                "Mensagens",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de novas mensagens"
                enableVibration(true)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(chamadasChannel)
            notificationManager.createNotificationChannel(mensagensChannel)

            Log.d(TAG, "✅ Canais de notificação criados com padrão de vibração personalizado")
        }
    }
    
    private fun criarNotificacaoService(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Conversa")
            .setContentText("Conectado - Recebendo notificações")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun inicializarSocket(host: String, port: Int, token: String) {
        socketManager = SocketManager(this)
        
        ChamadaActivity.sharedSocketManager = socketManager
        
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "📞 Chamada recebida: chamadaId=$chamadaId, usuarioId=$usuarioId")

            // CRÍTICO: Buscar dados ANTES de mostrar qualquer UI
            scope.launch {
                val chamadaData = SocketServiceHelper.buscarDadosChamada(chamadaId, currentToken ?: "")

                // Formata nome e descrição com dados completos
                val nomeExibicao = SocketServiceHelper.formatarNomeExibicao(chamadaData, usuarioNome)
                val descricao = SocketServiceHelper.formatarTextoNotificacao(chamadaData)

                Log.d(TAG, "✅ Dados obtidos - Nome: $nomeExibicao, Descrição: $descricao")

                // Verifica se o dispositivo está bloqueado
                val isDeviceLocked = isDeviceLocked()
                Log.d(TAG, "Dispositivo bloqueado: $isDeviceLocked")

                if (isDeviceLocked) {
                    // Dispositivo bloqueado: abre tela de chamada fullscreen
                    Log.d(TAG, "Dispositivo bloqueado - Abrindo tela de chamada")
                    mostrarTelaChamadaFullscreen(chamadaId, usuarioId, nomeExibicao, chamadaData)
                } else {
                    // Dispositivo desbloqueado: mostra notificação com botões
                    Log.d(TAG, "Dispositivo desbloqueado - Mostrando notificação")
                    mostrarNotificacaoChamada(chamadaId, usuarioId, nomeExibicao, chamadaData)
                }

                // Notifica o listener se o app estiver conectado
                if (isAppBound && callListener != null) {
                    callListener?.onChamadaRecebida(chamadaId.toString(), usuarioId, nomeExibicao)
                }
            }
        }

        socketManager.onNovaMensagem = { titulo, mensagem ->
            Log.d(TAG, "Nova mensagem: $titulo")

            // Sempre mostra notificação
            mostrarNotificacaoMensagem(titulo, mensagem)

            // Também notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onNovaMensagem(titulo, mensagem)
            }
        }

        socketManager.onConectado = {
            Log.d(TAG, "Socket conectado")
            atualizarNotificacaoService("Conectado")

            if (isAppBound && callListener != null) {
                callListener?.onSocketConectado()
            }
        }

        socketManager.onDesconectado = {
            Log.d(TAG, "Socket desconectado")
            atualizarNotificacaoService("Desconectado - Reconectando...")

            if (isAppBound && callListener != null) {
                callListener?.onSocketDesconectado()
            }
        }

        socketManager.onErro = { erro ->
            Log.e(TAG, "Erro no socket: $erro")
            atualizarNotificacaoService("Erro - Tentando reconectar...")

            if (isAppBound && callListener != null) {
                callListener?.onSocketErro(erro)
            }
        }
        
        scope.launch {
            socketManager.conectar(host, port, token)
        }
    }
    
    private fun atualizarNotificacaoService(status: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Conversa")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
    }
    
    private fun mostrarNotificacaoMensagem(titulo: String, mensagem: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MENSAGENS)
            .setContentTitle(titulo)
            .setContentText(mensagem)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MENSAGEM, notification)
    }

    /**
     * Verifica se o dispositivo está bloqueado
     */
    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            keyguardManager.isDeviceLocked
        } else {
            @Suppress("DEPRECATION")
            keyguardManager.isKeyguardLocked
        }
    }

    /**
     * Abre a tela de chamada fullscreen (para dispositivo bloqueado)
     */
    private fun mostrarTelaChamadaFullscreen(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamadaData: com.conversa.conversa.data.model.ChamadaResponse?
    ) {
        val intent = Intent(this, ChamadaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)

            // Passa informações adicionais se disponíveis
            chamadaData?.let { chamada ->
                putExtra("EXTRA_TIPO_CHAMADA", chamada.tipo)
                putExtra("EXTRA_NUM_PARTICIPANTES", chamada.usuarios.size)
            }
        }
        startActivity(intent)
    }

    /**
     * Mostra notificação de chamada recebida com botões de atender/recusar
     */
    private fun mostrarNotificacaoChamada(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamadaData: com.conversa.conversa.data.model.ChamadaResponse?
    ) {
        Log.d(TAG, "📲 Preparando notificação para chamada $chamadaId de $usuarioNome")

        // Mostra notificação com dados completos
        atualizarNotificacaoChamada(chamadaId, usuarioId, usuarioNome, chamadaData)
    }

    private fun atualizarNotificacaoChamada(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamada: com.conversa.conversa.data.model.ChamadaResponse?
    ) {
        // Intent para abrir a tela de chamada ao clicar no corpo da notificação
        val fullScreenIntent = Intent(this, ChamadaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            chamadaId,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent DIRETO para atender chamada (abre Activity, não BroadcastReceiver)
        val aceitarIntent = Intent(this, ChamadaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            putExtra("auto_answer", true)
        }

        val aceitarPendingIntent = PendingIntent.getActivity(
            this,
            chamadaId + 1000,
            aceitarIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent para recusar chamada (usa BroadcastReceiver pois não precisa abrir Activity)
        val recusarIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ACTION_RECUSAR_CHAMADA
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
        }

        val recusarPendingIntent = PendingIntent.getBroadcast(
            this,
            chamadaId + 2000,
            recusarIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val tituloNotificacao = SocketServiceHelper.formatarNomeExibicao(chamada, usuarioNome)
        val textoNotificacao = SocketServiceHelper.formatarTextoNotificacao(chamada)

        val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVibrate(vibrationPattern)
            .addAction(
                R.drawable.ic_notification,
                "Recusar",
                recusarPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "Atender",
                aceitarPendingIntent
            )
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação exibida: $tituloNotificacao")
    }

    /**
     * Cancela a notificação de chamada recebida
     */
    fun cancelarNotificacaoChamada() {
        notificationManager.cancel(NOTIFICATION_ID_CHAMADA)
    }
}
