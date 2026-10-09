package com.conversa.app.feature.config

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.model.BandaVideo
import com.conversa.app.core.model.ConfigChamada
import com.conversa.app.core.model.QUADROS_POR_SEGUNDO
import com.conversa.app.core.model.QualidadeAudio
import com.conversa.app.core.model.ResolucaoVideo
import com.conversa.app.core.ui.componentes.Interruptor
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Qualidade das chamadas (8.5, FC-807, CFG-06): guardada no aparelho, vale a partir da próxima chamada. */
@HiltViewModel
class QualidadeChamadasViewModel @Inject constructor(private val preferencias: PreferenciasRepositorio) : ViewModel() {
    val config: StateFlow<ConfigChamada> = preferencias.chamada
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigChamada.PADRAO)

    fun alterar(mudanca: (ConfigChamada) -> ConfigChamada) {
        viewModelScope.launch { preferencias.alterarChamada(mudanca(config.value)) }
    }

    fun restaurarPadrao() {
        viewModelScope.launch { preferencias.alterarChamada(ConfigChamada.PADRAO) }
    }
}

@Composable
fun QualidadeChamadasRotaTela(aoVoltar: () -> Unit, viewModel: QualidadeChamadasViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    SubTela(
        titulo = stringResource(R.string.chamadas_titulo),
        aoVoltar = aoVoltar,
        acoes = { TextButton(onClick = viewModel::restaurarPadrao) { Text(stringResource(R.string.chamadas_restaurar)) } },
    ) { modificador ->
        QualidadeChamadas(config, viewModel::alterar, modificador)
    }
}

/** As opções do web ("Áudio" e "Vídeo da câmera"); o compartilhamento de tela vem com a etapa 9. */
@Composable
private fun QualidadeChamadas(config: ConfigChamada, aoAlterar: ((ConfigChamada) -> ConfigChamada) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.chamadas_texto),
            style = MaterialTheme.typography.bodyMedium,
            color = ConversaTema.cores.textoTerciario,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Secao(R.string.chamadas_audio)
        LinhaInterruptor(R.string.chamadas_ruido, R.string.chamadas_ruido_texto, config.reducaoRuido) { v ->
            aoAlterar { it.copy(reducaoRuido = v) }
        }
        LinhaInterruptor(R.string.chamadas_eco, R.string.chamadas_eco_texto, config.cancelamentoEco) { v ->
            aoAlterar { it.copy(cancelamentoEco = v) }
        }
        LinhaInterruptor(R.string.chamadas_ganho, R.string.chamadas_ganho_texto, config.ganhoAutomatico) { v ->
            aoAlterar { it.copy(ganhoAutomatico = v) }
        }
        Escolha(
            titulo = R.string.chamadas_qualidade_audio,
            opcoes = QualidadeAudio.entries,
            atual = config.qualidadeAudio,
            rotulo = { stringResource(it.rotulo()) },
            descricao = stringResource(config.qualidadeAudio.descricao()),
            aoEscolher = { q -> aoAlterar { it.copy(qualidadeAudio = q) } },
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Secao(R.string.chamadas_video)
        Escolha(
            titulo = R.string.chamadas_resolucao,
            opcoes = ResolucaoVideo.entries,
            atual = config.resolucao,
            rotulo = { "${it.chave}p" },
            aoEscolher = { r -> aoAlterar { it.copy(resolucao = r) } },
        )
        Escolha(
            titulo = R.string.chamadas_quadros,
            opcoes = QUADROS_POR_SEGUNDO,
            atual = config.quadros,
            rotulo = { it.toString() },
            aoEscolher = { q -> aoAlterar { it.copy(quadros = q) } },
        )
        Escolha(
            titulo = R.string.chamadas_banda,
            opcoes = BandaVideo.entries,
            atual = config.banda,
            rotulo = { stringResource(it.rotulo()) },
            descricao = stringResource(config.banda.descricao()),
            aoEscolher = { b -> aoAlterar { it.copy(banda = b) } },
        )
    }
}

@Composable
private fun Secao(@StringRes titulo: Int) {
    Text(
        stringResource(titulo),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LinhaInterruptor(@StringRes titulo: Int, @StringRes texto: Int, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = ligado, role = Role.Switch, onValueChange = aoMudar)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(texto), style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario)
        }
        Interruptor(ligado)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Escolha(
    @StringRes titulo: Int,
    opcoes: List<T>,
    atual: T,
    rotulo: @Composable (T) -> String,
    aoEscolher: (T) -> Unit,
    descricao: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(titulo), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opcoes.forEachIndexed { i, opcao ->
                SegmentedButton(
                    selected = opcao == atual,
                    onClick = { aoEscolher(opcao) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = opcoes.size),
                ) { Text(rotulo(opcao)) }
            }
        }
        descricao?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario) }
    }
}

private fun QualidadeAudio.rotulo(): Int = when (this) {
    QualidadeAudio.NORMAL -> R.string.chamadas_audio_normal
    QualidadeAudio.ALTA -> R.string.chamadas_audio_alta
    QualidadeAudio.MUSICA -> R.string.chamadas_audio_musica
}

private fun QualidadeAudio.descricao(): Int = when (this) {
    QualidadeAudio.NORMAL -> R.string.chamadas_audio_normal_texto
    QualidadeAudio.ALTA -> R.string.chamadas_audio_alta_texto
    QualidadeAudio.MUSICA -> R.string.chamadas_audio_musica_texto
}

private fun BandaVideo.rotulo(): Int = when (this) {
    BandaVideo.AUTOMATICA -> R.string.chamadas_banda_auto
    BandaVideo.ECONOMICA -> R.string.chamadas_banda_economica
    BandaVideo.ALTA -> R.string.chamadas_banda_alta
}

private fun BandaVideo.descricao(): Int = when (this) {
    BandaVideo.AUTOMATICA -> R.string.chamadas_banda_auto_texto
    BandaVideo.ECONOMICA -> R.string.chamadas_banda_economica_texto
    BandaVideo.ALTA -> R.string.chamadas_banda_alta_texto
}
