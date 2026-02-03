package com.conversa.conversa.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.conversa.conversa.MainActivity
import com.conversa.conversa.R
import com.conversa.conversa.service.NotificationConstants
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador de notificações de mensagens agrupadas por conversa
 * 
 * Utiliza MessagingStyle para exibir mensagens no estilo nativo do Android
 * e RemoteInput para permitir resposta direta da notificação.
 */
object MensagemNotificationManager {
    private const val TAG = "MensagemNotificationManager"

    /** Chave para extrair texto da resposta direta */
    const val KEY_TEXT_REPLY = "key_text_reply"

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

    // Cache de Person objects para reutilização
    private val personCache = ConcurrentHashMap<Int, Person>()

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
     * Cria ou obtém um Person object para um remetente
     */
    private fun getPerson(remetenteId: Int, nome: String): Person {
        return personCache.getOrPut(remetenteId) {
            Person.Builder()
                .setKey(remetenteId.toString())
                .setName(nome)
                .build()
        }
    }

    /**
     * Atualiza a notificação de uma conversa específica usando MessagingStyle
     */
    private fun atualizarNotificacaoConversa(context: Context, conversaId: Int) {
        val conversaInfo = conversasInfo[conversaId] ?: return

        if (conversaInfo.mensagens.isEmpty()) {
            Log.d(TAG, "Nenhuma mensagem para conversa $conversaId")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationConstants.getNotificationIdMensagem(conversaId)

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

        // Cria RemoteInput para resposta direta
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Responder")
            .build()

        // Intent para resposta direta
        val replyIntent = Intent(context, MensagemActionReceiver::class.java).apply {
            action = MensagemActionReceiver.ACTION_REPLY
            putExtra(MensagemActionReceiver.EXTRA_CONVERSA_ID, conversaId)
            putExtra(MensagemActionReceiver.EXTRA_DESTINATARIO_ID, ultimaMensagem.remetenteId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            conversaId,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cria ação de resposta direta
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Responder",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Cria o estilo MessagingStyle
        val user = Person.Builder()
            .setName("Eu")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .setConversationTitle(conversaInfo.nomeConversa)

        // Adiciona as últimas 10 mensagens ao estilo
        mensagens.takeLast(10).forEach { msg ->
            val sender = getPerson(msg.remetenteId, msg.titulo)
            messagingStyle.addMessage(
                msg.mensagem,
                msg.timestamp,
                sender
            )
        }

        // Constrói a notificação
        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_MENSAGENS)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(quantidadeMensagens)
            .setGroup("group_mensagens")
            .setWhen(ultimaMensagem.timestamp)
            .setShowWhen(true)
            .setOnlyAlertOnce(mensagens.size > 1) // Só alerta na primeira mensagem
            .build()

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "✅ Notificação atualizada - Conversa: ${conversaInfo.nomeConversa}, Remetente: ${ultimaMensagem.titulo}, Total mensagens: $quantidadeMensagens")
    }

    /**
     * Atualiza a notificação após enviar uma resposta
     */
    fun atualizarNotificacaoAposResposta(context: Context, conversaId: Int, mensagemEnviada: String) {
        val conversaInfo = conversasInfo[conversaId] ?: return
        
        Log.d(TAG, "📤 Atualizando notificação após resposta - Conversa: $conversaId")
        
        // Limpa as mensagens após responder e remove a notificação
        limparMensagensConversa(context, conversaId)
    }

    /**
     * Mostra erro na notificação quando falha ao enviar resposta
     */
    fun mostrarErroResposta(context: Context, conversaId: Int, erro: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationConstants.getNotificationIdMensagem(conversaId)
        
        val conversaInfo = conversasInfo[conversaId]
        val nomeConversa = conversaInfo?.nomeConversa ?: "Conversa"

        val notification = NotificationCompat.Builder(context, NotificationConstants.CHANNEL_ID_MENSAGENS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(nomeConversa)
            .setContentText("Falha ao enviar: $erro")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Limpa as mensagens de uma conversa específica (quando o usuário abre a conversa)
     */
    fun limparMensagensConversa(context: Context, conversaId: Int) {
        Log.d(TAG, "🧹 Limpando mensagens da conversa $conversaId")

        conversasInfo.remove(conversaId)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationConstants.getNotificationIdMensagem(conversaId)
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
            val notificationId = NotificationConstants.getNotificationIdMensagem(conversaId)
            notificationManager.cancel(notificationId)
        }

        conversasInfo.clear()
        personCache.clear()
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

    /**
     * Retorna informações de uma conversa
     */
    fun getConversaInfo(conversaId: Int): ConversaInfo? {
        return conversasInfo[conversaId]
    }
}
