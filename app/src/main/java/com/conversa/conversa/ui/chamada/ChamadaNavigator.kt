package com.conversa.conversa.ui.chamada

import android.content.Context
import android.content.Intent
import com.conversa.conversa.ui.chamada.ChamadaActivity

/**
 * Helper object para navegar para a tela de chamada de qualquer lugar do app.
 * Centraliza a lógica de criação de Intents para telas de chamada.
 */
object ChamadaNavigator {

    // Extras para Intent
    const val EXTRA_CHAMADA_ID = "chamada_id"
    const val EXTRA_IS_INCOMING = "is_incoming"
    const val EXTRA_AUTO_ANSWER = "auto_answer"
    const val EXTRA_USUARIO_ID = "usuario_id"
    const val EXTRA_NOME_EXIBICAO = "nome_exibicao"

    /**
     * Abre tela de chamada recebida
     */
    fun abrirChamadaRecebida(
        context: Context,
        chamadaId: Int,
        usuarioId: Int,
        nomeExibicao: String,
        autoAnswer: Boolean = false
    ) {
        val intent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_CHAMADA_ID, chamadaId)
            putExtra(EXTRA_IS_INCOMING, true)
            putExtra(EXTRA_AUTO_ANSWER, autoAnswer)
            putExtra(EXTRA_USUARIO_ID, usuarioId)
            putExtra(EXTRA_NOME_EXIBICAO, nomeExibicao)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Abre tela para iniciar uma chamada sainte
     */
    fun iniciarChamada(
        context: Context,
        usuarioId: Int,
        nomeExibicao: String
    ) {
        val intent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_IS_INCOMING, false)
            putExtra(EXTRA_USUARIO_ID, usuarioId)
            putExtra(EXTRA_NOME_EXIBICAO, nomeExibicao)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Abre tela para iniciar chamada em grupo
     */
    fun iniciarChamadaGrupo(
        context: Context,
        usuariosIds: List<Int>,
        nomeGrupo: String
    ) {
        val intent = Intent(context, ChamadaActivity::class.java).apply {
            putExtra(EXTRA_IS_INCOMING, false)
            putIntegerArrayListExtra("usuarios_ids", ArrayList(usuariosIds))
            putExtra(EXTRA_NOME_EXIBICAO, nomeGrupo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Volta para tela de chamada ativa (quando já existe uma chamada)
     * Usado pelo banner de chamada em outras telas
     */
    fun voltarParaChamadaAtiva(context: Context) {
        val intent = Intent(context, ChamadaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        context.startActivity(intent)
    }
}
