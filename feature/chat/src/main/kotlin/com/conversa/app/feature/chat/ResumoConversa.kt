package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ia.ResumosRepositorio
import com.conversa.app.core.data.sistema.RecursosRepositorio
import com.conversa.app.core.model.AssuntoResumo
import com.conversa.app.core.model.PeriodoResumo
import com.conversa.app.core.model.ResumoSalvo
import com.conversa.app.core.model.StatusResumo
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A folha do resumo (CON-14): o período escolhido, o último resumo guardado e o erro do pedido. */
data class ResumoUiState(
    val periodo: PeriodoResumo = PeriodoResumo.HORAS_24,
    val salvo: ResumoSalvo? = null,
    val pedindo: Boolean = false,
    val erroAoPedir: String? = null,
) {
    val processando: Boolean get() = pedindo || salvo?.resumo?.status == StatusResumo.PROCESSANDO
}

/**
 * "Resumir conversa" (CON-14, como o `ResumoConversaModal` do web). Só com a IA ligada no
 * servidor ([disponivel], GER-08). Com a folha aberta, consulta o pedido a cada 2 s até ficar
 * pronto; ao reabrir, o último resumo volta com o período usado e, se estava em andamento, a
 * consulta continua.
 */
@HiltViewModel
class ResumoViewModel @Inject constructor(
    salvo: SavedStateHandle,
    private val resumos: ResumosRepositorio,
    recursos: RecursosRepositorio,
) : ViewModel() {
    private val conversaId: Long = checkNotNull(salvo["conversaId"])

    val disponivel: StateFlow<Boolean> = recursos.recursos.map { it?.ia == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _estado = MutableStateFlow(ResumoUiState())
    val estado: StateFlow<ResumoUiState> = _estado.asStateFlow()

    private var aberta = false
    private var escolheuPeriodo = false
    private var consulta: Job? = null

    init {
        viewModelScope.launch {
            resumos.observar(conversaId).collect { guardado ->
                _estado.update { atual ->
                    atual.copy(
                        salvo = guardado,
                        periodo = if (!escolheuPeriodo && guardado != null) guardado.resumo.periodo else atual.periodo,
                    )
                }
                if (aberta) acompanhar()
            }
        }
    }

    fun abrir() {
        aberta = true
        acompanhar()
    }

    fun fechar() {
        aberta = false
        consulta?.cancel()
        consulta = null
    }

    fun escolher(periodo: PeriodoResumo) {
        escolheuPeriodo = true
        _estado.update { it.copy(periodo = periodo) }
    }

    fun resumir() {
        if (_estado.value.processando) return
        consulta?.cancel()
        viewModelScope.launch {
            _estado.update { it.copy(pedindo = true, erroAoPedir = null) }
            resumos.pedir(conversaId, _estado.value.periodo)
                .onSuccess { _estado.update { it.copy(pedindo = false) } }
                .onFailure { erro -> _estado.update { it.copy(pedindo = false, erroAoPedir = erro.paraErroApi().mensagemAmigavel()) } }
            acompanhar()
        }
    }

    /** Enquanto o pedido estiver em andamento (e a folha aberta), consulta a cada 2 s. */
    private fun acompanhar() {
        if (!aberta || consulta?.isActive == true) return
        if (_estado.value.salvo?.resumo?.status != StatusResumo.PROCESSANDO) return
        consulta = viewModelScope.launch {
            while (isActive) {
                delay(INTERVALO_CONSULTA_MS)
                val atual = _estado.value.salvo ?: break
                if (atual.resumo.status != StatusResumo.PROCESSANDO) break
                resumos.consultar(atual)
            }
        }
    }

    companion object {
        const val INTERVALO_CONSULTA_MS = 2_000L
    }
}

/** O nome de cada período, como as pílulas do web. */
internal fun nomeDoPeriodo(periodo: PeriodoResumo): Int = when (periodo) {
    PeriodoResumo.HORAS_24 -> R.string.resumo_periodo_24h
    PeriodoResumo.DIAS_7 -> R.string.resumo_periodo_7d
    PeriodoResumo.DIAS_30 -> R.string.resumo_periodo_30d
    PeriodoResumo.RECENTES -> R.string.resumo_periodo_recentes
}

/** A folha "Resumo da conversa"; "Ver na conversa" fecha e vai à primeira mensagem do assunto. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FolhaResumo(viewModel: ResumoViewModel, aoIrParaMensagem: (Long) -> Unit, aoFechar: () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.abrir()
        onDispose { viewModel.fechar() }
    }
    val resumo = estado.salvo?.resumo
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.resumo_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fechar)) }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PeriodoResumo.entries.forEach { periodo ->
                        FilterChip(
                            selected = estado.periodo == periodo,
                            onClick = { viewModel.escolher(periodo) },
                            enabled = !estado.processando,
                            label = { Text(stringResource(nomeDoPeriodo(periodo))) },
                        )
                    }
                }
            }
            item {
                Button(onClick = viewModel::resumir, enabled = !estado.processando, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (resumo != null && !estado.processando) R.string.resumir_de_novo else R.string.resumir))
                }
            }
            val erro = estado.erroAoPedir ?: resumo?.erro?.takeIf { resumo.status == StatusResumo.ERRO }
            when {
                estado.processando -> item { Processando(resumo?.mensagens) }
                erro != null -> item { CaixaDeErro(erro) }
                resumo == null -> item { Explicacao(stringResource(R.string.resumo_escolha_o_periodo)) }
                resumo.assuntos.isEmpty() -> item { Explicacao(stringResource(R.string.resumo_sem_mensagens)) }
                else -> {
                    item {
                        val salvo = estado.salvo!!
                        val quando = salvo.geradoEm.atZone(ZoneId.systemDefault())
                        Text(
                            pluralStringResource(
                                R.plurals.resumo_gerado_em,
                                resumo.mensagens,
                                quando.format(DIA),
                                quando.format(HORA),
                                stringResource(nomeDoPeriodo(resumo.periodo)),
                                resumo.mensagens,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = ConversaTema.cores.textoTerciario,
                        )
                    }
                    items(resumo.assuntos) { assunto -> CartaoAssunto(assunto, aoIrParaMensagem) }
                    item {
                        Text(
                            pluralStringResource(R.plurals.resumo_rodape, resumo.mensagens, resumo.mensagens),
                            style = MaterialTheme.typography.labelSmall,
                            color = ConversaTema.cores.textoTerciario,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun Processando(mensagens: Int?) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            if (mensagens != null && mensagens > 0) {
                pluralStringResource(R.plurals.resumindo_mensagens, mensagens, mensagens)
            } else {
                stringResource(R.string.resumindo)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.resumo_pode_demorar),
            style = MaterialTheme.typography.bodySmall,
            color = ConversaTema.cores.textoTerciario,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CaixaDeErro(texto: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(
            texto,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Explicacao(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = ConversaTema.cores.textoTerciario,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun CartaoAssunto(assunto: AssuntoResumo, aoIrParaMensagem: (Long) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(assunto.titulo, style = MaterialTheme.typography.titleSmall)
            if (assunto.resumo.isNotBlank()) Text(assunto.resumo, style = MaterialTheme.typography.bodyMedium)
            assunto.pendencias.forEach { pendencia ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    // A bolinha âmbar das pendências (a mesma cor da presença "ausente", proposta do cores.md §9).
                    Box(Modifier.padding(top = 6.dp).size(6.dp).background(ConversaTema.cores.ausente, CircleShape))
                    Text(pendencia, style = MaterialTheme.typography.bodySmall)
                }
            }
            assunto.mensagens.firstOrNull()?.let { primeira ->
                TextButton(onClick = { aoIrParaMensagem(primeira) }) {
                    Text(pluralStringResource(R.plurals.ver_na_conversa, assunto.mensagens.size, assunto.mensagens.size))
                }
            }
        }
    }
}

private val DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
