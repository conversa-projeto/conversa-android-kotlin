    private fun mostrarNotificacaoChamada(
        chamadaId: Int, 
        usuarioId: Int, 
        usuarioNome: String,
        chamada: ChamadaResponse
    ) {
        val isAppInForeground = AppLifecycleManager.isAppInForeground
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isTelaBloqueda = keyguardManager.isKeyguardLocked
        
        Log.d(TAG, "📱 Estado - AppForeground: $isAppInForeground | TelaBloqueda: $isTelaBloqueda")
        
        // Se app está em foreground, envia broadcast para activity exibir dialog
        if (isAppInForeground) {
            Log.d(TAG, "✅ App ABERTO - Enviando broadcast para Activity atual")
            ChamadaBroadcast.notifyChamadaRecebida(chamadaId, usuarioId, usuarioNome)
        }
        
        // Configuração dos botões de ação da notificação
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // Botão Atender
        val answerIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_ANSWER
            putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActionReceiver.EXTRA_USUARIO_ID, usuarioId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(this, chamadaId, answerIntent, flags)
        
        // Botão Recusar
        val declineIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
            action = ChamadaActionReceiver.ACTION_DECLINE
            putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(this, chamadaId + 100000, declineIntent, flags)
        
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val textoNotificacao = if (chamada.tipo == 2) {
            getString(R.string.chamada_grupo)
        } else {
            "Chamada de voz"
        }
        
        // CENÁRIO 1: TELA DESBLOQUEADA - Notificação sem contentIntent (não abre activity ao clicar)
        if (!isTelaBloqueda) {
            Log.d(TAG, "🔓 TELA DESBLOQUEADA - Notificação SEM contentIntent")
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
                .setContentTitle(usuarioNome)
                .setContentText(textoNotificacao)
                .setSmallIcon(R.drawable.ic_notification)
                // SEM setContentIntent - não abre nada ao clicar na notificação
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setSound(soundUri)
                .setOngoing(false)
                .setTimeoutAfter(60000)
                .addAction(R.drawable.ic_call_end, getString(R.string.recusar), declinePendingIntent)
                .addAction(R.drawable.ic_call, getString(R.string.atender), answerPendingIntent)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_CHAMADA, notification)
            return
        }
        
        // CENÁRIO 2: TELA BLOQUEADA - Abre activity fullscreen + notificação
        Log.d(TAG, "🔒 TELA BLOQUEADA - Abrindo ChamadaActivity em fullscreen")
        
        val activityIntent = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            chamadaId,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notificationFullscreen = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
            .setContentTitle(usuarioNome)
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
            .addAction(R.drawable.ic_call_end, getString(R.string.recusar), declinePendingIntent)
            .addAction(R.drawable.ic_call, getString(R.string.atender), answerPendingIntent)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_CHAMADA, notificationFullscreen)
        
        // Abre activity diretamente (garante abertura mesmo se fullScreenIntent falhar)
        try {
            startActivity(activityIntent)
            Log.d(TAG, "✅ ChamadaActivity aberta com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao abrir ChamadaActivity", e)
        }
    }
