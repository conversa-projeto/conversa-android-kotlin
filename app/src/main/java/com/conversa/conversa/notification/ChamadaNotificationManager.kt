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
import com.conversa.conversa.service.ChamadaRingtoneManager
import com.conversa.conversa.ui.chamada.ChamadaActivity
import com.conversa.conversa.utils.AppLifecycleManager
import com.conversa.conversa.utils.ChamadaBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val ringtoneManager = ChamadaRingtoneManager.getInstance(context)
    
    suspend fun onChamadaRecebida(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        tipoChamada: Int
    ) {
        try {
            val instanceId = System.identityHashCode(this)
            val threadName = Thread.currentThread().name
            val ringtoneManagerId = System.identityHashCode(ringtoneManager)

            Log.d(TAG, "╔═══════════════════════════════════════════════╗")
            Log.d(TAG, "║ 📞 onChamadaRecebida INICIADO                ║")
            Log.d(TAG, "╠═══════════════════════════════════════════════╣")
            Log.d(TAG, "║ chamadaId: $chamadaId")
            Log.d(TAG, "║ usuarioId: $usuarioId")
            Log.d(TAG, "║ usuarioNome: $usuarioNome")
            Log.d(TAG, "║ Thread: $threadName")
            Log.d(TAG, "║ NotificationManager: @$instanceId")
            Log.d(TAG, "║ RingtoneManager: @$ringtoneManagerId")
            Log.d(TAG, "╚═══════════════════════════════════════════════╝")

            // PASSO 1: Iniciar ringtone IMEDIATAMENTE
            Log.d(TAG, "🔔 Chamando ringtoneManager.iniciar()...")
            ringtoneManager.iniciar()
            Log.d(TAG, "🔔 ringtoneManager.iniciar() retornou")

            // SEMPRE mostrar notificação fullscreen com heads-up
            Log.d(TAG, "📲 Mostrando notificação com fullscreen intent e heads-up")

            // PASSO 2: Mostrar notificação fullscreen IMEDIATAMENTE
            mostrarNotificacaoFullscreen(chamadaId, usuarioId, usuarioNome)

            // PASSO 3: Buscar dados em background e atualizar notificação
            CoroutineScope(Dispatchers.IO).launch {
                val chamada = buscarDadosChamada(chamadaId)

                if (chamada != null) {
                    Log.d(TAG, "✅ Dados obtidos, atualizando notificação")
                    withContext(Dispatchers.Main) {
                        mostrarNotificacaoFullscreen(chamadaId, usuarioId, usuarioNome, chamada)
                    }
                } else {
                    Log.w(TAG, "⚠️ Não foi possível obter dados adicionais da chamada")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao processar chamada recebida", e)
        }
    }

    private fun mostrarNotificacaoFullscreen(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamada: ChamadaResponse? = null
    ) {
        Log.d(TAG, "📲 Criando notificação com fullscreen intent")

        val tituloNotificacao = if (chamada != null) {
            formatarTituloNotificacao(chamada, usuarioNome)
        } else {
            usuarioNome.ifEmpty { "Chamada recebida" }
        }

        val textoNotificacao = if (chamada != null) {
            formatarTextoNotificacao(chamada.tipo)
        } else {
            "Chamada de voz"
        }

        // Intent principal da notificação (fullscreen)
        val fullScreenIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            chamadaId,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent do conteúdo (quando o usuário clicar na notificação minimizada)
        val contentIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            chamadaId + 10000,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPendingIntent = criarPendingIntentAtender(chamadaId, usuarioId)
        val declinePendingIntent = criarPendingIntentRecusar(chamadaId)

        // Estilo de chamada (BigText para mostrar mais informações)
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(textoNotificacao)
            .setBigContentTitle(tituloNotificacao)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true) // IMPORTANTE: Abre em tela cheia
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(120000)
            .setDefaults(0) // Desabilita sons/vibrações padrão
            .setSilent(true) // Som e vibração gerenciados pelo ChamadaRingtoneManager
            .setOnlyAlertOnce(false) // Permite tocar em todas as chamadas
            .setStyle(bigTextStyle) // Estilo para mostrar informações completas
            .addAction(R.drawable.ic_call_end, context.getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, context.getString(R.string.atender), answerPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação fullscreen exibida")
    }

    fun pararRingtone() {
        val instanceId = System.identityHashCode(this)
        val threadName = Thread.currentThread().name
        val ringtoneManagerId = System.identityHashCode(ringtoneManager)

        Log.d(TAG, "╔═══════════════════════════════════════════════╗")
        Log.d(TAG, "║ 🔕 pararRingtone CHAMADO                     ║")
        Log.d(TAG, "╠═══════════════════════════════════════════════╣")
        Log.d(TAG, "║ Thread: $threadName")
        Log.d(TAG, "║ NotificationManager: @$instanceId")
        Log.d(TAG, "║ RingtoneManager: @$ringtoneManagerId")
        Log.d(TAG, "╚═══════════════════════════════════════════════╝")

        ringtoneManager.parar()
        Log.d(TAG, "🔕 Ringtone parado - retornou")
    }

    fun cancelarNotificacao() {
        notificationManager.cancel(NOTIFICATION_ID_CHAMADA)
        Log.d(TAG, "🔕 Notificação de chamada cancelada")
    }

    fun release() {
        ringtoneManager.release()
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
    
    private fun mostrarNotificacaoComBotoesBasica(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String
    ) {
        Log.d(TAG, "📲 Criando notificação básica com botões")

        val tituloNotificacao = usuarioNome.ifEmpty { "Chamada recebida" }
        val textoNotificacao = "Chamada de voz"

        // Intent para abrir ChamadaActivity ao clicar no corpo da notificação
        val contentIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            chamadaId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPendingIntent = criarPendingIntentAtender(chamadaId, usuarioId)
        val declinePendingIntent = criarPendingIntentRecusar(chamadaId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent) // Abre ChamadaActivity ao clicar
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true) // Não pode ser removida pelo usuário
            .setTimeoutAfter(120000)
            .setSilent(true) // Som e vibração gerenciados pelo ChamadaRingtoneManager
            .addAction(R.drawable.ic_call_end, context.getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, context.getString(R.string.atender), answerPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação básica com botões exibida")
    }

    private fun mostrarNotificacaoComBotoes(
        chamadaId: Int,
        usuarioId: Int,
        usuarioNome: String,
        chamada: ChamadaResponse
    ) {
        Log.d(TAG, "📲 Criando notificação completa com botões")

        val tituloNotificacao = formatarTituloNotificacao(chamada, usuarioNome)
        val textoNotificacao = formatarTextoNotificacao(chamada.tipo)

        // Intent para abrir ChamadaActivity ao clicar no corpo da notificação
        val contentIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            chamadaId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPendingIntent = criarPendingIntentAtender(chamadaId, usuarioId)
        val declinePendingIntent = criarPendingIntentRecusar(chamadaId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAMADAS)
            .setContentTitle(tituloNotificacao)
            .setContentText(textoNotificacao)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent) // Abre ChamadaActivity ao clicar
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true) // Não pode ser removida pelo usuário
            .setTimeoutAfter(120000)
            .setSilent(true) // Som e vibração gerenciados pelo ChamadaRingtoneManager
            .addAction(R.drawable.ic_call_end, context.getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, context.getString(R.string.atender), answerPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
        Log.d(TAG, "✅ Notificação completa com botões exibida")
    }

    private fun formatarTituloNotificacao(chamada: ChamadaResponse, usuarioNome: String): String {
        // Busca o nome de quem criou a chamada (quem está ligando)
        val nomeCriador = chamada.usuarios.find { it.usuarioId == chamada.criadoPor }?.usuarioNome
            ?: usuarioNome

        val titulo = if (chamada.tipo == 2) {
            "$nomeCriador (Grupo - ${chamada.usuarios.size} pessoas)"
        } else {
            nomeCriador
        }

        Log.d(TAG, "📝 Título formatado: $titulo (criadoPor=${chamada.criadoPor})")
        return titulo
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
