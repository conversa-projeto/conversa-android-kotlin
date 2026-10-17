package com.conversa.app.feature.conversas.lista

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.EstadoPresenca
import com.conversa.app.core.model.FichaUsuario
import com.conversa.app.core.model.PresencaContato
import com.conversa.app.core.model.PreviaConversa
import com.conversa.app.core.model.RotuloData
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.avatarDoContato
import com.conversa.app.core.model.contatoCombina
import com.conversa.app.core.model.conversaCombina
import com.conversa.app.core.model.diretaCom
import com.conversa.app.core.model.estadoDe
import com.conversa.app.core.model.fichaDaConversa
import com.conversa.app.core.model.ordenarConversas
import com.conversa.app.core.model.previaDaConversa
import com.conversa.app.core.model.rotuloData
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.estado.EventosUnicos
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Um item da lista de conversas, já pronto para desenhar. */
@Immutable
data class ItemConversa(
    val id: Long,
    val titulo: String,
    val avatarUrl: String?,
    val grupo: Boolean,
    val online: Boolean,
    val fixada: Boolean,
    val arquivada: Boolean,
    /** Já zerado nas arquivadas (o contador não aparece nelas, CON-04). */
    val naoLidas: Int,
    val previa: PreviaConversa,
    val rotulo: RotuloData?,
    val digitando: Boolean,
    /** Alguém gravando áudio nesta conversa (tem prioridade sobre o "digitando", como o web). */
    val gravando: Boolean = false,
    /** Posição entre as fixadas, para habilitar "mover para cima/baixo". */
    val podeSubir: Boolean = false,
    val podeDescer: Boolean = false,
    /** Direta: o perfil da pessoa, aberto pelo avatar (8.4). */
    val ficha: FichaUsuario? = null,
    /** Direta com a pessoa ausente (bolinha amarela; [online] é a verde, de ativo). */
    val ausente: Boolean = false,
    /** Grupo com emoji (sem imagem): no lugar da inicial. */
    val emoji: String? = null,
)

/** Contato sem conversa direta, na seção "Nova conversa" (CON-02). */
@Immutable
data class ItemContato(
    val id: Long,
    val nome: String,
    val detalhe: String,
    val avatarUrl: String?,
    val online: Boolean,
    val ausente: Boolean = false,
)

@Immutable
data class ConversasUiState(
    val carregando: Boolean = true,
    val termo: String = "",
    /** Sem termo: as não arquivadas. Com termo: todas as que combinam, inclusive arquivadas. */
    val principais: List<ItemConversa> = emptyList(),
    /** Só sem termo: a seção recolhível "Chamadas (N)", dos chats de ligação não fixados (🆕 web `769bd7d`). */
    val chamadas: List<ItemConversa> = emptyList(),
    val chamadasAbertas: Boolean = false,
    /** Só sem termo: a seção recolhível "Arquivadas (N)". */
    val arquivadas: List<ItemConversa> = emptyList(),
    val arquivadasAbertas: Boolean = false,
    val novaConversa: List<ItemContato> = emptyList(),
    val atualizando: Boolean = false,
    /** Erro de carga, mostrado em tela cheia só quando não há nada no cache. */
    val erro: String? = null,
) {
    /** Nada para mostrar: nem conversas (em nenhuma seção) nem contatos. */
    val vazia: Boolean get() = principais.isEmpty() && chamadas.isEmpty() && arquivadas.isEmpty() && novaConversa.isEmpty()
}

sealed interface EventoConversas {
    data class Abrir(val conversaId: Long) : EventoConversas

    data class Erro(val mensagem: String) : EventoConversas
}

/**
 * Lista de conversas (CON-01…05, CON-13, PRE-01). O Room é a fonte; WS 2, 3 e 40
 * atualizam o Room pelo `SyncManager` e a lista acompanha sozinha.
 */
@HiltViewModel
class ConversasViewModel @Inject constructor(
    private val conversas: ConversasRepositorio,
    private val contatos: ContatosRepositorio,
    presenca: PresencaRepositorio,
    iniciador: IniciadorSessao,
    private val relogio: Clock,
) : ViewModel() {
    private val termo = MutableStateFlow("")
    private val arquivadasAbertas = MutableStateFlow(false)
    private val chamadasAbertas = MutableStateFlow(false)
    private val atualizando = MutableStateFlow(false)
    private val erro = MutableStateFlow<String?>(null)

    val eventos = EventosUnicos<EventoConversas>()

    private val dados = combine(
        conversas.observarTodas(),
        contatos.observarOutros(),
        presenca.presencas,
        presenca.digitando,
        presenca.gravando,
    ) { lista, pessoas, presencas, digitando, gravando -> Dados(lista, pessoas, presencas, digitando, gravando) }

    private val controles = combine(termo, arquivadasAbertas, chamadasAbertas, atualizando, erro) {
            t,
            abertas,
            chamadas,
            atualizandoAgora,
            falha,
        ->
        Controles(t, abertas, atualizandoAgora, falha, chamadas)
    }

    private val inicio = combine(iniciador.carregada, iniciador.falha) { carregada, falha -> Inicio(carregada, falha?.mensagemAmigavel()) }

    val estado: StateFlow<ConversasUiState> = combine(dados, controles, inicio) { d, c, i -> montarEstado(d, c, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversasUiState())

    fun alterarTermo(texto: String) {
        termo.value = texto
    }

    fun alternarChamadas() {
        chamadasAbertas.value = !chamadasAbertas.value
    }

    fun alternarArquivadas() {
        arquivadasAbertas.value = !arquivadasAbertas.value
    }

    /** Pull-to-refresh: conversas e contatos. */
    fun atualizar() {
        if (atualizando.value) return
        viewModelScope.launch {
            atualizando.value = true
            val resultadoConversas = async { conversas.atualizar() }
            val resultadoContatos = async { contatos.atualizar() }
            val falha = resultadoConversas.await().exceptionOrNull() ?: resultadoContatos.await().exceptionOrNull()
            erro.value = falha?.paraErroApi()?.mensagemAmigavel()
            if (falha != null) eventos.enviar(EventoConversas.Erro(falha.paraErroApi().mensagemAmigavel()))
            atualizando.value = false
        }
    }

    fun fixar(id: Long) = acao { conversas.fixar(id) }

    fun desafixar(id: Long) = acao { conversas.desafixar(id) }

    fun mover(id: Long, deslocamento: Int) = acao { conversas.moverFixada(id, deslocamento) }

    fun arquivar(id: Long, arquivada: Boolean) = acao { conversas.arquivar(id, arquivada) }

    /** "Nova conversa": abre a direta com o contato, criando se preciso (CON-06). */
    fun abrirContato(contatoId: Long) {
        viewModelScope.launch {
            conversas.obterOuCriarDireta(contatoId)
                .onSuccess { eventos.enviar(EventoConversas.Abrir(it)) }
                .onFailure { eventos.enviar(EventoConversas.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    private fun acao(bloco: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            bloco().onFailure { eventos.enviar(EventoConversas.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    private fun montarEstado(d: Dados, c: Controles, i: Inicio): ConversasUiState {
        val agora = relogio.instant()
        val zona = relogio.zone
        val ordenadas = ordenarConversas(d.conversas)
        val fixadasIds = ordenadas.filter { it.fixada && !it.arquivada }.map { it.id }

        fun item(conversa: Conversa): ItemConversa {
            val posicao = fixadasIds.indexOf(conversa.id)
            val direta = conversa.tipo == TipoConversa.DIRETA
            return ItemConversa(
                id = conversa.id,
                titulo = conversa.titulo,
                avatarUrl = conversa.avatarUrl,
                grupo = !direta,
                online = direta && d.presencas.estadoDe(conversa.destinatarioId) == EstadoPresenca.ATIVO,
                ausente = direta && d.presencas.estadoDe(conversa.destinatarioId) == EstadoPresenca.AUSENTE,
                fixada = conversa.fixada,
                arquivada = conversa.arquivada,
                naoLidas = if (conversa.arquivada) 0 else conversa.naoLidas,
                previa = previaDaConversa(conversa),
                rotulo = conversa.ultimaMensagemEm?.let { rotuloData(it, agora, zona) },
                digitando = d.digitando[conversa.id].orEmpty().isNotEmpty(),
                gravando = d.gravando[conversa.id].orEmpty().isNotEmpty(),
                podeSubir = posicao > 0,
                podeDescer = posicao >= 0 && posicao < fixadasIds.lastIndex,
                ficha = fichaDaConversa(conversa, d.contatos),
                emoji = conversa.emoji.takeIf { !direta },
            )
        }

        val comTermo = c.termo.isNotBlank()
        val principais = if (comTermo) {
            ordenadas.filter { conversaCombina(it, c.termo) }
        } else {
            ordenadas.filterNot { it.arquivada || naSecaoChamadas(it) }
        }
        // Os chats de ligação não fixados ficam numa seção própria no fim (com termo, tudo junto).
        val chamadas = if (comTermo) emptyList() else ordenadas.filter { !it.arquivada && naSecaoChamadas(it) }
        val arquivadas = if (comTermo) emptyList() else ordenadas.filter { it.arquivada }
        val novaConversa = d.contatos
            .filter { d.conversas.diretaCom(it.id) == null && contatoCombina(it, c.termo) }
            .map {
                val estado = d.presencas.estadoDe(it.id)
                ItemContato(
                    it.id,
                    it.nome,
                    it.login,
                    avatarDoContato(it, d.conversas),
                    estado == EstadoPresenca.ATIVO,
                    estado == EstadoPresenca.AUSENTE,
                )
            }

        return ConversasUiState(
            // Cache vazio e a primeira carga da sessão ainda não terminou: spinner, não "nenhuma conversa".
            carregando = d.conversas.isEmpty() && !i.carregada && i.falha == null,
            termo = c.termo,
            principais = principais.map(::item),
            chamadas = chamadas.map(::item),
            chamadasAbertas = c.chamadasAbertas,
            arquivadas = arquivadas.map(::item),
            arquivadasAbertas = c.arquivadasAbertas,
            novaConversa = novaConversa,
            atualizando = c.atualizando,
            erro = (c.erro ?: i.falha).takeIf { d.conversas.isEmpty() },
        )
    }

    private data class Dados(
        val conversas: List<Conversa>,
        val contatos: List<Contato>,
        val presencas: Map<Long, PresencaContato>,
        val digitando: Map<Long, Set<Long>>,
        val gravando: Map<Long, Set<Long>> = emptyMap(),
    )

    private data class Controles(
        val termo: String,
        val arquivadasAbertas: Boolean,
        val atualizando: Boolean,
        val erro: String?,
        val chamadasAbertas: Boolean = false,
    )

    /** "Chamadas (N)": chat de ligação que não está fixado (como o web: a fixada fica em cima). */
    private fun naSecaoChamadas(conversa: Conversa) = conversa.chamada && !conversa.fixada

    private data class Inicio(val carregada: Boolean, val falha: String?)
}
