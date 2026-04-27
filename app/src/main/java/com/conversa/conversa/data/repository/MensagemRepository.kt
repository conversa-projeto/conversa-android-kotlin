package com.conversa.conversa.data.repository

import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.ConteudoRequest
import com.conversa.conversa.data.model.DigitandoRequest
import com.conversa.conversa.data.model.EnviarMensagemComReferenciaRequest
import com.conversa.conversa.data.model.EnviarMensagemResponse
import com.conversa.conversa.data.model.MensagemNovaItem
import com.conversa.conversa.data.model.MensagemReferencia
import com.conversa.conversa.data.model.MensagemStatusResponse
import com.conversa.conversa.data.model.PesquisaResultado
import com.conversa.conversa.data.model.ReacaoRequest
import com.conversa.conversa.data.model.ReacaoResponse
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orquestra operacoes avancadas de mensagem:
 * - Deletar, reagir, responder, encaminhar
 * - Sync incremental (/mensagens/novas)
 * - Busca global
 * - Digitando / gravando (envio + recepcao)
 *
 * Estado observavel para a UI via Flows.
 */
class MensagemRepository(
    private val api: ConversaApi,
    private val socketManager: SocketManager,
    private val userPreferences: UserPreferences,
) {
    companion object {
        private const val TAG = "MensagemRepository"
        private const val DEBOUNCE_INDICADOR_MS = 2500L
        private const val EXPIRY_INDICADOR_MS = 4000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Estados para UI: quem esta digitando/gravando em cada conversa
    private val _digitando = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    val digitandoFlow: StateFlow<Map<Int, Set<Int>>> = _digitando.asStateFlow()

    private val _gravando = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    val gravandoFlow: StateFlow<Map<Int, Set<Int>>> = _gravando.asStateFlow()

    // Reacoes em tempo real
    data class EventoReacao(
        val conversaId: Int, val mensagemId: Int, val usuarioId: Int,
        val emoji: String, val acao: String,
    )
    private val _reacoes = MutableSharedFlow<EventoReacao>(replay = 0)
    val reacoesFlow: SharedFlow<EventoReacao> = _reacoes.asSharedFlow()

    // Mensagens novas (sincronizacao incremental)
    private val _novasMensagens = MutableSharedFlow<List<MensagemNovaItem>>(replay = 0)
    val novasMensagensFlow: SharedFlow<List<MensagemNovaItem>> = _novasMensagens.asSharedFlow()

    // Debounce por conversa para envio de digitando/gravando
    private val jobsDigitando = mutableMapOf<Int, Job>()
    private val jobsGravando = mutableMapOf<Int, Job>()
    private val expiryJobs = mutableMapOf<Pair<Int, Int>, Job>() // (conversaId, usuarioId) -> job

    init {
        registrarHandlers()
    }

    // ─── Operacoes REST ──────────────────────────────────────────────────────

    suspend fun deletar(mensagemId: Int): Result<Unit> = runCatching {
        val resp = api.deletarMensagem("Bearer ${token()}", mensagemId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    suspend fun reagir(mensagemId: Int, emoji: String): Result<ReacaoResponse> = runCatching {
        val resp = api.reagirMensagem("Bearer ${token()}", ReacaoRequest(mensagemId, emoji))
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    suspend fun enviarComReferencia(
        conversaId: Int,
        conteudos: List<ConteudoRequest>,
        referencia: MensagemReferencia? = null,
        visivelEm: String? = null,
    ): Result<EnviarMensagemResponse> = runCatching {
        val req = EnviarMensagemComReferenciaRequest(conversaId, conteudos, referencia, visivelEm)
        val resp = api.enviarMensagemComReferencia("Bearer ${token()}", req)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    suspend fun responder(
        conversaId: Int,
        conteudos: List<ConteudoRequest>,
        destinoMensagemId: Int,
    ): Result<EnviarMensagemResponse> =
        enviarComReferencia(
            conversaId,
            conteudos,
            MensagemReferencia(tipo = MensagemReferencia.TIPO_RESPONDER, destinoMensagemId = destinoMensagemId),
        )

    suspend fun encaminhar(
        destinoConversaId: Int,
        conteudos: List<ConteudoRequest>,
        origemMensagemId: Int,
    ): Result<EnviarMensagemResponse> =
        enviarComReferencia(
            destinoConversaId,
            conteudos,
            MensagemReferencia(tipo = MensagemReferencia.TIPO_ENCAMINHAR, destinoMensagemId = origemMensagemId),
        )

    suspend fun sincronizarDesde(desde: String): Result<List<MensagemNovaItem>> = runCatching {
        val resp = api.obterMensagensNovas("Bearer ${token()}", desde)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        val lista = resp.body() ?: emptyList()
        if (lista.isNotEmpty()) _novasMensagens.emit(lista)
        lista
    }

    suspend fun status(conversaId: Int, mensagemId: Int): Result<List<MensagemStatusResponse>> = runCatching {
        val resp = api.statusMensagem("Bearer ${token()}", conversaId, mensagemId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: emptyList()
    }

    suspend fun marcarReproduzida(mensagemId: Int, conversaId: Int): Result<Unit> = runCatching {
        val resp = api.reproduzirMensagem(
            "Bearer ${token()}",
            mapOf("mensagem_id" to mensagemId, "conversa_id" to conversaId),
        )
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    suspend fun pesquisar(
        texto: String,
        conversaId: Int? = null,
    ): Result<List<PesquisaResultado>> = runCatching {
        val resp = api.pesquisar("Bearer ${token()}", texto, conversaId = conversaId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: emptyList()
    }

    // ─── Digitando / Gravando (envio com debounce) ───────────────────────────

    fun usuarioDigitando(conversaId: Int) {
        jobsDigitando[conversaId]?.cancel()
        jobsDigitando[conversaId] = scope.launch {
            try {
                api.broadcastDigitando("Bearer ${token()}", DigitandoRequest(conversaId))
            } catch (e: Exception) { Log.w(TAG, "digitando falhou: ${e.message}") }
            delay(DEBOUNCE_INDICADOR_MS)
        }
    }

    fun usuarioGravando(conversaId: Int) {
        jobsGravando[conversaId]?.cancel()
        jobsGravando[conversaId] = scope.launch {
            try {
                api.broadcastGravando("Bearer ${token()}", DigitandoRequest(conversaId))
            } catch (e: Exception) { Log.w(TAG, "gravando falhou: ${e.message}") }
            delay(DEBOUNCE_INDICADOR_MS)
        }
    }

    // ─── Handlers de eventos WebSocket ───────────────────────────────────────

    private fun registrarHandlers() {
        socketManager.onDigitando = { conversaId, usuarioId ->
            marcarIndicador(_digitando, conversaId, usuarioId)
        }
        socketManager.onGravandoAudio = { conversaId, usuarioId ->
            marcarIndicador(_gravando, conversaId, usuarioId)
        }
        socketManager.onReacaoMensagem = { conversaId, mensagemId, usuarioId, emoji, acao ->
            scope.launch { _reacoes.emit(EventoReacao(conversaId, mensagemId, usuarioId, emoji, acao)) }
        }
    }

    private fun marcarIndicador(
        flow: MutableStateFlow<Map<Int, Set<Int>>>,
        conversaId: Int,
        usuarioId: Int,
    ) {
        val atual = flow.value
        flow.value = atual + (conversaId to ((atual[conversaId] ?: emptySet()) + usuarioId))

        // Agenda remocao apos expiry
        val chave = conversaId to usuarioId
        expiryJobs[chave]?.cancel()
        expiryJobs[chave] = scope.launch {
            delay(EXPIRY_INDICADOR_MS)
            val novo = flow.value
            val set = novo[conversaId] ?: return@launch
            flow.value = novo + (conversaId to (set - usuarioId))
        }
    }

    private suspend fun token(): String =
        userPreferences.authToken.first() ?: error("Sem token")
}
