package com.conversa.conversa.notification

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conversa.conversa.R
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.service.ChamadaActionReceiver
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.utils.AppLifecycleManager
import com.conversa.conversa.utils.ChamadaBroadcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChamadaNotificationManager(
    private val context: Context,
    private val api: ConversaApi,
    private val token: String
) {
    
    companion object {
        private const val TAG = "ChamadaNotificationManager"
        private const val NOTIFICATION_ID_CHAMADA = 1002
        const val CHANNEL_ID_CHAMADAS = "conversa_chamadas_channel"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    
    suspend fun onChamadaRecebida(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        tipoChamada: Int
    ) {
        try {
            Log.d(TAG, "📞 onChamadaRecebida - chamadaId=$chamadaId, usuarioId=$usuarioId")
            
            val chamada = buscarDadosChamada(chamadaId)
            
            if (chamada == null) {
                Log.e(TAG, "❌ Não foi possível obter dados da chamada")
                return
            }
            
            val isTelaBloqueda = keyguardManager.isKeyguardLocked
            
            Log.d(TAG, "📱 Estado - TelaBloqueda: $isTelaBloqueda")
            
            if (isTelaBloqueda) {
                Log.d(TAG, "🔒 Tela BLOQUEADA → Notificação fullscreen")
                mostrarNotificacaoFullscreen(chamadaId, usuarioId, usuarioNome, chamada)
            } else {
                Log.d(TAG, "📲 Tela DESBLOQUEADA → Notificação com botões")
                mostrarNotificacaoComBotoes(chamadaId, usuarioId, usuarioNome, chamada)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar chamada recebida", e)
        }
    }
    
    private suspend fun buscarDadosChamada(chamadaId: Int): ChamadaResponse? {
        return try {
            Log.d(TAG, "🔍 Buscando dados da chamada $chamadaId...")
            
            val response = withContext(Dispatchers.IO) {
                api.obterDadosChamada("Bearer $token", chamadaId)
            }
            
            if (response.isSuccessful) {
                val chamada = response.body()
                Log.d(TAG, "✅ Dados obtidos: ${chamada?.usuarios?.size} participantes")
                chamada
            } else {
                Log.e(TAG, "❌ Erro API: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao buscar dados", e)
            null
        }
    }
    
    private fun mostrarNotificacaoComBotoes(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamada: ChamadaResponse
    ) {
        Log.d(TAG, "📲 Criando notificação com botões")
        
        val tituloNotificacao = formatarTituloNotificacao(chamada, usuarioNome)
        val textoNotificacao = formatarTextoNotificacao(chamada.tipo)
        
        val answerPendingIntent = criarPendingIntentAtender(chamadaId, usuarioId)
        val declinePendingIntent = criarPendingIntentRecusar(chamadaId)
        
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setSound(soundUri)
            .setOngoing(false)
            .setTimeoutAfter(60000)
            .addAction(R.drawable.ic_call_end, context.getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, context.getString(R.string.atender), answerPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação com botões exibida")
    }
    
    private fun mostrarNotificacaoFullscreen(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamada: ChamadaResponse
    ) {
        Log.d(TAG, "🔒 Criando notificação fullscreen")
        
        val tituloNotificacao = formatarTituloNotificacao(chamada, usuarioNome)
        val textoNotificacao = formatarTextoNotificacao(chamada.tipo)
        
        val activityIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            chamadaId,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val answerPendingIntent = criarPendingIntentAtender(chamadaId, usuarioId)
        val declinePendingIntent = criarPendingIntentRecusar(chamadaId)
        
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setSound(soundUri)
            .setOngoing(false)
            .setTimeoutAfter(60000)
            .addAction(R.drawable.ic_call_end, context.getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, context.getString(R.string.atender), answerPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação fullscreen exibida")
    }
    
    private fun formatarTituloNotificacao(chamada: ChamadaResponse, usuarioNome: String): String {
        return if (chamada.tipo == 2) {
            "$usuarioNome (Grupo - ${chamada.usuarios.size} pessoas)"
        } else {
            usuarioNome
        }
    }
    
    private fun formatarTextoNotificacao(tipoChamada: Int): String {
        return if (tipoChamada == 2) {
            context.getString(R.string.chamada_grupo)
        } else {
            "Chamada de voz"
        }
    }
    
    private fun criarPendingIntentAtender(chamadaId: Int, usuarioId: Int): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val intent = Intent(context, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_ANSWER
            putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActionReceiver.EXTRA_USUARIO_ID, usuarioId)
        }
        
        return PendingIntent.getBroadcast(context, chamadaId, intent, flags)
    }
    
    private fun criarPendingIntentRecusar(chamadaId: Int): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val intent = Intent(context, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_DECLINE
            putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
        }
        
        return PendingIntent.getBroadcast(context, chamadaId + 100000, intent, flags)
    }
}
