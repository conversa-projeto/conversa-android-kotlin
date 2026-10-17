package com.conversa.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.pastas.PastasRepositorio
import com.conversa.app.core.model.LIMITE_NOME_PASTA
import com.conversa.app.core.model.MENSAGENS_POR_PAGINA_PASTA
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Pasta
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que deu errado, com o texto do web; [motivo] é a mensagem do servidor. */
data class FalhaPasta(val acao: AcaoPasta, val motivo: String)

enum class AcaoPasta { CRIAR, RENOMEAR, MUDAR, COR, EXCLUIR, POR, TIRAR, CARREGAR }

/** A pasta aberta por cima do chat (MSG-24): as mensagens dela, da mais antiga para a mais nova. */
data class PastaAbertaUi(
    val pasta: Pasta,
    val mensagens: List<Mensagem> = emptyList(),
    val carregando: Boolean = true,
    /** Já veio a página mais antiga: não há mais o que buscar. */
    val fim: Boolean = false,
    /** Selecionadas aqui para tirar de uma vez. */
    val selecionadas: Set<Long> = emptySet(),
)

/**
 * Pastas de mensagens da conversa direta aberta (7.14; web `stores/pastas.ts` e `PainelPastas.vue`):
 * a lista da folha, criar, renomear, cor, compartilhar e excluir (só o dono); as mensagens
 * selecionadas no chat para pôr numa pasta; e a pasta aberta, com tirar uma ou várias.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PastasViewModel @Inject constructor(salvo: SavedStateHandle, private val repositorio: PastasRepositorio, sessao: SessaoRepositorio) :
    ViewModel() {
    private val conversaId: Long = checkNotNull(salvo["conversaId"])
    private val direta = MutableStateFlow(false)

    /** Eu: só o dono muda a pasta. */
    val eu: StateFlow<Long> = sessao.sessao.map { it?.usuarioId ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** As pastas da conversa (vazio em grupo), por nome. */
    val pastas: StateFlow<List<Pasta>> = direta.flatMapLatest { if (it) repositorio.observar(conversaId) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selecionadas = MutableStateFlow<Set<Long>>(emptySet())

    /** Mensagens selecionadas no chat (toque longo › "Selecionar"); vazio = sem seleção. */
    val selecionadas: StateFlow<Set<Long>> = _selecionadas.asStateFlow()

    private val _aberta = MutableStateFlow<PastaAbertaUi?>(null)

    /** A pasta aberta, com os dados da lista sempre atualizados (nome, contagem). */
    val aberta: StateFlow<PastaAbertaUi?> = combine(_aberta, pastas) { aberta, lista ->
        aberta?.let { atual -> lista.find { it.id == atual.pasta.id }?.let { atual.copy(pasta = it) } ?: atual }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A pasta aberta já apareceu na lista (só então sumir dela fecha a pasta). */
    private var abertaNaLista = false

    private val _falhas = Channel<FalhaPasta>(Channel.BUFFERED)
    val falhas: Flow<FalhaPasta> = _falhas.receiveAsFlow()

    init {
        // A pasta aberta relê quando ganha ou perde mensagens (aqui, em outro aparelho ou pela outra pessoa).
        repositorio.mensagensMudaram.onEach { pastaId ->
            if (_aberta.value?.pasta?.id ==
                pastaId
            ) {
                recarregarAberta()
            }
        }.launchIn(viewModelScope)
        // A pasta aberta que sumiu (excluída, deixou de ser compartilhada) fecha, como no web. Só
        // depois de aparecer na lista: antes de a lista carregar, ela ainda não está lá.
        pastas.onEach { lista ->
            val atual = _aberta.value ?: return@onEach
            if (lista.any { it.id == atual.pasta.id }) {
                abertaNaLista = true
            } else if (abertaNaLista) {
                fecharPasta()
            }
        }.launchIn(viewModelScope)
    }

    /** A conversa é direta: lê as pastas. Em grupo não há pastas. */
    fun usarConversa(ehDireta: Boolean) {
        if (direta.value == ehDireta) return
        direta.value = ehDireta
        if (ehDireta) viewModelScope.launch { repositorio.carregar(conversaId).onFailure { falhar(AcaoPasta.CARREGAR, it) } }
    }

    fun criar(nome: String) {
        val limpo = nome.trim().take(LIMITE_NOME_PASTA)
        if (limpo.isEmpty()) return
        viewModelScope.launch { repositorio.criar(conversaId, limpo).onFailure { falhar(AcaoPasta.CRIAR, it) } }
    }

    fun renomear(pasta: Pasta, nome: String) {
        val limpo = nome.trim().take(LIMITE_NOME_PASTA)
        if (limpo.isEmpty() || limpo == pasta.nome) return
        viewModelScope.launch { repositorio.alterar(pasta, nome = limpo).onFailure { falhar(AcaoPasta.RENOMEAR, it) } }
    }

    fun mudarCor(pasta: Pasta, cor: String) {
        if (cor == pasta.cor) return
        viewModelScope.launch { repositorio.alterar(pasta, cor = cor).onFailure { falhar(AcaoPasta.COR, it) } }
    }

    fun alternarCompartilhada(pasta: Pasta) {
        viewModelScope.launch { repositorio.alterar(pasta, compartilhada = !pasta.compartilhada).onFailure { falhar(AcaoPasta.MUDAR, it) } }
    }

    fun excluir(pasta: Pasta) {
        viewModelScope.launch { repositorio.excluir(pasta).onFailure { falhar(AcaoPasta.EXCLUIR, it) } }
    }

    // --- Seleção no chat ---

    /** "Selecionar" no menu da mensagem: começa (ou soma) com ela. Só as já salvas. */
    fun selecionar(mensagemId: Long) {
        if (mensagemId > 0) _selecionadas.update { it + mensagemId }
    }

    fun alternarSelecao(mensagemId: Long) {
        if (mensagemId <= 0) return
        _selecionadas.update { if (mensagemId in it) it - mensagemId else it + mensagemId }
    }

    fun limparSelecao() {
        _selecionadas.value = emptySet()
    }

    /** "Pôr na pasta": as selecionadas vão para [pasta] e a seleção acaba. */
    fun porSelecionadas(pasta: Pasta) {
        val ids = _selecionadas.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repositorio.por(pasta, ids)
                .onSuccess { limparSelecao() }
                .onFailure { falhar(AcaoPasta.POR, it) }
        }
    }

    // --- Pasta aberta ---

    fun abrir(pasta: Pasta) {
        limparSelecao()
        abertaNaLista = pastas.value.any { it.id == pasta.id }
        _aberta.value = PastaAbertaUi(pasta)
        viewModelScope.launch { recarregarAberta() }
    }

    fun fecharPasta() {
        _aberta.value = null
    }

    /** Perto do topo da pasta: as 50 anteriores à mais antiga carregada. */
    fun carregarAnteriores() {
        val atual = _aberta.value ?: return
        val primeira = atual.mensagens.firstOrNull() ?: return
        if (atual.fim || atual.carregando) return
        _aberta.value = atual.copy(carregando = true)
        viewModelScope.launch {
            repositorio.mensagens(atual.pasta.id, antes = primeira.id)
                .onSuccess { anteriores ->
                    _aberta.update { agora ->
                        if (agora?.pasta?.id != atual.pasta.id) return@update agora
                        val ja = agora.mensagens.map { it.id }.toSet()
                        agora.copy(
                            mensagens = anteriores.filter { it.id !in ja } + agora.mensagens,
                            carregando = false,
                            fim = anteriores.size < MENSAGENS_POR_PAGINA_PASTA,
                        )
                    }
                }
                .onFailure {
                    _aberta.update { agora -> agora?.copy(carregando = false) }
                    falhar(AcaoPasta.CARREGAR, it)
                }
        }
    }

    private suspend fun recarregarAberta() {
        val atual = _aberta.value ?: return
        repositorio.mensagens(atual.pasta.id)
            .onSuccess { mensagens ->
                _aberta.update { agora ->
                    if (agora?.pasta?.id != atual.pasta.id) return@update agora
                    agora.copy(
                        mensagens = mensagens,
                        carregando = false,
                        fim = mensagens.size < MENSAGENS_POR_PAGINA_PASTA,
                        selecionadas = agora.selecionadas.filterTo(mutableSetOf()) { id -> mensagens.any { it.id == id } },
                    )
                }
            }
            .onFailure {
                _aberta.update { agora -> agora?.copy(carregando = false) }
                falhar(AcaoPasta.CARREGAR, it)
            }
    }

    fun alternarSelecaoNaPasta(mensagemId: Long) {
        _aberta.update { atual ->
            atual?.copy(
                selecionadas = if (mensagemId in
                    atual.selecionadas
                ) {
                    atual.selecionadas - mensagemId
                } else {
                    atual.selecionadas + mensagemId
                },
            )
        }
    }

    fun limparSelecaoNaPasta() {
        _aberta.update { it?.copy(selecionadas = emptySet()) }
    }

    /** "Tirar da pasta": uma mensagem ou as selecionadas na pasta aberta. Some da lista na hora. */
    fun tirar(mensagens: Collection<Long>) {
        val atual = _aberta.value ?: return
        if (mensagens.isEmpty()) return
        viewModelScope.launch {
            repositorio.tirar(atual.pasta, mensagens.toList())
                .onSuccess {
                    _aberta.update { agora ->
                        agora?.copy(
                            mensagens = agora.mensagens.filter { it.id !in mensagens },
                            selecionadas =
                            agora.selecionadas - mensagens.toSet(),
                        )
                    }
                }
                .onFailure { falhar(AcaoPasta.TIRAR, it) }
        }
    }

    private fun falhar(acao: AcaoPasta, erro: Throwable) {
        _falhas.trySend(FalhaPasta(acao, erro.paraErroApi().mensagemAmigavel()))
    }
}
