package com.conversa.conversa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conversa.conversa.MainActivity
import com.conversa.conversa.R
import com.conversa.conversa.data.socket.SocketManager
import com.conversa.conversa.utils.AppLifecycleManager
import com.conversa.conversa.notification.MensagemNotificationManager
import com.conversa.conversa.service.ChamadaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Constantes para Intents enviadas ao ChamadaService
 */
object ChamadaServiceActions {
    const val ACTION_CHAMADA_RECEBIDA = "com.conversa.chamada.RECEBIDA"
    const val ACTION_CHAMADA_FINALIZADA = "com.conversa.chamada.FINALIZADA"
    const val ACTION_USUARIO_ENTROU = "com.conversa.chamada.USUARIO_ENTROU"
    const val ACTION_USUARIO_SAIU = "com.conversa.chamada.USUARIO_SAIU"
    const val ACTION_USUARIO_RECUSOU = "com.conversa.chamada.USUARIO_RECUSOU"
}

class SocketService : Service() {

    // Interface para callbacks de chamadas e mensagens
    interface CallListener {
        fun onChamadaRecebida(chamadaId: String, usuarioId: Int, usuarioNome: String?)
        fun onNovaMensagem(conversaId: Int, remetenteId: Int, destinatarioId: Int, titulo: String, subtitulo: String, mensagem: String, tipoConversa: Int)
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

        private const val NOTIFICATION_ID_SERVICE = 1000

        private const val CHANNEL_ID_SERVICE = "conversa_service_channel"

        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TOKEN = "auth_token"

        private var lastValidHost: String? = null
        private var lastValidPort: Int = 0
        private var lastValidToken: String? = null

        /**
         * Flag que indica se o ChamadaService está ativo com uma chamada.
         * Atualizada pelo ChamadaService quando inicia/finaliza uma chamada.
         *
         * Eventos que NÃO devem iniciar o ChamadaService verificam esta flag:
         * - ACTION_CHAMADA_FINALIZADA
         * - ACTION_USUARIO_ENTROU
         * - ACTION_USUARIO_SAIU
         * - ACTION_USUARIO_RECUSOU
         *
         * Apenas ACTION_CHAMADA_RECEBIDA pode iniciar o serviço.
         */
        @Volatile
        var chamadaServiceAtivo: Boolean = false
        
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
        // WakeLock sem timeout - foreground service mantém ativo
        @Suppress("WakelockTimeout")
        wakeLock.acquire()

        // Inicia monitoramento do lifecycle do app
        iniciarMonitoramentoLifecycle()
    }

    /**
     * Monitora o lifecycle do app e re-registra listeners quando volta ao foreground
     * Também verifica se o socket ainda está conectado e reconecta se necessário
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

                        // Verifica se socket ainda está conectado
                        if (!socketManager.isConectado()) {
                            Log.d(TAG, "⚠️ Socket desconectado - Forçando reconexão")
                            socketManager.resetReconnectAttempts()
                            val host = currentHost
                            val token = currentToken
                            if (host != null && token != null && currentPort > 0) {
                                socketManager.conectar(host, currentPort, token)
                            }
                        }
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
            Log.d(TAG, "📞 Chamada recebida: enviando Intent para ChamadaService")

            val intent = Intent(this, ChamadaService::class.java).apply {
                action = ChamadaServiceActions.ACTION_CHAMADA_RECEBIDA
                putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                putExtra(ChamadaService.EXTRA_USUARIO_NOME, usuarioNome)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onChamadaRecebida(chamadaId.toString(), usuarioId, usuarioNome)
            }
        }

        socketManager.onChamadaFinalizada = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "📴 Chamada finalizada: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_CHAMADA_FINALIZADA
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "📴 Chamada finalizada ignorada: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioEntrou = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "👤 Usuário entrou: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_ENTROU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "👤 Usuário entrou ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioSaiu = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "👋 Usuário saiu: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_SAIU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "👋 Usuário saiu ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioRecusou = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "❌ Usuário recusou: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_RECUSOU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "❌ Usuário recusou ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onNovaMensagem = { conversaId, remetenteId, destinatarioId, titulo, subtitulo, mensagem, tipoConversa ->
            Log.d(TAG, "📨 Nova mensagem - Conversa: $conversaId, Remetente: $subtitulo, Tipo: $tipoConversa")

            // Adiciona à notificação agrupada
            // titulo = nome da conversa (grupo ou contato)
            // subtitulo = nome do remetente
            // tipoConversa = 1 (individual) ou 2 (grupo)
            MensagemNotificationManager.adicionarMensagem(
                this@SocketService,
                conversaId,
                remetenteId,
                destinatarioId,
                subtitulo,      // Nome do remetente (para Person na notificação)
                mensagem,
                tipoConversa,   // Tipo da conversa (1=individual, 2=grupo)
                titulo          // Nome da conversa (título da notificação)
            )

            // Também notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onNovaMensagem(conversaId, remetenteId, destinatarioId, titulo, subtitulo, mensagem, tipoConversa)
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
                notificationManager.deleteNotificationChannel(NotificationConstants.CHANNEL_ID_MENSAGENS)
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

            val mensagensChannel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_MENSAGENS,
                "Mensagens",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novas mensagens"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(mensagensChannel)

            Log.d(TAG, "✅ Canais de notificação criados")
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
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun inicializarSocket(host: String, port: Int, token: String) {
        socketManager = SocketManager(this)

        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "📞 Chamada recebida: enviando Intent para ChamadaService")

            val intent = Intent(this, ChamadaService::class.java).apply {
                action = ChamadaServiceActions.ACTION_CHAMADA_RECEBIDA
                putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                putExtra(ChamadaService.EXTRA_USUARIO_NOME, usuarioNome)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onChamadaRecebida(chamadaId.toString(), usuarioId, usuarioNome)
            }
        }

        socketManager.onChamadaFinalizada = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "📴 Chamada finalizada: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_CHAMADA_FINALIZADA
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "📴 Chamada finalizada ignorada: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioEntrou = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "👤 Usuário entrou: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_ENTROU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "👤 Usuário entrou ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioSaiu = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "👋 Usuário saiu: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_SAIU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "👋 Usuário saiu ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onUsuarioRecusou = { chamadaId, usuarioId ->
            // Só envia Intent se ChamadaService está ativo (há chamada em andamento)
            if (chamadaServiceAtivo) {
                Log.d(TAG, "❌ Usuário recusou: enviando Intent para ChamadaService")

                val intent = Intent(this, ChamadaService::class.java).apply {
                    action = ChamadaServiceActions.ACTION_USUARIO_RECUSOU
                    putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
                    putExtra(ChamadaService.EXTRA_USUARIO_ID, usuarioId)
                }
                startService(intent)
            } else {
                Log.d(TAG, "❌ Usuário recusou ignorado: ChamadaService não está ativo")
            }
        }

        socketManager.onNovaMensagem = { conversaId, remetenteId, destinatarioId, titulo, subtitulo, mensagem, tipoConversa ->
            Log.d(TAG, "📨 Nova mensagem - Conversa: $conversaId, Remetente: $subtitulo, Tipo: $tipoConversa")

            // Adiciona à notificação agrupada
            // titulo = nome da conversa (grupo ou contato)
            // subtitulo = nome do remetente
            // tipoConversa = 1 (individual) ou 2 (grupo)
            MensagemNotificationManager.adicionarMensagem(
                this@SocketService,
                conversaId,
                remetenteId,
                destinatarioId,
                subtitulo,      // Nome do remetente (para Person na notificação)
                mensagem,
                tipoConversa,   // Tipo da conversa (1=individual, 2=grupo)
                titulo          // Nome da conversa (título da notificação)
            )

            // Também notifica o listener se o app estiver conectado
            if (isAppBound && callListener != null) {
                callListener?.onNovaMensagem(conversaId, remetenteId, destinatarioId, titulo, subtitulo, mensagem, tipoConversa)
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
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
    }

    /**
     * Limpa as notificações de mensagens de uma conversa específica
     * Deve ser chamado quando o usuário abre uma conversa
     */
    fun limparNotificacoesConversa(conversaId: Int) {
        MensagemNotificationManager.limparMensagensConversa(this, conversaId)
    }

    /**
     * Limpa todas as notificações de mensagens
     */
    fun limparTodasNotificacoesMensagens() {
        MensagemNotificationManager.limparTodasMensagens(this)
    }
}
