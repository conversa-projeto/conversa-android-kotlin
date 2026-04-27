package com.conversa.conversa.service

import android.util.Log
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.DispositivoRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receiver do Firebase Cloud Messaging.
 *
 * - onNewToken: chamado quando o Firebase emite um novo token para este dispositivo
 *   (primeira instalacao ou reinstalacao). Precisa atualizar o backend (tabela `dispositivo.token_fcm`).
 * - onMessageReceived: push chegou com app em foreground/background/fechado.
 *   Roteia por tipo (nova_mensagem, chamada_recebida) para notificacao local apropriada.
 *
 * Requer google-services.json configurado. Ver comentario em app/build.gradle.kts.
 */
class ConversaFcmService : FirebaseMessagingService() {

    companion object { private const val TAG = "ConversaFcmService" }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        Log.d(TAG, "Novo token FCM: ${token.take(20)}...")
        scope.launch {
            try {
                val prefs = UserPreferences(applicationContext)
                val repo = DispositivoRepository(applicationContext, RetrofitClient.api, prefs)
                repo.atualizarTokenFcm(token).onFailure {
                    Log.w(TAG, "Falha ao registrar token no backend: ${it.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sem token de auth, adiando registro FCM: ${e.message}")
                // Fica para proximo login — LoginActivity chama DispositivoRepository.registrarOuAtualizar()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Push recebido: ${message.data}")
        val tipo = message.data["tipo"] ?: return

        when (tipo) {
            "nova_mensagem" -> {
                val conversaId = message.data["conversa_id"]?.toIntOrNull() ?: return
                val titulo = message.data["titulo"] ?: message.data["remetente_nome"] ?: "Nova mensagem"
                val texto = message.data["mensagem"] ?: ""
                val remetenteId = message.data["remetente_id"]?.toIntOrNull() ?: 0
                notificarMensagem(conversaId, remetenteId, titulo, texto)
            }
            "chamada_recebida" -> {
                val chamadaId = message.data["chamada_id"]?.toIntOrNull() ?: return
                val usuarioId = message.data["usuario_id"]?.toIntOrNull() ?: 0
                val usuarioNome = message.data["usuario_nome"] ?: "Desconhecido"
                iniciarServicoChamadaEntrante(chamadaId, usuarioId, usuarioNome)
            }
            else -> Log.w(TAG, "Tipo de push desconhecido: $tipo")
        }
    }

    private fun notificarMensagem(conversaId: Int, remetenteId: Int, titulo: String, texto: String) {
        // Push de nova_mensagem eh redundante com as notificacoes ja tratadas via WebSocket +
        // MensagemNotificationManager em SocketService; aqui apenas registramos o push
        Log.d(TAG, "Push nova_mensagem conv=$conversaId de=$remetenteId (ignorado — ja tratado via WS)")
    }

    private fun iniciarServicoChamadaEntrante(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        try {
            val intent = android.content.Intent(applicationContext, ChamadaService::class.java).apply {
                action = "CHAMADA_PUSH_RECEBIDA"
                putExtra("chamada_id", chamadaId)
                putExtra("usuario_id", usuarioId)
                putExtra("usuario_nome", usuarioNome)
            }
            androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar ChamadaService via push", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
