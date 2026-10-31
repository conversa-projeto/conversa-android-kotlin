package com.conversa.app.feature.config

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.registro.EntradaDoRegistro
import com.conversa.app.core.registro.Registro
import com.conversa.app.core.registro.agruparEntradas
import com.conversa.app.core.ui.componentes.DialogoConfirmacao
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** A tela do registro: as últimas entradas (a mais nova em cima), o período guardado e o tamanho. */
data class RegistroUiState(
    val carregando: Boolean = true,
    val entradas: List<EntradaDoRegistro> = emptyList(),
    val desde: LocalDate? = null,
    val tamanho: Long = 0,
    val soProblemas: Boolean = false,
    val preparando: Boolean = false,
)

sealed interface EventoRegistro {
    /** O zip está pronto em [arquivo]: a tela abre o "Compartilhar" do Android. */
    data class Compartilhar(val arquivo: File) : EventoRegistro

    data object Falhou : EventoRegistro
}

@HiltViewModel
class RegistroViewModel @Inject constructor(private val registro: Registro, @ApplicationContext private val contexto: Context) :
    ViewModel() {
    private val _estado = MutableStateFlow(RegistroUiState())
    val estado: StateFlow<RegistroUiState> = _estado.asStateFlow()

    val eventos = EventosUnicos<EventoRegistro>()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            val (entradas, lista) = withContext(Dispatchers.IO) {
                agruparEntradas(registro.arquivos.ultimasLinhas(MAXIMO_LINHAS)).takeLast(MAXIMO_ENTRADAS).asReversed() to
                    registro.arquivos.lista()
            }
            _estado.update {
                it.copy(
                    carregando = false,
                    entradas = entradas,
                    desde = lista.firstOrNull()?.dia,
                    tamanho = lista.sumOf { a -> a.arquivo.length() },
                )
            }
        }
    }

    fun alternarSoProblemas() = _estado.update { it.copy(soProblemas = !it.soProblemas) }

    /** Junta os arquivos num zip no cache (`cache/registro/`, compartilhado pelo FileProvider do app). */
    fun compartilhar() {
        if (_estado.value.preparando) return
        _estado.update { it.copy(preparando = true) }
        viewModelScope.launch {
            Timber.tag(Registro.TAG).i("A pessoa pediu para compartilhar o registro")
            val zip = withContext(Dispatchers.IO) {
                runCatching {
                    val pasta = File(contexto.cacheDir, Registro.PASTA)
                    pasta.listFiles().orEmpty().forEach { it.delete() }
                    val nome = "conversa-registro-${LocalDateTime.now().format(NOME_DO_ZIP)}.zip"
                    registro.arquivos.compactar(File(pasta, nome))
                }.onFailure { Timber.tag(Registro.TAG).w(it, "Não deu para compactar o registro") }.getOrNull()
            }
            _estado.update { it.copy(preparando = false) }
            eventos.enviar(if (zip != null) EventoRegistro.Compartilhar(zip) else EventoRegistro.Falhou)
        }
    }

    fun apagar() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { registro.apagarTudo() }
            carregar()
        }
    }

    companion object {
        /** O bastante para a tela; o arquivo compartilhado leva tudo. */
        private const val MAXIMO_LINHAS = 4_000
        const val MAXIMO_ENTRADAS = 1_500
        private val NOME_DO_ZIP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)
    }
}

/**
 * Registro do app: o que ficou guardado neste aparelho nos últimos 7 dias (`core:registro`). A pessoa
 * vê, filtra os avisos e erros, compartilha o zip com quem vai investigar ou apaga tudo.
 */
@Composable
fun RegistroRotaTela(aoVoltar: () -> Unit, viewModel: RegistroViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val textoFalhou = stringResource(R.string.registro_falhou)
    val assunto = stringResource(R.string.registro_compartilhar_assunto, LocalDate.now().toString())
    val tituloCompartilhar = stringResource(R.string.registro_compartilhar)
    var confirmarApagar by rememberSaveable { mutableStateOf(false) }
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoRegistro.Compartilhar ->
                if (!compartilharZip(contexto, evento.arquivo, assunto, tituloCompartilhar)) avisos.mostrarErro(textoFalhou)
            EventoRegistro.Falhou -> avisos.mostrarErro(textoFalhou)
        }
    }
    SubTela(
        titulo = stringResource(R.string.registro_titulo),
        aoVoltar = aoVoltar,
        acoes = {
            IconButton(onClick = viewModel::carregar) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.registro_atualizar))
            }
            IconButton(onClick = viewModel::compartilhar, enabled = !estado.preparando && estado.entradas.isNotEmpty()) {
                Icon(Icons.Outlined.Share, contentDescription = tituloCompartilhar)
            }
            IconButton(onClick = { confirmarApagar = true }, enabled = estado.entradas.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.registro_apagar))
            }
        },
    ) { modificador ->
        RegistroConteudo(estado, aoAlternarSoProblemas = viewModel::alternarSoProblemas, modifier = modificador)
    }
    if (confirmarApagar) {
        DialogoConfirmacao(
            titulo = stringResource(R.string.registro_apagar),
            mensagem = stringResource(R.string.registro_apagar_confirmar),
            textoConfirmar = stringResource(R.string.registro_apagar_botao),
            perigo = true,
            aoConfirmar = {
                confirmarApagar = false
                viewModel.apagar()
            },
            aoCancelar = { confirmarApagar = false },
        )
    }
}

@Composable
internal fun RegistroConteudo(estado: RegistroUiState, aoAlternarSoProblemas: () -> Unit, modifier: Modifier = Modifier) {
    val visiveis = remember(estado.entradas, estado.soProblemas) {
        if (estado.soProblemas) estado.entradas.filter { it.nivel in PROBLEMAS } else estado.entradas
    }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.registro_explicacao),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                estado.desde?.let { desde ->
                    Text(
                        stringResource(R.string.registro_desde, desde.format(DIA), tamanhoLegivel(estado.tamanho)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilterChip(
                    selected = estado.soProblemas,
                    onClick = aoAlternarSoProblemas,
                    label = { Text(stringResource(R.string.registro_so_problemas)) },
                )
                if (estado.preparando) {
                    Text(stringResource(R.string.registro_preparando), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (!estado.carregando && estado.entradas.isNotEmpty()) {
                    Text(
                        stringResource(R.string.registro_ultimas, RegistroViewModel.MAXIMO_ENTRADAS),
                        style = MaterialTheme.typography.bodySmall,
                        color = ConversaTema.cores.textoTerciario,
                    )
                }
            }
            HorizontalDivider()
        }
        if (!estado.carregando && visiveis.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.registro_vazio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(visiveis) { entrada -> LinhaDoRegistro(entrada) }
    }
}

/** Uma entrada: a faixa da cor do nível à esquerda e o texto em fonte fixa (dá para selecionar e copiar). */
@Composable
private fun LinhaDoRegistro(entrada: EntradaDoRegistro) {
    val faixa = when (entrada.nivel) {
        'E', 'A' -> MaterialTheme.colorScheme.error
        'W' -> ConversaTema.cores.avisoConexao
        'I' -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(faixa))
        SelectionContainer(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                "${momentoCurto(entrada.momento)} ${entrada.nivel} ${entrada.texto}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    HorizontalDivider(color = ConversaTema.cores.divisorLista)
}

private val PROBLEMAS = setOf('W', 'E', 'A')
private val DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

/** "2026-10-31 15:46:12.345 -03:00" → "31/10 15:46:12". */
internal fun momentoCurto(momento: String): String =
    if (momento.length < 19) momento else "${momento.substring(8, 10)}/${momento.substring(5, 7)} ${momento.substring(11, 19)}"

private fun tamanhoLegivel(bytes: Long): String = when {
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("pt-BR"), "%.0f kB", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024))
}

/** Abre o "Compartilhar" do Android com o zip (só leitura, pela permissão temporária do FileProvider). */
private fun compartilharZip(contexto: Context, arquivo: File, assunto: String, titulo: String): Boolean {
    val uri = runCatching { FileProvider.getUriForFile(contexto, contexto.packageName + ".arquivos", arquivo) }.getOrNull() ?: return false
    val envio = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, assunto)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        contexto.startActivity(Intent.createChooser(envio, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
