package com.conversa.app.core.data.mensagens

import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.alternarReacao
import com.conversa.app.core.model.emojiAceito
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.ReacaoRequisicao
import com.conversa.app.core.network.http.chamarApi
import java.time.Clock
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
    private val relogio: Clock,
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

    /**
     * Ir para a mensagem (MSG-06, TODO 7.5): garante que [mensagemId] está no aparelho sem
     * deixar buraco na conversa. Volta de [PAGINA_SALTO] em [PAGINA_SALTO] a partir da mais
     * antiga salva até ela aparecer, no máximo [MAXIMO_PAGINAS_SALTO] vezes. `true` = está.
     */
    suspend fun trazerAte(conversaId: Long, mensagemId: Long): Result<Boolean> {
        repeat(MAXIMO_PAGINAS_SALTO + 1) { tentativa ->
            if (mensagemDao.buscar(mensagemId)?.mensagem?.conversaId == conversaId) return Result.success(true)
            if (tentativa == MAXIMO_PAGINAS_SALTO) return Result.success(false)
            val primeira = mensagemDao.primeiraSalva(conversaId)
            val novas = chamarApi {
                if (primeira == null) {
                    api.mensagens(conversaId, mensagensPrevias = PAGINA_SALTO)
                } else {
                    api.mensagens(conversaId, mensagemReferencia = primeira, mensagensPrevias = PAGINA_SALTO)
                }
            }.map { lista -> salvar(lista.filter { it.id != primeira }) }
                .getOrElse { return Result.failure(it) }
            if (novas == 0) return Result.success(mensagemDao.buscar(mensagemId)?.mensagem?.conversaId == conversaId)
        }
        return Result.success(false)
    }

    /** Relê uma mensagem do servidor (reações com nomes, marca de oculta). */
    suspend fun recarregar(conversaId: Long, mensagemId: Long): Result<Unit> =
        chamarApi {
            api.mensagens(conversaId, mensagemReferencia = mensagemId)
        }.map { lista -> salvar(lista.filter { it.id == mensagemId }) }

    /**
     * Reagir (ENV-17, FC-501): mostra na hora (otimista), manda o `PUT /mensagem/reacao`
     * (que alterna) e relê a mensagem para ficar igual ao servidor — inclusive se falhar.
     */
    suspend fun reagir(conversaId: Long, mensagemId: Long, emoji: String, eu: Long, meuNome: String): Result<Unit> {
        if (!emojiAceito(emoji)) return Result.failure(IllegalArgumentException("Emoji inválido"))
        val atuais = mensagemDao.buscar(mensagemId)?.paraModelo()?.reacoes ?: emptyList()
        val novas = alternarReacao(atuais, emoji, eu, meuNome, relogio.instant())
        mensagemDao.trocarReacoes(mensagemId, novas.mapIndexed { ordem, reacao -> reacao.paraEntidade(mensagemId, ordem) })
        val resultado = chamarApi { api.reagir(ReacaoRequisicao(mensagemId, emoji)) }.map { }
        recarregar(conversaId, mensagemId)
        return resultado
    }

    /**
     * Ocultar (ENV-18, FC-503): `DELETE /mensagem`. Com `excluida_em` a mensagem fica marcada
     * como oculta; sem, era agendada e foi apagada de vez.
     */
    suspend fun ocultar(mensagemId: Long): Result<Unit> = chamarApi { api.ocultarMensagem(mensagemId) }.map { resposta ->
        val excluidaEm = resposta.excluidaEm
        if (excluidaEm != null) mensagemDao.atualizarOculta(mensagemId, excluidaEm.toEpochMilli()) else mensagemDao.remover(mensagemId)
    }

    private suspend fun salvar(lista: List<com.conversa.app.core.network.dto.MensagemDto>): Int {
        mensagemDao.salvarCompletas(lista.map { it.paraEntidade() })
        return lista.size
    }

    /** `POST /conversa/digitando` (ENV-15). Melhor esforço: falha não importa. */
    suspend fun avisarDigitando(conversaId: Long) {
        chamarApi { api.avisarDigitando(IdDto(conversaId)) }
    }

    /** Gravando áudio (ANX-11): a tela chama a cada 2,5 s enquanto a gravação está aberta (WS 5 aos outros). */
    suspend fun avisarGravando(conversaId: Long) {
        chamarApi { api.avisarGravando(IdDto(conversaId)) }
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

    /**
     * "Marcar como lida" da notificação (FC-604): todas as de outras pessoas ainda não lidas.
     * Diferente de [marcarLida], espera cada `POST`: quem chama é uma ação de notificação,
     * que tem pouco tempo de vida.
     */
    suspend fun marcarConversaLida(conversaId: Long, eu: Long) {
        for (completa in mensagemDao.naoLidasDeOutros(conversaId, eu, Int.MAX_VALUE)) {
            val id = completa.mensagem.id
            if (!synchronized(jaPedidas) { jaPedidas.add(id) }) continue
            mensagemDao.marcarLidaPorMim(id)
            conversaDao.descontarNaoLida(conversaId)
            chamarApi { api.visualizar(MarcarStatusRequisicao(conversaId, id)) }
                .onFailure { synchronized(jaPedidas) { jaPedidas.remove(id) } }
        }
    }

    // --- Reproduzida (ANX-10) ---

    private val jaReproduzidas = mutableSetOf<Long>()

    /**
     * Primeiro play de um áudio de outra pessoa: o botão deixa de ser verde na hora
     * (Room) e `POST /mensagem/reproduzir` vai uma vez. Se falhar, pode pedir de novo.
     */
    suspend fun marcarReproduzida(conversaId: Long, mensagemId: Long) {
        if (mensagemId <= 0) return
        if (!synchronized(jaReproduzidas) { jaReproduzidas.add(mensagemId) }) return
        mensagemDao.marcarReproduzidaPorMim(mensagemId)
        chamarApi { api.reproduzir(MarcarStatusRequisicao(conversaId, mensagemId)) }
            .onFailure {
                synchronized(jaReproduzidas) { jaReproduzidas.remove(mensagemId) }
                Timber.d("Falha ao marcar como reproduzida: %s", it.javaClass.simpleName)
            }
    }

    /** Fim da sessão. */
    fun limpar() {
        synchronized(jaPedidas) { jaPedidas.clear() }
        synchronized(jaReproduzidas) { jaReproduzidas.clear() }
    }

    companion object {
        /** Igual ao web: 80 ao abrir, 60 por página. */
        const val RECENTES = 80
        const val PAGINA = 60

        /** O servidor devolve no máximo 100 por chamada: 99 antes + a de referência. */
        const val PAGINA_SALTO = 99

        /** Até ~2.000 mensagens para trás; além disso, "não foi possível localizar". */
        const val MAXIMO_PAGINAS_SALTO = 20
    }
}
