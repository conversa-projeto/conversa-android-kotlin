package com.conversa.app.core.data.notificacoes

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.conversa.app.core.data.R

/**
 * Canais de notificação (TODO 5.3, FC-606), criados uma vez no `Application`. O sufixo
 * `_v1` permite trocar som/importância depois (canal criado não muda; cria-se o `_v2`).
 */
object CanaisNotificacao {
    const val MENSAGENS = "mensagens_v1"
    const val CHAMADAS_RECEBIDAS = "chamadas_recebidas_v1"
    const val CHAMADA_ATIVA = "chamada_ativa_v1"
    const val SISTEMA = "sistema_v1"

    fun criar(contexto: Context) {
        val canais = listOf(
            canal(
                contexto,
                MENSAGENS,
                NotificationManagerCompat.IMPORTANCE_HIGH,
                R.string.canal_mensagens,
                R.string.canal_mensagens_descricao,
            ),
            canal(
                contexto,
                CHAMADAS_RECEBIDAS,
                NotificationManagerCompat.IMPORTANCE_HIGH,
                R.string.canal_chamadas_recebidas,
                R.string.canal_chamadas_recebidas_descricao,
            ),
            canal(
                contexto,
                CHAMADA_ATIVA,
                NotificationManagerCompat.IMPORTANCE_LOW,
                R.string.canal_chamada_ativa,
                R.string.canal_chamada_ativa_descricao,
            ),
            canal(contexto, SISTEMA, NotificationManagerCompat.IMPORTANCE_LOW, R.string.canal_sistema, R.string.canal_sistema_descricao),
        )
        NotificationManagerCompat.from(contexto).apply {
            createNotificationChannelsCompat(canais)
            // O canal só de downloads da 4.7 virou o "Sistema".
            deleteNotificationChannel("downloads")
        }
    }

    private fun canal(contexto: Context, id: String, importancia: Int, nome: Int, descricao: Int) =
        NotificationChannelCompat.Builder(id, importancia)
            .setName(contexto.getString(nome))
            .setDescription(contexto.getString(descricao))
            .build()
}
