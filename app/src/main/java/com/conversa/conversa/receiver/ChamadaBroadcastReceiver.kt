package com.conversa.conversa.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.notification.ChamadaNotificationManager
import com.conversa.conversa.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChamadaBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ChamadaBroadcastReceiver"
        const val ACTION_CHAMADA_RECEBIDA = "com.conversa.CHAMADA_RECEBIDA"
        const val EXTRA_CHAMADA_ID = "chamadaId"
        const val EXTRA_USUARIO_ID = "usuarioId"
        const val EXTRA_USUARIO_NOME = "usuarioNome"
        const val EXTRA_TIPO_CHAMADA = "tipoChamada"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast recebido: ${intent.action}")

        if (intent.action != ACTION_CHAMADA_RECEBIDA) {
            Log.w(TAG, "Action inválida: ${intent.action}")
            return
        }

        val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, -1)
        val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, -1)
        val usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME)
        val tipoChamada = intent.getIntExtra(EXTRA_TIPO_CHAMADA, 1)

        if (chamadaId == -1 || usuarioId == -1 || usuarioNome.isNullOrEmpty()) {
            Log.e(TAG, "Extras obrigatórios ausentes ou inválidos - chamadaId: $chamadaId, usuarioId: $usuarioId, usuarioNome: $usuarioNome")
            return
        }

        Log.d(TAG, "Processando chamada - ID: $chamadaId, Usuario: $usuarioNome ($usuarioId), Tipo: $tipoChamada")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userPreferences = UserPreferences(context)
                val token = userPreferences.getToken()

                if (token.isNullOrEmpty()) {
                    Log.e(TAG, "Token não disponível")
                    pendingResult.finish()
                    return@launch
                }

                Log.d(TAG, "Token obtido com sucesso")

                val api = RetrofitClient.api
                val notificationManager = ChamadaNotificationManager(context, api, token)

                notificationManager.onChamadaRecebida(
                    chamadaId = chamadaId,
                    usuarioId = usuarioId,
                    usuarioNome = usuarioNome,
                    tipoChamada = tipoChamada
                )

                Log.d(TAG, "Notificação de chamada processada com sucesso")

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar broadcast de chamada", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
