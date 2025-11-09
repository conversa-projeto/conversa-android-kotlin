package com.conversa.conversa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conversa.conversa.MainActivity
import com.conversa.conversa.R
import com.conversa.conversa.data.socket.SocketManager
import com.conversa.conversa.ui.chamada.ChamadaIncomingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service em foreground que mantém conexão TCP ativa
 * Recebe notificações de chamadas e mensagens mesmo com app fechado
 */
class SocketService : Service() {
    
    companion object {
        private const val TAG = "SocketService"
        
        // IDs de notificação
        private const val NOTIFICATION_ID_SERVICE = 1001
        private const val NOTIFICATION_ID_CHAMADA = 1002
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
        
        // Cria canais de notificação
        criarCanaisNotificacao()
        
        // Adquire WakeLock para manter CPU ativa
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Conversa::SocketServiceWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L) // 10 minutos, será renovado
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service iniciado")
        
        // Extrai parâmetros
        val host = intent?.getStringExtra(EXTRA_HOST)
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        val port = intent?.getIntExtra(EXTRA_PORT, 8090) ?: 8090

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
        
        currentHost = host
        currentPort = port
        currentToken = token
        
        // Inicia em foreground com notificação persistente
        startForeground(NOTIFICATION_ID_SERVICE, criarNotificacaoService())
        
        // Inicializa SocketManager se ainda não foi
        if (!isRunning) {
            inicializarSocket(host, port, token)
            isRunning = true
        }
        
        // START_STICKY: reinicia service se for morto pelo sistema
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        // Service não é bound
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destruído")
        
        isRunning = false
        
        // Remove referência compartilhada
        ChamadaIncomingActivity.sharedSocketManager = null
        
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
    
    /**
     * Cria canais de notificação (Android 8+)
     */
    private fun criarCanaisNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal do service (baixa prioridade)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Serviço Conversa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém conexão ativa para receber notificações"
                setShowBadge(false)
            }
            
            // Canal de chamadas (alta prioridade)
            val chamadasChannel = NotificationChannel(
                CHANNEL_ID_CHAMADAS,
                "Chamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de chamadas recebidas"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            
            // Canal de mensagens (prioridade normal)
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
    
    /**
     * Cria notificação persistente do service
     */
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
            .setSmallIcon(R.drawable.ic_notification) // Você precisa adicionar esse ícone
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Não pode ser descartada pelo usuário
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Inicializa conexão socket
     */
    private fun inicializarSocket(host: String, port: Int, token: String) {
        socketManager = SocketManager(this)
        
        // Compartilha com Activities
        ChamadaIncomingActivity.sharedSocketManager = socketManager
        
        // Configura callbacks
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            Log.d(TAG, "Chamada recebida: $chamadaId de $usuarioNome")
            mostrarNotificacaoChamada(chamadaId, usuarioId, usuarioNome)
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
        
        // Conecta
        scope.launch {
            socketManager.conectar(host, port, token)
        }
    }
    
    /**
     * Atualiza notificação do service
     */
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
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setContentTitle("Chamada de $usuarioNome")
            .setContentText("Toque para atender")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true) // Abre app automaticamente
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
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
