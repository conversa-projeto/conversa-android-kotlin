package com.example.conversaandroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.seudominio.conversa.R
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.presentation.chat.ChatActivity
import com.seudominio.conversa.presentation.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ConversaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    companion object {
        private const val CHANNEL_ID = "conversa_messages_channel"
        private const val CHANNEL_NAME = "Mensagens"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Salvar token e enviar para o servidor
        preferencesManager.saveFirebaseToken(token)
        // TODO: Enviar token para o servidor
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.data.let { data ->
            val conversaId = data["conversa_id"]?.toIntOrNull()
            val remetenteNome = data["remetente_nome"] ?: "Nova mensagem"
            val mensagem = data["mensagem"] ?: ""
            val tipo = data["tipo"] ?: "texto"

            sendNotification(conversaId, remetenteNome, mensagem, tipo)
        }
    }

    private fun sendNotification(
        conversaId: Int?,
        title: String,
        message: String,
        tipo: String
    ) {
        val intent = if (conversaId != null) {
            Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSA_ID, conversaId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            conversaId ?: 0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Criar canal de notificação para Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de novas mensagens"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val contentText = when (tipo) {
            "imagem" -> "📷 Imagem"
            "arquivo" -> "📎 Arquivo"
            "audio" -> "🎵 Áudio"
            else -> message
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))

        val notificationId = conversaId ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}