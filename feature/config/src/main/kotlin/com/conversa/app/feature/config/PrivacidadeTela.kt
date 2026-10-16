package com.conversa.app.feature.config

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.CodigoPermissao
import com.conversa.app.core.model.Privacidade
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.componentes.Interruptor
import com.conversa.app.core.ui.componentes.LocalPermissoesDeUso
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PrivacidadeUiState(
    val carregando: Boolean = true,
    val privacidade: Privacidade = Privacidade(),
    val salvando: Boolean = false,
    /** O erro do servidor (ou da rede), como veio. */
    val erro: String? = null,
)

/** Uma das três chaves (a ordem e os textos do web, `ConfiguracaoPrivacidade.vue`). */
enum class OpcaoPrivacidade(@param:StringRes val titulo: Int, @param:StringRes val descricao: Int) {
    APARECER_OFFLINE(R.string.privacidade_offline, R.string.privacidade_offline_descricao),
    MOSTRAR_VISTO(R.string.privacidade_visto, R.string.privacidade_visto_descricao),
    MOSTRAR_NA_CONVERSA(R.string.privacidade_na_conversa, R.string.privacidade_na_conversa_descricao),
}

/**
 * Privacidade (FC-810, CFG-09): "Aparecer offline", "Mostrar o visto por último" e "Mostrar
 * quando estou na conversa", do servidor (`GET /usuario/privacidade`, `PATCH /usuario` só com a
 * chave que mudou). Muda na hora; se o servidor recusar, volta e mostra o erro.
 */
@HiltViewModel
class PrivacidadeViewModel @Inject constructor(private val perfil: PerfilRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(PrivacidadeUiState())
    val estado: StateFlow<PrivacidadeUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            perfil.privacidade()
                .onSuccess { atual -> _estado.update { it.copy(carregando = false, privacidade = atual) } }
                .onFailure { falha -> _estado.update { it.copy(carregando = false, erro = falha.paraErroApi().mensagemAmigavel()) } }
        }
    }

    fun alterar(opcao: OpcaoPrivacidade, valor: Boolean) {
        val atual = _estado.value
        if (atual.salvando || atual.carregando) return
        val anterior = atual.privacidade
        val nova = when (opcao) {
            OpcaoPrivacidade.APARECER_OFFLINE -> anterior.copy(aparecerOffline = valor)
            OpcaoPrivacidade.MOSTRAR_VISTO -> anterior.copy(mostrarVistoEm = valor)
            OpcaoPrivacidade.MOSTRAR_NA_CONVERSA -> anterior.copy(mostrarNaConversa = valor)
        }
        _estado.update { it.copy(privacidade = nova, salvando = true, erro = null) }
        viewModelScope.launch {
            perfil.alterarPrivacidade(
                aparecerOffline = valor.takeIf { opcao == OpcaoPrivacidade.APARECER_OFFLINE },
                mostrarVistoEm = valor.takeIf { opcao == OpcaoPrivacidade.MOSTRAR_VISTO },
                mostrarNaConversa = valor.takeIf { opcao == OpcaoPrivacidade.MOSTRAR_NA_CONVERSA },
            )
                .onSuccess { _estado.update { it.copy(salvando = false) } }
                .onFailure { falha ->
                    _estado.update { it.copy(privacidade = anterior, salvando = false, erro = falha.paraErroApi().mensagemAmigavel()) }
                }
        }
    }
}

@Composable
fun PrivacidadeRotaTela(aoVoltar: () -> Unit, viewModel: PrivacidadeViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    SubTela(titulo = stringResource(R.string.privacidade_titulo), aoVoltar = aoVoltar) { modificador ->
        Privacidade(estado, viewModel::alterar, modificador)
    }
}

@Composable
private fun Privacidade(estado: PrivacidadeUiState, aoAlterar: (OpcaoPrivacidade, Boolean) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        if (estado.carregando) {
            Text(
                stringResource(R.string.privacidade_carregando),
                style = MaterialTheme.typography.bodyMedium,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        Text(
            stringResource(R.string.privacidade_status),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Text(
            stringResource(R.string.privacidade_status_texto),
            style = MaterialTheme.typography.bodySmall,
            color = ConversaTema.cores.textoTerciario,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // Sem a permissão, vê mas não muda (GER-09, como o web).
        val podeMudar = LocalPermissoesDeUso.current.pode(CodigoPermissao.PRIVACIDADE)
        if (!podeMudar) {
            Text(
                stringResource(R.string.privacidade_sem_permissao),
                style = MaterialTheme.typography.bodyMedium,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val privacidade = estado.privacidade
        OpcaoPrivacidade.entries.forEach { opcao ->
            val ligado = when (opcao) {
                OpcaoPrivacidade.APARECER_OFFLINE -> privacidade.aparecerOffline
                OpcaoPrivacidade.MOSTRAR_VISTO -> privacidade.mostrarVistoEm
                OpcaoPrivacidade.MOSTRAR_NA_CONVERSA -> privacidade.mostrarNaConversa
            }
            // Aparecendo offline, as outras duas não valem: ficam desabilitadas (como o web).
            val habilitada = podeMudar && !estado.salvando && (opcao == OpcaoPrivacidade.APARECER_OFFLINE || !privacidade.aparecerOffline)
            ListItem(
                headlineContent = { Text(stringResource(opcao.titulo)) },
                supportingContent = { Text(stringResource(opcao.descricao), color = ConversaTema.cores.textoTerciario) },
                trailingContent = { Interruptor(ligado) },
                modifier = Modifier
                    .alpha(if (habilitada || estado.salvando) 1f else 0.5f)
                    .toggleable(value = ligado, enabled = habilitada, role = Role.Switch) { aoAlterar(opcao, it) },
            )
        }
        estado.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
    }
}
