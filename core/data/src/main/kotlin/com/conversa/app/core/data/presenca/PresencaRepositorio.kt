package com.conversa.app.core.data.presenca

import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Quem está online e quem está digitando/gravando (PRE-01, contrato §15).
 *
 * - Online: `GET /contatos/online` a cada (re)conexão + WS 60 `{usuario_id, online}`.
 *   Só vale para quem tem conversa direta com o usuário.
 * - Digitando/gravando: WS 4/5. Não existe "parou de digitar": cada aviso vale
 *   [EXPIRACAO_MS] (mesmo tempo do web) e some antes se chegar mensagem nova.
 */
@Singleton
class PresencaRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val tempoReal: RealtimeClient,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _online = MutableStateFlow<Set<Long>>(emptySet())
    val online: StateFlow<Set<Long>> = _online.asStateFlow()

    private val _digitando = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Conversa → usuários digitando nela. */
    val digitando: StateFlow<Map<Long, Set<Long>>> = _digitando.asStateFlow()

    private val _gravando = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Conversa → usuários gravando áudio nela. */
    val gravando: StateFlow<Map<Long, Set<Long>>> = _gravando.asStateFlow()

    private val trava = Any()
    private val expiracoes = mutableMapOf<Triple<Boolean, Long, Long>, Job>()
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        tempoReal.eventos.onEach(::tratar).launchIn(escopo)
    }

    suspend fun recarregarOnline() {
        chamarApi { api.contatosOnline() }.onSuccess { _online.value = it.toSet() }
    }

    /** Chegou mensagem nessa conversa: quem digitava terminou. */
    fun limparDigitando(conversaId: Long) {
        synchronized(trava) {
            expiracoes.keys.filter { it.second == conversaId }.forEach { expiracoes.remove(it)?.cancel() }
        }
        _digitando.update { it - conversaId }
        _gravando.update { it - conversaId }
    }

    /** Fim da sessão. */
    fun limpar() {
        synchronized(trava) {
            expiracoes.values.forEach { it.cancel() }
            expiracoes.clear()
        }
        _online.value = emptySet()
        _digitando.value = emptyMap()
        _gravando.value = emptyMap()
    }

    private fun tratar(evento: EventoSocket) {
        when (evento) {
            is EventoSocket.StatusUsuario -> _online.update { if (evento.online) it + evento.usuarioId else it - evento.usuarioId }
            is EventoSocket.Digitando -> marcar(gravacao = false, evento.conversaId, evento.usuarioId)
            is EventoSocket.GravandoAudio -> marcar(gravacao = true, evento.conversaId, evento.usuarioId)
            else -> Unit
        }
    }

    private fun marcar(gravacao: Boolean, conversaId: Long, usuarioId: Long) {
        val estado = if (gravacao) _gravando else _digitando
        estado.update { mapa -> mapa + (conversaId to (mapa[conversaId].orEmpty() + usuarioId)) }
        val chave = Triple(gravacao, conversaId, usuarioId)
        val expiracao = escopo.launch {
            delay(EXPIRACAO_MS)
            estado.update { mapa ->
                val restantes = mapa[conversaId].orEmpty() - usuarioId
                if (restantes.isEmpty()) mapa - conversaId else mapa + (conversaId to restantes)
            }
        }
        synchronized(trava) { expiracoes.put(chave, expiracao)?.cancel() }
    }

    companion object {
        /** Igual ao web (`DIGITANDO_EXPIRACAO_MS`). */
        const val EXPIRACAO_MS = 4_000L
    }
}
