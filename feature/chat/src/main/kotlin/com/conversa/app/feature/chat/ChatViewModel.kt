package com.conversa.app.feature.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.model.AtividadeConversa
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.atividadeDaConversa
import com.conversa.app.core.model.montarItensChat
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.estado.EventosUnicos
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ChatUiState(
    val conversa: Conversa? = null,
    val online: Boolean = false,
    /** Grupo: nomes dos membros (some quando alguém digita). */
    val membros: String = "",
    val atividade: AtividadeConversa = AtividadeConversa.Nenhuma,
    /** Em ordem cronológica (a tela desenha de baixo para cima). */
    val itens: List<ItemChat> = emptyList(),
    val eu: Long = 0,
    /** Ainda não há nada no Room e a primeira carga não terminou. */
    val carregando: Boolean = true,
    val carregandoAnteriores: Boolean = false,
    val chegouAoInicio: Boolean = false,
    /** A primeira não lida já foi decidida: a tela pode se posicionar (MSG-05). */
    val pronto: Boolean = false,
) {
    val grupo: Boolean get() = conversa?.tipo == TipoConversa.GRUPO
}

sealed interface EventoChat {
    data class Erro(val mensagem: String) : EventoChat

    /** Depois de enviar: descer até a última mensagem. */
    data object RolarAoFim : EventoChat

    /** Tocou numa menção: abrir a conversa direta com a pessoa (CON-06). */
    data class AbrirConversa(val conversaId: Long) : EventoChat
}

/**
 * Conversa aberta (CON-09, MSG-01…14, ENV-01, ENV-15). O Room é a fonte; ao abrir,
 * recarrega as recentes da rede (inclusive quando vem de notificação, CON-12).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    salvo: SavedStateHandle,
    private val conversas: ConversasRepositorio,
    contatos: ContatosRepositorio,
    presenca: PresencaRepositorio,
    sessao: SessaoRepositorio,
    private val mensagens: MensagensRepositorio,
    private val envio: EnvioMensagens,
    private val relogio: Clock,
) : ViewModel() {
    val conversaId: Long = checkNotNull(salvo["conversaId"])
    private val eu: Long = sessao.sessao.value?.usuarioId ?: 0

    private val membros = MutableStateFlow<List<MembroConversa>>(emptyList())
    private val carga = MutableStateFlow(Carga())

    /**
     * Primeira não lida, fixada na abertura (a linha "Últimas" não pula enquanto a
     * pessoa lê, MSG-03). `null` = ainda não decidida; 0 = não há.
     */
    private val primeiraNaoLida = MutableStateFlow<Long?>(null)

    val eventos = EventosUnicos<EventoChat>()

    private val nomes = combine(contatos.observarOutros(), membros) { pessoas, lista ->
        pessoas.associate { it.id to it.nome } + lista.associate { it.usuarioId to it.nome }
    }

    private val presente = combine(presenca.online, presenca.digitando, presenca.gravando, nomes) { online, digitando, gravando, n ->
        Presente(online, digitando[conversaId].orEmpty(), gravando[conversaId].orEmpty(), n)
    }

    val estado: StateFlow<ChatUiState> = combine(
        conversas.observar(conversaId),
        mensagens.observar(conversaId),
        presente,
        carga,
        primeiraNaoLida,
    ) { conversa, lista, p, c, naoLida ->
        val grupo = conversa?.tipo == TipoConversa.GRUPO
        ChatUiState(
            conversa = conversa,
            online = conversa?.tipo == TipoConversa.DIRETA && conversa.destinatarioId in p.online,
            membros = if (grupo) membros.value.map { it.nome }.sorted().joinToString(", ") else "",
            atividade = atividadeDaConversa(grupo, p.digitando, p.gravando, p.nomes, eu),
            itens = montarItensChat(lista, naoLida?.takeIf { it > 0 }, grupo, eu, relogio.zone),
            eu = eu,
            carregando = lista.isEmpty() && !c.carregou,
            carregandoAnteriores = c.anteriores,
            chegouAoInicio = c.inicio,
            pronto = naoLida != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(eu = eu))

    init {
        viewModelScope.launch {
            val resultado = mensagens.carregarRecentes(conversaId)
            carga.value = carga.value.copy(carregou = true)
            resultado.onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
            // "Últimas": a primeira de outra pessoa ainda não lida, decidida uma vez só.
            val lista = mensagens.observar(conversaId).first()
            primeiraNaoLida.value = lista.firstOrNull { it.remetenteId != eu && !it.visualizada && it.id > 0 && !it.oculta }?.id ?: 0
        }
        viewModelScope.launch {
            val conversa = conversas.observar(conversaId).first()
            if (conversa?.tipo == TipoConversa.GRUPO) conversas.membros(conversaId).onSuccess { membros.value = it }
        }
    }

    /** A lista chegou perto do topo. */
    fun carregarAnteriores() {
        val atual = carga.value
        if (atual.anteriores || atual.inicio || !atual.carregou) return
        viewModelScope.launch {
            carga.value = carga.value.copy(anteriores = true)
            mensagens.carregarAnteriores(conversaId)
                .onSuccess { quantas -> carga.value = carga.value.copy(anteriores = false, inicio = quantas == 0) }
                .onFailure {
                    carga.value = carga.value.copy(anteriores = false)
                    eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel()))
                }
        }
    }

    /** Mensagens de outras pessoas que apareceram na tela com o app em primeiro plano (MSG-04). */
    fun marcarLidas(visiveis: List<Mensagem>) {
        val paraLer = visiveis.filter { it.remetenteId != eu && !it.visualizada && it.id > 0 }
        if (paraLer.isEmpty()) return
        viewModelScope.launch { paraLer.forEach { mensagens.marcarLida(conversaId, it.id) } }
    }

    /** Envia e avisa a tela para limpar o campo só depois de gravado no Room. */
    fun enviar(texto: String, aoGravar: () -> Unit) {
        val limpo = texto.trim()
        if (limpo.isEmpty()) return
        viewModelScope.launch {
            envio.enviarTexto(conversaId, limpo)
            aoGravar()
            reiniciarDigitando()
            eventos.enviar(EventoChat.RolarAoFim)
        }
    }

    /** Menção tocada: obtém ou cria a direta e abre. */
    fun abrirDireta(usuarioId: Long) {
        if (usuarioId == eu) return
        viewModelScope.launch {
            conversas.obterOuCriarDireta(usuarioId)
                .onSuccess { if (it != conversaId) eventos.enviar(EventoChat.AbrirConversa(it)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    fun reenviar(idLocal: Long) {
        viewModelScope.launch { envio.reenviar(idLocal) }
    }

    fun descartar(idLocal: Long) {
        viewModelScope.launch { envio.descartar(idLocal) }
    }

    // --- Digitando (ENV-15): no máximo 1 aviso a cada 2,5 s; o último é adiado, não descartado ---

    private var janela: Job? = null
    private var pendente = false

    fun aoDigitar(texto: String) {
        if (texto.isBlank()) return
        if (janela?.isActive == true) {
            pendente = true
            return
        }
        avisarEAbrirJanela()
    }

    private fun avisarEAbrirJanela() {
        pendente = false
        viewModelScope.launch { mensagens.avisarDigitando(conversaId) }
        janela = viewModelScope.launch {
            delay(INTERVALO_DIGITANDO_MS)
            if (pendente) avisarEAbrirJanela()
        }
    }

    private fun reiniciarDigitando() {
        janela?.cancel()
        janela = null
        pendente = false
    }

    private data class Carga(val carregou: Boolean = false, val anteriores: Boolean = false, val inicio: Boolean = false)

    private data class Presente(val online: Set<Long>, val digitando: Set<Long>, val gravando: Set<Long>, val nomes: Map<Long, String>)

    companion object {
        /** Igual ao web (`DIGITANDO_DEBOUNCE_MS`). */
        const val INTERVALO_DIGITANDO_MS = 2_500L
    }
}
