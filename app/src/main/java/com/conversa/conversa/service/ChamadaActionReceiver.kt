package com.conversa.conversa.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ChamadaIdRequest
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.ui.chamada.ChamadaActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.conversa.conversa.service.NotificationConstants.NOTIFICATION_ID_CHAMADA_INCOMING

class ChamadaActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ChamadaActionReceiver"

        const val ACTION_ANSWER = "com.conversa.conversa.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE = "com.conversa.conversa.ACTION_DECLINE_CALL"

        // Novas actions para compatibilidade com SocketService
        private const val ACTION_ACEITAR_CHAMADA = "com.conversa.ACTION_ACEITAR_CHAMADA"
        private const val ACTION_RECUSAR_CHAMADA = "com.conversa.ACTION_RECUSAR_CHAMADA"

        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e(TAG, "Context ou Intent nulo")
            return
        }

        val chamadaId = intent.getIntExtra(ChamadaActivity.EXTRA_CHAMADA_ID, -1)
        if (chamadaId == -1) {
            Log.e(TAG, "chamadaId inválido")
            return
        }

        when (intent.action) {
            ACTION_ANSWER, ACTION_ACEITAR_CHAMADA -> {
                val usuarioId = intent.getIntExtra(ChamadaActivity.EXTRA_USUARIO_ID, -1)
                val usuarioNome = intent.getStringExtra(ChamadaActivity.EXTRA_USUARIO_NOME) ?: ""
                atenderChamada(context, chamadaId, usuarioId, usuarioNome)
            }
            ACTION_DECLINE, ACTION_RECUSAR_CHAMADA -> recusarChamada(context, chamadaId)
            else -> Log.w(TAG, "Action desconhecida: ${intent.action}")
        }
    }
    
    private fun atenderChamada(context: Context, chamadaId: Int, usuarioId: Int, usuarioNome: String = "") {
        Log.d(TAG, "Atendendo chamada $chamadaId")

        // Para ringtone e vibração
        ChamadaRingtoneManager.getInstance(context).parar()

        // Abre ChamadaActivity para atender
        val activityIntent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
            putExtra("auto_answer", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        context.startActivity(activityIntent)

        // Remove notificação após abrir a activity
        removerNotificacao(context)
    }

    private fun recusarChamada(context: Context, chamadaId: Int) {
        Log.d(TAG, "Recusando chamada $chamadaId")

        // Para ringtone e vibração
        ChamadaRingtoneManager.getInstance(context).parar()

        // Remove notificação ao recusar
        removerNotificacao(context)

        // Notifica ChamadaService para finalizar e parar o serviço
        val serviceIntent = Intent(context, ChamadaService::class.java).apply {
            action = ChamadaServiceActions.ACTION_CHAMADA_FINALIZADA
            putExtra(ChamadaService.EXTRA_CHAMADA_ID, chamadaId)
        }
        context.startService(serviceIntent)

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val userPrefs = UserPreferences(context)
                val token = userPrefs.authToken.first()

                if (token != null) {
                    val response = RetrofitClient.api.recusarChamada(
                        "Bearer $token",
                        ChamadaIdRequest(chamadaId)
                    )

                    if (response.isSuccessful) {
                        Log.d(TAG, "Chamada recusada com sucesso")
                    } else {
                        Log.e(TAG, "Erro ao recusar: ${response.code()}")
                    }
                } else {
                    Log.e(TAG, "Token não disponível")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao recusar chamada", e)
            }
        }
    }
    
    private fun removerNotificacao(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_CHAMADA_INCOMING)
    }
}
