package com.conversa.conversa.service

/**
 * Constantes centralizadas para notificações do app.
 * Mantém todos os IDs de notificação e canais em um único lugar.
 */
object NotificationConstants {

    // ==================== CANAIS ====================

    /** Canal para notificações de chamadas de voz */
    const val CHANNEL_ID_CHAMADAS = "conversa_chamada_channel"

    // ==================== IDs DE NOTIFICAÇÃO - CHAMADAS (2000-2099) ====================

    /** Notificação do foreground service de chamadas */
    const val NOTIFICATION_ID_CHAMADA_FOREGROUND = 2000

    /** Notificação de chamada recebida (heads-up) */
    const val NOTIFICATION_ID_CHAMADA_INCOMING = 2001

    /** Notificação de chamada em andamento */
    const val NOTIFICATION_ID_CHAMADA_ONGOING = 2002

    /** Notificação de chamada perdida */
    const val NOTIFICATION_ID_CHAMADA_MISSED = 2003
}
