package com.conversa.app.feature.atividades

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.atividades.AtividadesRepositorio
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AtividadesUiState(
    val atividades: List<Atividade> = emptyList(),
    /** Primeira carga sem nada no aparelho ainda. */
    val carregando: Boolean = true,
    val carregandoMais: Boolean = false,
    val fim: Boolean = false,
    val erro: String? = null,
)

private data class Carga(val carregando: Boolean = true, val mais: Boolean = false, val fim: Boolean = false, val erro: String? = null)

/**
 * Aba Atividades (8.1, FC-800): a lista vem do Room (o repositório relê do servidor ao abrir
 * a aba e marca como vistas); perto do fim, as anteriores.
 */
@HiltViewModel
class AtividadesViewModel @Inject constructor(private val atividades: AtividadesRepositorio) : ViewModel() {
    private val carga = MutableStateFlow(Carga())

    val estado: StateFlow<AtividadesUiState> = combine(atividades.observar(), carga) { lista, c ->
        AtividadesUiState(lista, carregando = c.carregando && lista.isEmpty(), carregandoMais = c.mais, fim = c.fim, erro = c.erro)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AtividadesUiState())

    /** A aba apareceu (ou o app voltou com ela na tela). */
    fun abrir() {
        viewModelScope.launch {
            carga.value = carga.value.copy(carregando = true, erro = null)
            atividades.abrir()
                .onSuccess { fim -> carga.value = Carga(carregando = false, fim = fim) }
                .onFailure { carga.value = carga.value.copy(carregando = false, erro = it.paraErroApi().mensagemAmigavel()) }
        }
    }

    /** A aba saiu da tela: o que chegar volta a contar no badge. */
    fun fechar() = atividades.fechar()

    /** Perto do fim da lista: as anteriores à última. */
    fun carregarMais() {
        val atual = carga.value
        val ultima = estado.value.atividades.lastOrNull() ?: return
        if (atual.fim || atual.mais || atual.carregando) return
        carga.value = atual.copy(mais = true)
        viewModelScope.launch {
            atividades.carregarMais(ultima.id)
                .onSuccess { fim -> carga.value = carga.value.copy(mais = false, fim = fim) }
                .onFailure { carga.value = carga.value.copy(mais = false) }
        }
    }
}
