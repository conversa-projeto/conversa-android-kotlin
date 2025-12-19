package com.conversa.conversa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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

class SocketService : Service() {
    
    companion object {
        private const val TAG = "SocketService"
        
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_MENSAGEM = 1003
        
        private const val CHANNEL_ID_SERVICE = "conversa_service_channel"
        private const val CHANNEL_ID_CHAMADAS = "conversa_chamadas_channel"
        private const val CHANNEL_ID_MENSAGENS = "conversa_mensagens_channel"
        
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
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
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
            Log.d(TAG, "📞 Chamada recebida: chamadaId=$chamadaId, usuarioId=$usuarioId")
            
            val broadcastIntent = Intent("com.conversa.CHAMADA_RECEBIDA").apply {
                putExtra("chamadaId", chamadaId)
                putExtra("usuarioId", usuarioId)
                putExtra("usuarioNome", usuarioNome)
            }
            
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
        }
        
        socketManager.onNovaMensagem = { titulo, mensagem ->
            Log.d(TAG, "Nova mensagem: $titulo")
            mostrarNotificacaoMensagem(titulo, mensagem)
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
