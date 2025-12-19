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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Interface para receber callbacks de chamadas quando app está vinculado
 */
interface CallListener {
    fun onChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String)
    fun onNovaMensagem(titulo: String, mensagem: String)
}

/**
 * Service em foreground que mantém conexão TCP ativa
 * Recebe notificações de chamadas e mensagens mesmo com app fechado
 */
class SocketService : Service() {

    companion object {
        private const val TAG = "SocketService"

        // IDs de notificação
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_MENSAGEM = 1003

        // Canais de notificação
        private const val CHANNEL_ID_SERVICE = "conversa_service_channel"
        private const val CHANNEL_ID_CHAMADAS = "conversa_chamadas_channel"
        private const val CHANNEL_ID_MENSAGENS = "conversa_mensagens_channel"

        // Extras do Intent
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TOKEN = "auth_token"

        /**
         * Inicia o service
         */
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

        /**
         * Para o service
         */
        fun stop(context: Context) {
            val intent = Intent(context, SocketService::class.java)
            context.stopService(intent)
        }
    }

    // Binder para vincular com Activities
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    private lateinit var socketManager: SocketManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var ringtoneManager: CallRingtoneManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isRunning = false
    private var currentHost: String? = null
    private var currentPort: Int = 0
    private var currentToken: String? = null

    // Controle de vinculação
    private var isAppBound = false
    private var callListener: CallListener? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ringtoneManager = CallRingtoneManager(this)

        // Cria canais de notificação
        criarCanaisNotificacao()

        // Adquire WakeLock para manter CPU ativa
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Conversa::SocketServiceWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L)
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
        callListener = null
        return super.onUnbind(intent)
    }

    /**
     * Registra listener para receber callbacks de chamadas
     */
    fun setCallListener(listener: CallListener?) {
        callListener = listener
        Log.d(TAG, "CallListener ${if (listener != null) "registrado" else "removido"}")
    }

    /**
     * Retorna se o app está vinculado ao service
     */
    fun isAppBound(): Boolean = isAppBound

    /**
     * Cancela notificação de chamada e para ringtone
     */
    fun cancelarNotificacaoChamada() {
        notificationManager.cancel(NOTIFICATION_ID_CHAMADA)
        ringtoneManager.parar()
        Log.d(TAG, "Notificação de chamada cancelada")
    }

    /**
     * Para ringtone sem cancelar notificação
     */
    fun pararRingtone() {
        ringtoneManager.parar()
        Log.d(TAG, "Ringtone parado")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destruído")

        isRunning = false

        // Remove referência compartilhada
        ChamadaIncomingActivity.sharedSocketManager = null

        // Para ringtone
        if (::ringtoneManager.isInitialized) {
            ringtoneManager.release()
        }

        // Desconecta socket
        if (::socketManager.isInitialized) {
            socketManager.desconectar()
        }

        // Libera WakeLock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Cancela coroutines
        scope.cancel()
    }
    
    private fun criarCanaisNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Serviço Conversa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém conexão ativa para receber notificações"
                setShowBadge(false)
            }
            
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val chamadasChannel = NotificationChannel(
                CHANNEL_ID_CHAMADAS,
                "Chamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas recebidas"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(true)
                
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
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
            Log.d(TAG, "Chamada recebida: $chamadaId de $usuarioNome")

            if (isAppBound && callListener != null) {
                // App vinculado: envia via callback direto
                Log.d(TAG, "Enviando chamada via callback direto")
                callListener?.onChamadaRecebida(chamadaId, usuarioId, usuarioNome)
            } else {
                // App não vinculado: mostra notificação
                Log.d(TAG, "Enviando chamada via notificação")
                mostrarNotificacaoChamada(chamadaId, usuarioId, usuarioNome)
            }
        }

        socketManager.onNovaMensagem = { titulo, mensagem ->
            Log.d(TAG, "Nova mensagem: $titulo")

            if (isAppBound && callListener != null) {
                // App vinculado: envia via callback direto
                Log.d(TAG, "Enviando mensagem via callback direto")
                callListener?.onNovaMensagem(titulo, mensagem)
            } else {
                // App não vinculado: mostra notificação
                Log.d(TAG, "Enviando mensagem via notificação")
                mostrarNotificacaoMensagem(titulo, mensagem)
            }
        }
        
        socketManager.onConectado = {
            Log.d(TAG, "Socket conectado")
            atualizarNotificacaoService("Conectado")
        }
        
        socketManager.onDesconectado = {
            Log.d(TAG, "Socket desconectado")
            atualizarNotificacaoService("Desconectado - Reconectando...")
        }
        
        socketManager.onErro = { erro ->
            Log.e(TAG, "Erro no socket: $erro")
            atualizarNotificacaoService("Erro - Tentando reconectar...")
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
    
    /**
     * Mostra notificação de chamada recebida
     */
    private fun mostrarNotificacaoChamada(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        // Inicia ringtone/vibração IMEDIATAMENTE
        ringtoneManager.iniciar()

        // Intent para abrir tela de chamada
        val intent = Intent(this, ChamadaIncomingActivity::class.java).apply {
            putExtra(ChamadaIncomingActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaIncomingActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaIncomingActivity.EXTRA_USUARIO_NOME, usuarioNome)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chamadaId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cria notificação SEM vibração (CallRingtoneManager controla)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setContentTitle("Chamada de $usuarioNome")
            .setContentText("Toque para atender")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Não pode ser descartada
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Mostra na tela bloqueada
            .setFullScreenIntent(pendingIntent, true) // Abre app automaticamente
            .build()

        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)

        // Abre automaticamente a tela de chamada
        startActivity(intent)
    }
    
    /**
     * Mostra notificação de nova mensagem
     */
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
}
