package com.conversa.conversa.data.repository

import android.content.Context
import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.chamada.model.EventoChamadaUI
import com.conversa.conversa.data.chamada.model.TipoEventoChamadaUI
import com.conversa.conversa.data.model.ChamadaIdRequest
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.data.model.EstadoChamadaLocal
import com.conversa.conversa.data.model.EventoChamada
import com.conversa.conversa.data.model.IniciarChamadaRequest
import com.conversa.conversa.data.model.UsuarioIdDto
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.socket.SocketManager
import com.conversa.conversa.data.webrtc.WebRTCManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orquestra chamadas WebRTC: eventos WS 51-56 e REST /api/chamada.
 * Sinalizacao SDP/ICE delegada ao WebRTCManager (WHIP/WHEP direto no MediaMTX).
 */
class ChamadaRepository(
    private val context: Context,
    private val api: ConversaApi,
    private val webRTCManager: WebRTCManager,
    private val socketManager: SocketManager,
    private val userPreferences: UserPreferences,
) {
    companion object { private const val TAG = "ChamadaRepository" }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sincronizarMutex = Mutex()

    private val _chamadaAtual = MutableStateFlow<ChamadaResponse?>(null)
    val chamadaAtualFlow: StateFlow<ChamadaResponse?> = _chamadaAtual.asStateFlow()

    private val _estadoLocal = MutableStateFlow(EstadoChamadaLocal.DESCONHECIDO)
    val estadoLocalFlow: StateFlow<EstadoChamadaLocal> = _estadoLocal.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoChamada>(replay = 0)
    val eventosChamadaFlow: SharedFlow<EventoChamada> = _eventos.asSharedFlow()

    private val _eventosUI = MutableSharedFlow<EventoChamadaUI>(replay = 0)
    val eventosUIFlow: SharedFlow<EventoChamadaUI> = _eventosUI.asSharedFlow()

    private val _videoRemotoAtivado = MutableSharedFlow<Pair<Int, Int>>(replay = 0)
    val videoRemotoAtivadoFlow: SharedFlow<Pair<Int, Int>> = _videoRemotoAtivado.asSharedFlow()

    var onChamadaIniciada: ((ChamadaResponse) -> Unit)? = null
    var onChamadaConectada: (() -> Unit)? = null
    var onChamadaFinalizada: (() -> Unit)? = null
    var onErro: ((String) -> Unit)? = null

    val chamadaAtual: ChamadaResponse? get() = _chamadaAtual.value

    init {
        webRTCManager.inicializar()
        registrarHandlersSocket()
        registrarCallbacksWebRTC()
    }

    // ─── REST ────────────────────────────────────────────────────────────────

    suspend fun iniciarChamada(destinatariosIds: List<Int>, comVideo: Boolean = false): Result<ChamadaResponse> =
        runCatching {
            val (token, meuUid) = credenciais()
            _estadoLocal.value = EstadoChamadaLocal.INICIANDO_CHAMADA

            val todosUsuarios = (listOf(meuUid) + destinatariosIds).distinct()
            val request = IniciarChamadaRequest(
                tipo = if (todosUsuarios.size == 2) 1 else 2,
                usuarios = todosUsuarios.map { UsuarioIdDto(it) },
            )
            val response = api.iniciarChamada("Bearer $token", request)
            if (!response.isSuccessful) error("Erro ao iniciar chamada: ${response.code()}")
            val chamada = response.body()!!
            _chamadaAtual.value = chamada

            webRTCManager.adquirirMidiaLocal(comVideo)
            webRTCManager.publicarLocalNaSala(chamada.id, meuUid)

            withContext(Dispatchers.Main) { onChamadaIniciada?.invoke(chamada) }
            chamada
        }.onFailure {
            Log.e(TAG, "iniciarChamada falhou", it)
            _estadoLocal.value = EstadoChamadaLocal.DESCONHECIDO
        }

    suspend fun aceitarChamada(chamadaId: Int, comVideo: Boolean = false): Result<Unit> = runCatching {
        val (token, meuUid) = credenciais()

        val dados = obterDadosChamada(chamadaId).getOrThrow()
        _chamadaAtual.value = dados

        val response = api.entrarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
        if (!response.isSuccessful) error("Erro API entrarChamada: ${response.code()}")

        webRTCManager.adquirirMidiaLocal(comVideo)
        webRTCManager.publicarLocalNaSala(chamadaId, meuUid)

        dados.usuarios.filter { it.usuarioId != meuUid && it.status == STATUS_ENTROU }
            .forEach { webRTCManager.assinarDePeer(chamadaId, it.usuarioId) }

        _estadoLocal.value = EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO
        withContext(Dispatchers.Main) { onChamadaConectada?.invoke(); Unit }
    }.onFailure { Log.e(TAG, "aceitarChamada falhou", it) }

    suspend fun recusarChamada(chamadaId: Int): Result<Unit> = runCatching {
        val token = userPreferences.authToken.first() ?: error("Sem token")
        val response = api.recusarChamada("Bearer $token", ChamadaIdRequest(chamadaId))
        if (!response.isSuccessful) error("Erro API recusarChamada: ${response.code()}")
        _estadoLocal.value = EstadoChamadaLocal.RECUSADA
        if (_chamadaAtual.value?.id == chamadaId) _chamadaAtual.value = null
    }

    suspend fun finalizarChamada(): Result<Unit> = runCatching {
        val chamada = _chamadaAtual.value
        webRTCManager.desligar()
        if (chamada != null) {
            val token = userPreferences.authToken.firstOrNull()
            if (token != null) {
                try { api.sairChamada("Bearer $token", ChamadaIdRequest(chamada.id)) }
                catch (e: Exception) { Log.w(TAG, "sairChamada API falhou: ${e.message}") }
            }
        }
        _chamadaAtual.value = null
        _estadoLocal.value = EstadoChamadaLocal.CHAMADA_FINALIZADA
        withContext(Dispatchers.Main) { onChamadaFinalizada?.invoke() }
    }

    /**
     * Popula _chamadaAtual e estadoLocal a partir de um evento RECEBIDA externo
     * (chamado pelo ChamadaService quando o handler de socket foi sobrescrito por outro consumidor).
     * Garante que aceitarChamada()/recusarChamada() consigam ler o id depois.
     */
    suspend fun processarEventoChamadaRecebida(chamadaId: Int, usuarioId: Int): Result<ChamadaResponse> = runCatching {
        val dados = obterDadosChamada(chamadaId).getOrThrow()
        _chamadaAtual.value = dados
        _estadoLocal.value = EstadoChamadaLocal.RECEBENDO_CHAMADA
        val nome = dados.usuarios.firstOrNull { it.usuarioId == usuarioId }?.usuarioNome ?: ""
        _eventos.emit(EventoChamada.Recebida(chamadaId, usuarioId, nome))
        dados
    }

    suspend fun obterDadosChamada(chamadaId: Int): Result<ChamadaResponse> = runCatching {
        val token = userPreferences.authToken.first() ?: error("Sem token")
        val response = api.obterDadosChamada("Bearer $token", chamadaId)
        if (!response.isSuccessful) error("Erro API obterDadosChamada: ${response.code()}")
        response.body()!!
    }

    /** Upgrade audio→video: bate no endpoint REST + liga video no manager (renegocia WHIP). */
    suspend fun alternarVideo(ativar: Boolean): Result<Unit> = runCatching {
        val chamada = _chamadaAtual.value ?: error("Sem chamada ativa")
        if (ativar) {
            val token = userPreferences.authToken.first() ?: error("Sem token")
            val resp = api.ativarVideoChamada("Bearer $token", ChamadaIdRequest(chamada.id))
            if (!resp.isSuccessful) error("ativarVideoChamada: ${resp.code()}")
        }
        webRTCManager.alternarVideo(ativar)
    }

    // ─── Sincronizacao de peers (espelha sincronizarPeersAtivos do web) ──────

    /**
     * Busca /api/chamada/dados, assina peers com status=Entrou e fecha peers
     * que nao estao mais na lista. Tambem chamada ao reconectar WS.
     */
    suspend fun sincronizarPeersAtivos() {
        if (_estadoLocal.value != EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO) return
        if (!sincronizarMutex.tryLock()) return
        try {
            val chamadaId = _chamadaAtual.value?.id ?: return
            val meuUid = userPreferences.userId.firstOrNull() ?: return
            val dados = obterDadosChamada(chamadaId).getOrNull() ?: return
            _chamadaAtual.value = dados

            val ativos = dados.usuarios.filter { it.usuarioId != meuUid && it.status == STATUS_ENTROU }
            val idsAtivos = ativos.map { it.usuarioId }.toSet()

            val peersAtuais = webRTCManager.peers.value.keys.toSet()
            peersAtuais.filter { it !in idsAtivos }.forEach { webRTCManager.desconectarPeer(it) }

            for (usuario in ativos) {
                try { webRTCManager.assinarDePeer(chamadaId, usuario.usuarioId) }
                catch (e: Exception) { Log.e(TAG, "Falha sincronizar peer ${usuario.usuarioId}", e) }
            }
        } finally {
            sincronizarMutex.unlock()
        }
    }

    // ─── Controles de midia ──────────────────────────────────────────────────

    fun toggleMuteMicrofone(mutado: Boolean) = webRTCManager.alternarMicrofone(mutado)
    fun toggleCamera(mutada: Boolean) = webRTCManager.alternarCamera(mutada)
    fun trocarCamera() = webRTCManager.trocarCamera()
    fun toggleSpeaker(ligado: Boolean) = webRTCManager.alternarAltoFalante(ligado)

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    fun cleanup() {
        scope.launch { webRTCManager.desligar() }
        _chamadaAtual.value = null
        _estadoLocal.value = EstadoChamadaLocal.DESCONHECIDO
        scope.cancel()
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private suspend fun credenciais(): Pair<String, Int> {
        val token = userPreferences.authToken.first() ?: error("Sem token")
        val uid = userPreferences.userId.first() ?: error("Sem userId")
        return token to uid
    }

    private fun registrarHandlersSocket() {
        socketManager.onChamadaRecebida = { chamadaId, usuarioId, _ ->
            scope.launch {
                obterDadosChamada(chamadaId).onSuccess { c ->
                    _chamadaAtual.value = c
                    _estadoLocal.value = EstadoChamadaLocal.RECEBENDO_CHAMADA
                    _eventos.emit(EventoChamada.Recebida(chamadaId, usuarioId, c.usuarios
                        .firstOrNull { it.usuarioId == usuarioId }?.usuarioNome ?: ""))
                }
            }
        }

        socketManager.onUsuarioEntrou = { chamadaId, peerId ->
            scope.launch {
                val meuUid = userPreferences.userId.firstOrNull()
                if (peerId != meuUid && _chamadaAtual.value?.id == chamadaId) {
                    _estadoLocal.value = EstadoChamadaLocal.CHAMADA_EM_ANDAMENTO
                    sincronizarPeersAtivos()
                }
                _eventos.emit(EventoChamada.UsuarioEntrou(chamadaId, peerId))
                _eventosUI.emit(EventoChamadaUI(TipoEventoChamadaUI.PARTICIPANTE_ENTROU, chamadaId, peerId))
            }
        }

        socketManager.onUsuarioSaiu = { chamadaId, peerId ->
            scope.launch {
                if (_chamadaAtual.value?.id == chamadaId) {
                    webRTCManager.desconectarPeer(peerId)
                    obterDadosChamada(chamadaId).onSuccess { _chamadaAtual.value = it }
                }
                _eventos.emit(EventoChamada.UsuarioSaiu(chamadaId, peerId))
                _eventosUI.emit(EventoChamadaUI(TipoEventoChamadaUI.PARTICIPANTE_SAIU, chamadaId, peerId))
            }
        }

        socketManager.onUsuarioRecusou = { chamadaId, peerId ->
            scope.launch {
                _eventos.emit(EventoChamada.UsuarioRecusou(chamadaId, peerId))
                _eventosUI.emit(EventoChamadaUI(TipoEventoChamadaUI.CHAMADA_RECUSADA, chamadaId, peerId))
            }
        }

        socketManager.onChamadaFinalizada = { chamadaId, usuarioId ->
            scope.launch {
                if (_chamadaAtual.value?.id == chamadaId) {
                    webRTCManager.desligar()
                    _chamadaAtual.value = null
                    _estadoLocal.value = EstadoChamadaLocal.CHAMADA_FINALIZADA
                }
                _eventos.emit(EventoChamada.Finalizada(chamadaId, usuarioId))
                _eventosUI.emit(EventoChamadaUI(TipoEventoChamadaUI.CHAMADA_FINALIZADA, chamadaId, usuarioId))
                withContext(Dispatchers.Main) { onChamadaFinalizada?.invoke() }
            }
        }

        socketManager.onVideoAtivado = { chamadaId, peerId ->
            scope.launch { _videoRemotoAtivado.emit(chamadaId to peerId) }
        }

        // Reconexao WS: ressincronizar peers (chamadas pendentes sao puxadas pelo SocketService)
        socketManager.adicionarOnConectado {
            scope.launch { sincronizarPeersAtivos() }
        }
    }

    private fun registrarCallbacksWebRTC() {
        webRTCManager.onErro = { msg ->
            Log.e(TAG, "WebRTC erro: $msg")
            scope.launch(Dispatchers.Main) { onErro?.invoke(msg) }
        }
    }
}

// 1=Pendente, 2=Recusou, 3=Entrou, 4=Saiu, 5=Desconectou
private const val STATUS_ENTROU = 3
