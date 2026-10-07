package com.conversa.app.core.data.mensagens

import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mensagens de uma conversa (MSG-01, MSG-04). O Room é a fonte da tela; a rede só
 * acrescenta. `GET /mensagens` devolve no máximo 100, sempre em ordem crescente, e
 * **inclui** a mensagem de referência (contrato §10.4).
 */
@Singleton
class MensagensRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    private val conversaDao: ConversaDao,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    fun observar(conversaId: Long): Flow<List<Mensagem>> =
        mensagemDao.observarDaConversa(conversaId).map { lista -> lista.map { it.paraModelo() } }

    /** Ao abrir a conversa: as [RECENTES] mais recentes (o que já está no Room aparece antes). */
    suspend fun carregarRecentes(conversaId: Long): Result<Int> =
        chamarApi { api.mensagens(conversaId, mensagensPrevias = RECENTES) }.map { salvar(it) }

    /**
     * Rolou para cima: [PAGINA] antes da mais antiga salva. Devolve quantas mensagens
     * **novas** chegaram (a de referência vem repetida e não conta); 0 = chegou ao começo.
     */
    suspend fun carregarAnteriores(conversaId: Long): Result<Int> {
        val primeira = mensagemDao.primeiraSalva(conversaId) ?: return carregarRecentes(conversaId)
        return chamarApi { api.mensagens(conversaId, mensagemReferencia = primeira, mensagensPrevias = PAGINA) }
            .map { lista -> salvar(lista.filter { it.id != primeira }) }
    }

    /** Rolou para baixo depois de um salto: [PAGINA] depois da mais nova salva. 0 = chegou ao fim. */
    suspend fun carregarSeguintes(conversaId: Long): Result<Int> {
        val ultima = mensagemDao.ultimaSalva(conversaId) ?: return carregarRecentes(conversaId)
        return chamarApi { api.mensagens(conversaId, mensagemReferencia = ultima, mensagensSeguintes = PAGINA) }
            .map { lista -> salvar(lista.filter { it.id != ultima }) }
    }

    private suspend fun salvar(lista: List<com.conversa.app.core.network.dto.MensagemDto>): Int {
        mensagemDao.salvarCompletas(lista.map { it.paraEntidade() })
        return lista.size
    }

    // --- Lida (MSG-04) ---

    private val filaLida = Channel<Pair<Long, Long>>(Channel.UNLIMITED)
    private val jaPedidas = mutableSetOf<Long>()
    private var filaIniciada = false

    /**
     * Marca como lida uma mensagem de outra pessoa que apareceu na tela. Otimista:
     * atualiza o Room e o contador da conversa na hora; a fila envia
     * `POST /mensagem/visualizar` uma por vez (a rota marca uma só; pendência S9).
     */
    suspend fun marcarLida(conversaId: Long, mensagemId: Long) {
        if (mensagemId <= 0) return
        val nova = synchronized(jaPedidas) { jaPedidas.add(mensagemId) }
        if (!nova) return
        iniciarFila()
        mensagemDao.marcarLidaPorMim(mensagemId)
        conversaDao.descontarNaoLida(conversaId)
        filaLida.send(conversaId to mensagemId)
    }

    private fun iniciarFila() {
        synchronized(jaPedidas) {
            if (filaIniciada) return
            filaIniciada = true
        }
        escopo.launch {
            for ((conversa, mensagem) in filaLida) {
                chamarApi { api.visualizar(MarcarStatusRequisicao(conversa, mensagem)) }
                    .onFailure {
                        // Deixa pedir de novo na próxima vez que aparecer na tela.
                        synchronized(jaPedidas) { jaPedidas.remove(mensagem) }
                        Timber.d("Falha ao marcar como lida: %s", it.javaClass.simpleName)
                    }
            }
        }
    }

    /** Fim da sessão. */
    fun limpar() {
        synchronized(jaPedidas) { jaPedidas.clear() }
    }

    companion object {
        /** Igual ao web: 80 ao abrir, 60 por página. */
        const val RECENTES = 80
        const val PAGINA = 60
    }
}
