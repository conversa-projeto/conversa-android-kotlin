package com.conversa.app.core.data.presenca

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.notificacoes.ConversaEmTela
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.model.EstadoPresenca
import com.conversa.app.core.model.PresencaContato
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presença dos contatos e quem está digitando/gravando (PRE-01…03, contrato §15).
 *
 * - Estado (servidor `769bd7d`): `GET /contatos/presenca` a cada (re)conexão + WS 60
 *   `{usuario_id, estado, visto_em}`: ativo, ausente ou offline, e o "visto por último" (só
 *   vem quando muda). Só vale para quem tem conversa direta com o usuário; o meu próprio
 *   estado chega pelo WS 60 logo depois do login no socket.
 * - Quem está com cada conversa aberta: `GET /conversa/presentes` ao abrir + WS 63 `aberta`.
 * - A minha presença (PRE-02): WS 63 `{ativo, conversa_id}`, ativo com o app na frente e a
 *   conversa que está na tela; só quando muda, e de novo a cada login no socket. Sem isso o
 *   servidor considera o aparelho ativo enquanto o socket está aberto (até em segundo plano).
 * - Digitando/gravando: WS 4/5. Não existe "parou de digitar": cada aviso vale
 *   [EXPIRACAO_MS] (mesmo tempo do web) e some antes se chegar mensagem nova.
 */
@Singleton
class PresencaRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val tempoReal: RealtimeClient,
    private val sessao: SessaoRepositorio,
    private val primeiroPlano: MonitorPrimeiroPlano,
    private val emTela: ConversaEmTela,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _presencas = MutableStateFlow<Map<Long, PresencaContato>>(emptyMap())

    /** Usuário → estado e "visto por último" (quem não está aqui está offline). */
    val presencas: StateFlow<Map<Long, PresencaContato>> = _presencas.asStateFlow()

    private val _presentes = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Conversa → quem está com ela aberta agora (sem mim). */
    val presentes: StateFlow<Map<Long, Set<Long>>> = _presentes.asStateFlow()

    private val _digitando = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Conversa → usuários digitando nela. */
    val digitando: StateFlow<Map<Long, Set<Long>>> = _digitando.asStateFlow()

    private val _gravando = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    /** Conversa → usuários gravando áudio nela. */
    val gravando: StateFlow<Map<Long, Set<Long>>> = _gravando.asStateFlow()

    /** O servidor já aceitou o login deste socket (chegou o meu WS 60): a minha presença pode ir. */
    private val logado = MutableStateFlow(false)

    private val trava = Any()
    private val expiracoes = mutableMapOf<Triple<Boolean, Long, Long>, Job>()
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        tempoReal.eventos.onEach(::tratar).launchIn(escopo)
        tempoReal.estado.onEach { if (it != EstadoConexao.CONECTADO) logado.value = false }.launchIn(escopo)
        // A minha presença: nulo enquanto o socket não está logado (aí o próximo login reenvia).
        combine(logado, primeiroPlano.emPrimeiroPlano, emTela.id) { ok, visivel, conversa ->
            if (ok) MinhaPresenca(visivel, conversa.takeIf { visivel }) else null
        }
            .distinctUntilChanged()
            .onEach { presenca -> presenca?.let { tempoReal.enviarPresenca(it.ativo, it.conversaId) } }
            .launchIn(escopo)
    }

    /** `GET /contatos/presenca` (a lista não traz o meu estado: ele fica como o WS 60 mandou). */
    suspend fun recarregarOnline() {
        chamarApi { api.contatosPresenca() }.onSuccess { lista ->
            val eu = sessao.sessao.value?.usuarioId
            _presencas.update { atual ->
                val meu = eu?.let { atual[it] }?.let { mapOf(eu to it) }.orEmpty()
                lista.associate { it.usuarioId to PresencaContato(EstadoPresenca.de(it.estado) ?: EstadoPresenca.OFFLINE, it.vistoEm) } +
                    meu
            }
        }
    }

    /** Quem está com a conversa aberta (ao abrir o chat e a lista de membros). */
    suspend fun carregarPresentes(conversaId: Long) {
        chamarApi { api.presentesConversa(conversaId) }.onSuccess { ids -> _presentes.update { it + (conversaId to ids.toSet()) } }
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
        logado.value = false
        _presencas.value = emptyMap()
        _presentes.value = emptyMap()
        _digitando.value = emptyMap()
        _gravando.value = emptyMap()
    }

    private fun tratar(evento: EventoSocket) {
        when (evento) {
            is EventoSocket.StatusUsuario -> {
                val estado = EstadoPresenca.de(evento.estado) ?: if (evento.online) EstadoPresenca.ATIVO else EstadoPresenca.OFFLINE
                // O "visto por último" só vem quando muda: sem ele, fica o que já se sabia (como o web).
                val visto = evento.vistoEm?.let { runCatching { Instant.parse(it) }.getOrNull() }
                _presencas.update { atual ->
                    atual +
                        (evento.usuarioId to PresencaContato(estado, visto ?: atual[evento.usuarioId]?.vistoEm))
                }
                if (evento.usuarioId == sessao.sessao.value?.usuarioId) logado.value = true
            }
            is EventoSocket.PresencaConversa -> _presentes.update { mapa ->
                val atuais = mapa[evento.conversaId].orEmpty()
                mapa + (evento.conversaId to if (evento.aberta) atuais + evento.usuarioId else atuais - evento.usuarioId)
            }
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

    private data class MinhaPresenca(val ativo: Boolean, val conversaId: Long?)

    companion object {
        /** Igual ao web (`DIGITANDO_EXPIRACAO_MS`). */
        const val EXPIRACAO_MS = 4_000L
    }
}
