package com.conversa.conversa.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.conversa.conversa.MainActivity
import com.conversa.conversa.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador de notificações de mensagens agrupadas por conversa
 */
object MensagemNotificationManager {
    private const val TAG = "MensagemNotificationManager"
    private const val CHANNEL_ID_MENSAGENS = "conversa_mensagens_channel"
    private const val BASE_NOTIFICATION_ID = 2000 // Base para IDs de notificação de mensagens

    /**
     * Dados de uma mensagem para notificação
     */
    data class MensagemNotificacao(
        val conversaId: Int,
        val remetenteId: Int,
        val destinatarioId: Int,
        val titulo: String, // Nome do remetente
        val mensagem: String,
        val tipo: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Dados de uma conversa para a notificação
     */
    data class ConversaInfo(
        val conversaId: Int,
        var nomeConversa: String, // Nome do grupo ou contato
        val mensagens: MutableList<MensagemNotificacao> = mutableListOf()
    )

    // Armazena informações de conversas com mensagens
    private val conversasInfo = ConcurrentHashMap<Int, ConversaInfo>()

    /**
     * Adiciona uma nova mensagem e atualiza a notificação da conversa
     * @param titulo - Nome do remetente (quem enviou a mensagem)
     * @param nomeConversa - Nome da conversa/grupo (opcional, usa o titulo se não fornecido)
     */
    fun adicionarMensagem(
        context: Context,
        conversaId: Int,
        remetenteId: Int,
        destinatarioId: Int,
        titulo: String,
        mensagem: String,
        tipo: Int,
        nomeConversa: String? = null
    ) {
        Log.d(TAG, "📨 Adicionando mensagem - Conversa: $conversaId, Remetente: $titulo")

        val novaMensagem = MensagemNotificacao(
            conversaId = conversaId,
            remetenteId = remetenteId,
            destinatarioId = destinatarioId,
            titulo = titulo,
            mensagem = mensagem,
            tipo = tipo
        )

        // Obtém ou cria a info da conversa
        val conversaInfo = conversasInfo.getOrPut(conversaId) {
            ConversaInfo(
                conversaId = conversaId,
                nomeConversa = nomeConversa ?: titulo // Usa o título como fallback
            )
        }

        // Atualiza o nome da conversa se fornecido
        if (nomeConversa != null) {
            conversaInfo.nomeConversa = nomeConversa
        }

        // Adiciona a mensagem
        conversaInfo.mensagens.add(novaMensagem)

        // Atualiza a notificação
        atualizarNotificacaoConversa(context, conversaId)
    }

    /**
     * Atualiza a notificação de uma conversa específica
     */
    private fun atualizarNotificacaoConversa(context: Context, conversaId: Int) {
        val conversaInfo = conversasInfo[conversaId] ?: return

        if (conversaInfo.mensagens.isEmpty()) {
            Log.d(TAG, "Nenhuma mensagem para conversa $conversaId")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = BASE_NOTIFICATION_ID + conversaId

        val mensagens = conversaInfo.mensagens
        val ultimaMensagem = mensagens.last()
        val quantidadeMensagens = mensagens.size

        // Intent para abrir o app na conversa específica
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversa_id", conversaId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            conversaId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cria o estilo de notificação com múltiplas linhas
        val inboxStyle = NotificationCompat.InboxStyle()

        // Adiciona as últimas 5 mensagens ao estilo inbox
        // Mostra "Nome do Remetente: mensagem" para cada linha
        mensagens.takeLast(5).forEach { msg ->
            inboxStyle.addLine("${msg.titulo}: ${msg.mensagem}")
        }

        // Define o resumo
        val textoResumo = if (quantidadeMensagens > 1) {
            "$quantidadeMensagens novas mensagens"
        } else {
            "Nova mensagem"
        }
        inboxStyle.setSummaryText(textoResumo)

        // Título da notificação = Nome da conversa/grupo
        // Subtítulo = Nome do remetente da última mensagem
        // Texto = Apenas a mensagem (sem o nome do remetente)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MENSAGENS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(conversaInfo.nomeConversa) // Nome da conversa/grupo
            .setSubText(ultimaMensagem.titulo) // Nome do remetente
            .setContentText(ultimaMensagem.mensagem) // Apenas a mensagem
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(inboxStyle)
            .setNumber(quantidadeMensagens)
            .setGroup("group_conversa_$conversaId") // Agrupa por conversa
            .setWhen(ultimaMensagem.timestamp)
            .setShowWhen(true)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "✅ Notificação atualizada - Conversa: ${conversaInfo.nomeConversa}, Remetente: ${ultimaMensagem.titulo}, Total mensagens: $quantidadeMensagens")
    }

    /**
     * Limpa as mensagens de uma conversa específica (quando o usuário abre a conversa)
     */
    fun limparMensagensConversa(context: Context, conversaId: Int) {
        Log.d(TAG, "🧹 Limpando mensagens da conversa $conversaId")

        conversasInfo.remove(conversaId)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = BASE_NOTIFICATION_ID + conversaId
        notificationManager.cancel(notificationId)

        Log.d(TAG, "✅ Notificação cancelada para conversa $conversaId")
    }

    /**
     * Limpa todas as notificações de mensagens
     */
    fun limparTodasMensagens(context: Context) {
        Log.d(TAG, "🧹 Limpando todas as mensagens")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        conversasInfo.keys.forEach { conversaId ->
            val notificationId = BASE_NOTIFICATION_ID + conversaId
            notificationManager.cancel(notificationId)
        }

        conversasInfo.clear()
        Log.d(TAG, "✅ Todas as notificações canceladas")
    }

    /**
     * Retorna a quantidade de mensagens não lidas de uma conversa
     */
    fun getQuantidadeMensagensConversa(conversaId: Int): Int {
        return conversasInfo[conversaId]?.mensagens?.size ?: 0
    }

    /**
     * Retorna a quantidade total de conversas com mensagens não lidas
     */
    fun getQuantidadeConversasComMensagens(): Int {
        return conversasInfo.size
    }
}
