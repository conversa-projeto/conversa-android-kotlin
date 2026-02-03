package com.conversa.conversa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ConteudoRequest
import com.conversa.conversa.data.model.EnviarMensagemRequest
import com.conversa.conversa.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver para processar ações das notificações de mensagens.
 * 
 * Suporta:
 * - ACTION_REPLY: Processa resposta direta enviada pelo usuário via notificação
 * - ACTION_MARK_READ: Marca mensagens como lidas (para implementação futura)
 */
class MensagemActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MensagemActionReceiver"

        const val ACTION_REPLY = "com.conversa.conversa.ACTION_REPLY_MESSAGE"
        const val ACTION_MARK_READ = "com.conversa.conversa.ACTION_MARK_READ"

        const val EXTRA_CONVERSA_ID = "conversa_id"
        const val EXTRA_DESTINATARIO_ID = "destinatario_id"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e(TAG, "Context ou Intent nulo")
            return
        }

        val conversaId = intent.getIntExtra(EXTRA_CONVERSA_ID, -1)
        if (conversaId == -1) {
            Log.e(TAG, "conversaId inválido")
            return
        }

        when (intent.action) {
            ACTION_REPLY -> processarResposta(context, intent, conversaId)
            ACTION_MARK_READ -> marcarComoLida(context, conversaId)
            else -> Log.w(TAG, "Action desconhecida: ${intent.action}")
        }
    }

    /**
     * Processa a resposta direta enviada pelo usuário
     */
    private fun processarResposta(context: Context, intent: Intent, conversaId: Int) {
        // Extrai o texto da resposta do RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val textoResposta = remoteInput?.getCharSequence(MensagemNotificationManager.KEY_TEXT_REPLY)?.toString()

        if (textoResposta.isNullOrBlank()) {
            Log.e(TAG, "Texto da resposta vazio")
            return
        }

        Log.d(TAG, "📤 Enviando resposta para conversa $conversaId: $textoResposta")

        scope.launch {
            try {
                val userPrefs = UserPreferences(context)
                val token = userPrefs.authToken.first()
                val userId = userPrefs.userId.first() ?: -1

                if (token == null) {
                    Log.e(TAG, "Token não disponível")
                    MensagemNotificationManager.mostrarErroResposta(
                        context, 
                        conversaId, 
                        "Não autenticado"
                    )
                    return@launch
                }

                // Envia a mensagem via API
                val conteudoTexto = ConteudoRequest(
                    tipo = 1, // Tipo texto
                    ordem = 1,
                    conteudo = textoResposta
                )
                
                val request = EnviarMensagemRequest(
                    conversaId = conversaId,
                    conteudos = listOf(conteudoTexto)
                )

                val response = RetrofitClient.api.enviarMensagem(
                    "Bearer $token",
                    request
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Mensagem enviada com sucesso")
                    
                    // Atualiza a notificação para mostrar que foi enviada
                    MensagemNotificationManager.atualizarNotificacaoAposResposta(
                        context,
                        conversaId,
                        textoResposta
                    )
                } else {
                    Log.e(TAG, "❌ Erro ao enviar mensagem: ${response.code()}")
                    MensagemNotificationManager.mostrarErroResposta(
                        context,
                        conversaId,
                        "Erro ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao enviar mensagem", e)
                MensagemNotificationManager.mostrarErroResposta(
                    context,
                    conversaId,
                    e.message ?: "Erro desconhecido"
                )
            }
        }
    }

    /**
     * Marca as mensagens de uma conversa como lidas
     */
    private fun marcarComoLida(context: Context, conversaId: Int) {
        Log.d(TAG, "📖 Marcando conversa $conversaId como lida")
        
        // Limpa as notificações da conversa
        MensagemNotificationManager.limparMensagensConversa(context, conversaId)
    }
}
