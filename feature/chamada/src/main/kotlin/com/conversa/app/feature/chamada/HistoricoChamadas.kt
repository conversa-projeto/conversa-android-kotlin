package com.conversa.app.feature.chamada

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.chamadas.ChamadasRepositorio
import com.conversa.app.core.model.ChamadaHistorico
import com.conversa.app.core.model.DiaHistorico
import com.conversa.app.core.model.StatusChamada
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.agruparHistorico
import com.conversa.app.core.model.filtrarHistorico
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoErro
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoricoUiState(
    val carregando: Boolean = true,
    val erro: String? = null,
    /** A mais recente primeiro, como o servidor manda. */
    val chamadas: List<ChamadaHistorico> = emptyList(),
    val soPerdidas: Boolean = false,
    val de: LocalDate? = null,
    val ate: LocalDate? = null,
)

/** Histórico de chamadas (TODO 6.10, CHA-22): `GET /chamadas` com período opcional. */
@HiltViewModel
class HistoricoChamadasViewModel @Inject constructor(
    private val chamadas: ChamadasRepositorio,
    gerenciador: GerenciadorChamadas,
    sessao: SessaoRepositorio,
    private val relogio: Clock,
) : ViewModel() {
    private val _estado = MutableStateFlow(HistoricoUiState())
    val estado: StateFlow<HistoricoUiState> = _estado.asStateFlow()
    val eu: Long = sessao.sessao.value?.usuarioId ?: 0
    private var carga: Job? = null

    init {
        // Acabou uma chamada: ela entra no histórico (o servidor finaliza logo depois).
        gerenciador.estado
            .map { it.fase == FaseChamada.INATIVO }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .onEach {
                delay(ESPERA_FINALIZAR_MS)
                carregar()
            }
            .launchIn(viewModelScope)
    }

    fun hoje(): LocalDate = LocalDate.now(relogio)

    fun carregar() {
        carga?.cancel()
        carga = viewModelScope.launch {
            _estado.update { it.copy(carregando = true, erro = null) }
            val atual = _estado.value
            chamadas.historico(atual.de, atual.ate)
                .onSuccess { lista -> _estado.update { it.copy(carregando = false, chamadas = lista) } }
                .onFailure { erro ->
                    val texto = (erro as? ErroApi)?.mensagemAmigavel() ?: erro.message.orEmpty()
                    _estado.update { it.copy(carregando = false, erro = texto) }
                }
        }
    }

    fun soPerdidas(sim: Boolean) = _estado.update { it.copy(soPerdidas = sim) }

    fun periodo(de: LocalDate?, ate: LocalDate?) {
        _estado.update { it.copy(de = de, ate = ate) }
        carregar()
    }

    private companion object {
        const val ESPERA_FINALIZAR_MS = 1_500L
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoricoChamadasRota(
    aoAbrirConversa: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoricoChamadasViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Toda vez que a aba aparece, a lista vem de novo (como o web ao abrir).
    LaunchedEffect(Unit) { viewModel.carregar() }
    var busca by rememberSaveable { mutableStateOf("") }
    val ligarNovamente = rememberLigarNovamente()
    val eu = viewModel.eu
    val filtradas =
        remember(estado.chamadas, estado.soPerdidas, busca, eu) { filtrarHistorico(estado.chamadas, eu, estado.soPerdidas, busca) }
    val grupos = remember(filtradas) { agruparHistorico(filtradas, viewModel.hoje(), java.time.ZoneId.systemDefault()) }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Cabecalho(estado, busca, aoBuscar = { busca = it }, aoPerdidas = viewModel::soPerdidas, aoPeriodo = viewModel::periodo)
        when {
            estado.carregando && estado.chamadas.isEmpty() -> Carregando()
            estado.erro != null && estado.chamadas.isEmpty() -> EstadoErro(estado.erro.orEmpty(), aoTentarDeNovo = viewModel::carregar)
            filtradas.isEmpty() -> EstadoVazio(
                titulo = stringResource(if (estado.soPerdidas) R.string.nenhuma_chamada_perdida else R.string.nenhuma_chamada),
                icone = Icons.Outlined.Call,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                for (grupo in grupos) {
                    stickyHeader(key = "dia-${grupo.dia}") { TituloDoDia(grupo.dia) }
                    items(grupo.chamadas, key = { it.id }) { chamada ->
                        LinhaChamada(chamada, eu, aoAbrir = {
                            chamada.conversaId?.let(aoAbrirConversa)
                        }, aoLigar = { ligarNovamente(chamada) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho(
    estado: HistoricoUiState,
    busca: String,
    aoBuscar: (String) -> Unit,
    aoPerdidas: (Boolean) -> Unit,
    aoPeriodo: (LocalDate?, LocalDate?) -> Unit,
) {
    var escolhendoPeriodo by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chamadas), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !estado.soPerdidas, onClick = { aoPerdidas(false) }, label = { Text(stringResource(R.string.todas)) })
            FilterChip(
                selected = estado.soPerdidas,
                onClick = { aoPerdidas(true) },
                label = { Text(stringResource(R.string.perdidas)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ConversaTema.cores.chamadaPerdida),
            )
            if (estado.de != null || estado.ate != null) {
                InputChip(
                    selected = true,
                    onClick = { escolhendoPeriodo = true },
                    label = { Text(textoPeriodo(estado.de, estado.ate)) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.limpar_periodo),
                            modifier = Modifier.size(18.dp).clickable { aoPeriodo(null, null) },
                        )
                    },
                )
            } else {
                FilterChip(
                    selected = false,
                    onClick = { escolhendoPeriodo = true },
                    label = { Text(stringResource(R.string.periodo)) },
                    leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        OutlinedTextField(
            value = busca,
            onValueChange = aoBuscar,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.buscar_contato)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )
    }
    if (escolhendoPeriodo) {
        val seletor = rememberDateRangePickerState(
            initialSelectedStartDateMillis = estado.de?.let(::paraMillis),
            initialSelectedEndDateMillis = estado.ate?.let(::paraMillis),
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoPeriodo = false },
            confirmButton = {
                TextButton(onClick = {
                    escolhendoPeriodo = false
                    aoPeriodo(seletor.selectedStartDateMillis?.let(::paraData), seletor.selectedEndDateMillis?.let(::paraData))
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { escolhendoPeriodo = false }) { Text(stringResource(android.R.string.cancel)) } },
        ) {
            DateRangePicker(state = seletor, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TituloDoDia(dia: DiaHistorico) {
    Text(
        when (dia) {
            DiaHistorico.Hoje -> stringResource(R.string.hoje)
            DiaHistorico.Ontem -> stringResource(R.string.ontem)
            is DiaHistorico.Data -> FORMATO_DATA.format(dia.dia)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.surfaceContainer,
        ).padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Linha do histórico (como a do web): avatar, seta efetuada/recebida, nome, hora, tipo · duração ou status, e "Ligar novamente". */
@Composable
private fun LinhaChamada(chamada: ChamadaHistorico, eu: Long, aoAbrir: () -> Unit, aoLigar: () -> Unit) {
    val cores = ConversaTema.cores
    val outro = chamada.outro(eu)
    val perdida = chamada.perdida(eu)
    val nome = when {
        chamada.grupo -> stringResource(R.string.grupo_n, chamada.participantes.size)
        else -> outro?.nome?.takeIf { it.isNotBlank() } ?: stringResource(R.string.desconhecido)
    }
    val (seta, descricaoSeta) = when {
        perdida -> Icons.AutoMirrored.Filled.CallMissed to stringResource(R.string.status_perdida)
        chamada.efetuada(eu) -> Icons.AutoMirrored.Filled.CallMade to stringResource(R.string.efetuada)
        else -> Icons.AutoMirrored.Filled.CallReceived to stringResource(R.string.recebida)
    }
    val corSeta = when {
        perdida || chamada.status == StatusChamada.RECUSADA -> cores.chamadaPerdida
        chamada.status == StatusChamada.CANCELADA -> cores.textoTerciario
        chamada.efetuada(eu) -> cores.chamadaRealizada
        else -> cores.chamadaRecebida
    }
    val tipo = stringResource(if (chamada.tipo == TipoChamada.VIDEO) R.string.video else R.string.audio)
    val detalhe = when {
        chamada.duracao != null -> formatarDuracao(chamada.duracao ?: 0)
        perdida -> stringResource(R.string.status_perdida)
        chamada.status == StatusChamada.RECUSADA -> stringResource(R.string.status_recusada)
        chamada.status == StatusChamada.CANCELADA -> stringResource(R.string.status_cancelada)
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().clickable(
            enabled = chamada.conversaId != null,
            onClick = aoAbrir,
        ).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(if (chamada.grupo) nome else outro?.nome, if (chamada.grupo) null else outro?.avatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(seta, contentDescription = descricaoSeta, tint = corSeta, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    nome,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (perdida) cores.chamadaPerdida else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                chamada.criadoEm?.let {
                    Text(
                        FORMATO_HORA.format(it.atZone(java.time.ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (detalhe != null) stringResource(R.string.tipo_e_detalhe, tipo, detalhe) else tipo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = aoLigar) {
            Icon(
                if (chamada.tipo == TipoChamada.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call,
                contentDescription = stringResource(R.string.ligar_novamente),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun textoPeriodo(de: LocalDate?, ate: LocalDate?): String = when {
    de != null && ate != null -> stringResource(R.string.de_ate, FORMATO_CURTO.format(de), FORMATO_CURTO.format(ate))
    de != null -> stringResource(R.string.desde, FORMATO_CURTO.format(de))
    else -> stringResource(R.string.ate, FORMATO_CURTO.format(ate ?: LocalDate.now()))
}

/** O seletor de datas trabalha com meia-noite UTC. */
private fun paraMillis(dia: LocalDate): Long = dia.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun paraData(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_CURTO = DateTimeFormatter.ofPattern("dd/MM")
