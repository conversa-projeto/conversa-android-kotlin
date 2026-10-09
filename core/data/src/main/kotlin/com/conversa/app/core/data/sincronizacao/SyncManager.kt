package com.conversa.app.core.data.sincronizacao

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.atividades.AtividadesRepositorio
import com.conversa.app.core.data.enquetes.EnquetesRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.dao.SyncEstadoDao
import com.conversa.app.core.database.entidades.SyncEstadoEntidade
import com.conversa.app.core.model.ChamadaPendente
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.lerInstant
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Mantém o cache local em dia com o servidor.
 *
 * O servidor não guarda fila de eventos: o que acontece com o socket fechado
 * se perde (contrato §6.7). Por isso, a cada (re)conexão e ao receber push,
 * [ressincronizar] busca tudo de novo de forma incremental:
 * 1. `GET /mensagens/novas?desde=<cursor>` e as mensagens que faltam por conversa;
 * 2. `GET /conversas`;
 * 3. `GET /contatos/online` (no [PresencaRepositorio]);
 * 4. `GET /atividades/novas`;
 * 5. `GET /chamadas/pendentes`.
 *
 * O evento WS 2 não traz `conversa_id` e pode chegar duplicado: vira só um
 * gatilho de sincronização incremental, com debounce.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    private val api: ConversaApi,
    private val tempoReal: RealtimeClient,
    private val conversaDao: ConversaDao,
    private val mensagemDao: MensagemDao,
    private val syncEstadoDao: SyncEstadoDao,
    private val presenca: PresencaRepositorio,
    private val sessao: SessaoRepositorio,
    private val enquetes: EnquetesRepositorio,
    private val atividades: AtividadesRepositorio,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val trava = Mutex()

    /** Badge da aba Atividades (8.1): o contador fica no repositório, que também sabe se a aba está aberta. */
    val atividadesNovas: StateFlow<Int> = atividades.novas

    private val _chamadasPendentes = MutableSharedFlow<List<ChamadaPendente>>(replay = 1)

    /** Chamadas tocando para o usuário; o gerenciador de chamadas (etapa 6) consome. */
    val chamadasPendentes: SharedFlow<List<ChamadaPendente>> = _chamadasPendentes.asSharedFlow()

    private val gatilhoMensagens = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _novasDeOutros = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 8)

    /**
     * Conversas que acabaram de receber mensagens de outras pessoas ainda não lidas
     * (NOT-01/NOT-02). Avisa depois de atualizar a lista, para o contador já estar certo.
     */
    val novasDeOutros: SharedFlow<Set<Long>> = _novasDeOutros.asSharedFlow()
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true

        tempoReal.estado
            .filter { it == EstadoConexao.CONECTADO }
            .onEach { ressincronizar() }
            .launchIn(escopo)

        gatilhoMensagens
            .debounce(DEBOUNCE_MS)
            .onEach { sincronizarMensagensNovas() }
            .launchIn(escopo)

        tempoReal.eventos.onEach(::tratarEvento).launchIn(escopo)
        tempoReal.eventos.filterIsInstance<EventoSocket.NovaMensagem>().onEach { gatilhoMensagens.tryEmit(Unit) }.launchIn(escopo)
    }

    /** Sincronização completa (reconexão, volta ao primeiro plano, push). */
    suspend fun ressincronizar() {
        trava.withLock {
            coroutineScope {
                listOf(
                    async { sincronizarMensagensNovasTravado() },
                    async { sincronizarConversas() },
                    async { presenca.recarregarOnline() },
                    async { atividades.atualizarNovas() },
                    async { sincronizarChamadasPendentes() },
                ).awaitAll()
            }
        }
    }

    suspend fun sincronizarConversas() {
        chamarApi { api.conversas() }
            .onSuccess { lista -> conversaDao.substituirTodas(lista.map { it.paraEntidade() }) }
            .onFailure { Timber.d("Falha ao sincronizar conversas: %s", it.javaClass.simpleName) }
    }

    private suspend fun sincronizarMensagensNovas() = trava.withLock { sincronizarMensagensNovasTravado() }

    private suspend fun sincronizarMensagensNovasTravado() {
        val desde = syncEstadoDao.ler(CURSOR_MENSAGENS).orEmpty()
        val novas = chamarApi { api.mensagensNovas(desde) }.getOrElse {
            Timber.d("Falha em mensagens/novas: %s", it.javaClass.simpleName)
            return
        }
        if (novas.isEmpty()) return

        val comNovasDeOutros = mutableSetOf<Long>()
        for (nova in novas) {
            if (buscarSeguintes(nova.conversaId)) comNovasDeOutros += nova.conversaId
            // Mensagem nova na conversa: quem estava digitando terminou (como no web).
            presenca.limparDigitando(nova.conversaId)
        }
        // Cursor = maior `ate`, guardado como veio (contrato §10.10).
        val maior = novas.maxByOrNull { lerInstant(it.ate) ?: java.time.Instant.EPOCH }?.ate
        if (maior != null) syncEstadoDao.gravar(SyncEstadoEntidade(CURSOR_MENSAGENS, maior))
        sincronizarConversas()
        if (comNovasDeOutros.isNotEmpty()) _novasDeOutros.tryEmit(comNovasDeOutros)
    }

    /**
     * Mensagens depois da última salva da conversa (ou as [LOTE_INICIAL] últimas, se não há
     * nenhuma). `true` se chegou alguma de outra pessoa ainda não lida.
     */
    private suspend fun buscarSeguintes(conversaId: Long): Boolean {
        val ultima = mensagemDao.ultimaSalva(conversaId)
        val eu = sessao.sessao.value?.usuarioId
        return chamarApi {
            if (ultima != null) {
                api.mensagens(conversaId, mensagemReferencia = ultima, mensagensSeguintes = LOTE)
            } else {
                api.mensagens(conversaId, mensagensPrevias = LOTE_INICIAL)
            }
        }.map { mensagens ->
            mensagemDao.salvarCompletas(mensagens.map { it.paraEntidade() })
            mensagens.any { it.remetenteId != eu && !it.visualizada && (ultima == null || it.id > ultima) }
        }.getOrDefault(false)
    }

    /**
     * Atualização periódica enquanto o WebSocket está fora (o web faz a cada 8 s):
     * mensagens novas e chamadas pendentes. Quem chama é a [ConexaoTempoReal].
     */
    suspend fun atualizacaoPeriodica() {
        sincronizarMensagensNovas()
        sincronizarChamadasPendentes()
    }

    /** Fim da sessão: zera o que fica só em memória (o banco é limpo à parte). */
    fun limpar() {
        atividades.limpar()
        _chamadasPendentes.resetReplayCache()
    }

    /**
     * WS 7 (reação de alguém): o evento não traz o nome de quem reagiu, então a mensagem
     * é relida do servidor — só se ela está no aparelho.
     */
    private suspend fun atualizarReacoes(evento: EventoSocket.Reacao) {
        if (mensagemDao.buscar(evento.mensagemId) == null) return
        chamarApi { api.mensagens(evento.conversaId, mensagemReferencia = evento.mensagemId) }
            .onSuccess { lista -> mensagemDao.salvarCompletas(lista.filter { it.id == evento.mensagemId }.map { it.paraEntidade() }) }
    }

    private suspend fun sincronizarChamadasPendentes() {
        chamarApi { api.chamadasPendentes() }.onSuccess { lista -> _chamadasPendentes.emit(lista.map { it.paraModelo() }) }
    }

    private fun tratarEvento(evento: EventoSocket) {
        when (evento) {
            is EventoSocket.NovaAtividade -> escopo.launch { atividades.aoReceberAviso() }
            is EventoSocket.ConversaAtualizada -> escopo.launch { sincronizarConversas() }
            is EventoSocket.StatusMensagens -> escopo.launch { atualizarStatus(evento) }
            is EventoSocket.Reacao -> escopo.launch { atualizarReacoes(evento) }
            // 62: relê a enquete só se alguma bolha já a carregou (7.12).
            is EventoSocket.EnqueteAtualizada -> escopo.launch { enquetes.aoAtualizar(evento.enqueteId) }
            else -> Unit
        }
    }

    private suspend fun atualizarStatus(evento: EventoSocket.StatusMensagens) {
        if (evento.mensagens.isEmpty()) return
        // Id que ainda não está no cache: mensagem nova que chegou sem WS 2 — por exemplo o
        // resumo de chamada (tipo 6), que só gera WS 3 (contrato §9.8). Busca o que falta.
        val faltando = evento.mensagens.filter { mensagemDao.buscar(it) == null }
        if (faltando.isNotEmpty()) buscarSeguintes(evento.conversaId)
        chamarApi { api.statusMensagens(evento.conversaId, evento.mensagens.joinToString(",")) }
            .onSuccess { lista ->
                val eu = sessao.sessao.value?.usuarioId ?: return@onSuccess
                lista.forEach {
                    // O status desta rota é o agregado de todos: só vale nas minhas mensagens (contrato §10.5).
                    mensagemDao.atualizarStatusDaMinha(it.mensagemId, eu, it.recebida, it.visualizada, it.reproduzida)
                    mensagemDao.atualizarOculta(it.mensagemId, it.excluidaEm?.toEpochMilli())
                }
            }
        sincronizarConversas()
    }

    companion object {
        const val CURSOR_MENSAGENS = "mensagens_novas_ate"
        private const val DEBOUNCE_MS = 300L
        private const val LOTE = 100
        private const val LOTE_INICIAL = 80
    }
}
