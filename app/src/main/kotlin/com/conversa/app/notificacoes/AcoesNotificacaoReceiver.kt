package com.conversa.app.notificacoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Ações da notificação de mensagem (TODO 5.5, FC-604, AND-08):
 * - "Responder": a mensagem entra na fila de envio (WorkManager, nunca se perde) e a
 *   conversa é marcada como lida;
 * - "Marcar como lida": `POST /mensagem/visualizar` de cada não lida.
 * Usa `goAsync()` (#42 do legado): o trabalho termina antes de o Android encerrar o processo.
 * A notificação some sozinha quando o contador da conversa zera ([NotificadorMensagens]).
 */
@AndroidEntryPoint
class AcoesNotificacaoReceiver : BroadcastReceiver() {
    @Inject lateinit var envio: EnvioMensagens

    @Inject lateinit var mensagens: MensagensRepositorio

    @Inject lateinit var sessao: SessaoRepositorio

    @Inject lateinit var notificador: NotificadorMensagens

    @Inject @EscopoAplicacao
    lateinit var escopo: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val conversaId = intent.getLongExtra(EXTRA_CONVERSA, 0)
        if (conversaId <= 0) return
        val pendente = goAsync()
        escopo.launch {
            try {
                val eu = sessao.sessao.value?.usuarioId ?: return@launch
                when (intent.action) {
                    ACAO_RESPONDER -> {
                        val texto = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CHAVE_RESPOSTA)?.toString()?.trim()
                        if (!texto.isNullOrEmpty()) envio.enviarTexto(conversaId, texto)
                        mensagens.marcarConversaLida(conversaId, eu)
                        // Atualizar (não só cancelar): o Android 15+ segura a notificação respondida até uma atualização.
                        if (texto.isNullOrEmpty()) notificador.cancelar(conversaId) else notificador.respondida(conversaId, texto)
                    }
                    ACAO_MARCAR_LIDA -> {
                        mensagens.marcarConversaLida(conversaId, eu)
                        notificador.cancelar(conversaId)
                    }
                }
            } catch (e: Exception) {
                Timber.w("Ação da notificação falhou: %s", e.javaClass.simpleName)
            } finally {
                pendente.finish()
            }
        }
    }

    companion object {
        const val ACAO_RESPONDER = "com.conversa.app.notificacao.RESPONDER"
        const val ACAO_MARCAR_LIDA = "com.conversa.app.notificacao.MARCAR_LIDA"
        const val CHAVE_RESPOSTA = "resposta"
        private const val EXTRA_CONVERSA = "conversa"

        fun intencao(contexto: Context, acao: String, conversaId: Long) =
            Intent(contexto, AcoesNotificacaoReceiver::class.java).setAction(acao).putExtra(EXTRA_CONVERSA, conversaId)
    }
}
