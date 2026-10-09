package com.conversa.app.feature.pesquisa

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PesquisaUiState(
    /** O termo da última pesquisa (o destaque nos trechos). */
    val termo: String = "",
    val buscando: Boolean = false,
    /** Nulo enquanto não pesquisou; vazio = "Nenhum resultado encontrado.". */
    val resultados: List<Mensagem>? = null,
    val erro: String? = null,
    /** Nome de cada conversa, para os títulos dos grupos de resultados. */
    val titulos: Map<Long, String> = emptyMap(),
)

/**
 * Pesquisa em todos os chats (8.2, PES-02, como o `PesquisaAvancada.vue`): `GET /pesquisar`
 * com `conversa = 0`; os resultados ficam agrupados por conversa na tela. Abre já pesquisando
 * o termo que vinha no campo da lista de conversas (se houver).
 */
@HiltViewModel
class PesquisaViewModel @Inject constructor(
    salvo: SavedStateHandle,
    conversas: ConversasRepositorio,
    private val mensagens: MensagensRepositorio,
) : ViewModel() {
    private val busca = MutableStateFlow(PesquisaUiState())
    private var tarefa: Job? = null

    /** O termo com que a tela abriu (o campo começa com ele). */
    val termoInicial: String = salvo.get<String>("termo").orEmpty()

    val estado: StateFlow<PesquisaUiState> = combine(
        busca,
        conversas.observarTodas().map { lista ->
            lista.associate { it.id to it.titulo }
        },
    ) { b, titulos ->
        b.copy(titulos = titulos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PesquisaUiState())

    init {
        if (termoInicial.isNotBlank()) pesquisar(termoInicial)
    }

    /** Uma pesquisa nova cancela a anterior. */
    fun pesquisar(termo: String) {
        val limpo = termo.trim()
        if (limpo.isEmpty()) return
        tarefa?.cancel()
        busca.update { it.copy(termo = limpo, buscando = true, erro = null) }
        tarefa = viewModelScope.launch {
            mensagens.pesquisar(limpo)
                .onSuccess { lista -> busca.update { it.copy(buscando = false, resultados = lista) } }
                .onFailure { falha ->
                    busca.update { it.copy(buscando = false, resultados = emptyList(), erro = falha.paraErroApi().mensagemAmigavel()) }
                }
        }
    }
}
