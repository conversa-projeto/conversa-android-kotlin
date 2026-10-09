package com.conversa.app.feature.pesquisa

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.agruparPorConversa
import com.conversa.app.core.ui.R as UiR
import com.conversa.app.core.ui.componentes.LinhaResultadoPesquisa
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * Pesquisa em todos os chats (8.2, PES-02), como o `PesquisaAvancada.vue`: o campo com o foco,
 * "Pesquisando…", "Nenhum resultado encontrado." e os resultados agrupados por conversa (nome e
 * quantidade), cada um com quem mandou, a data e o trecho com o termo destacado. O toque abre a
 * conversa na mensagem.
 */
@Composable
fun PesquisaRotaTela(
    aoAbrirMensagem: (conversaId: Long, mensagemId: Long) -> Unit,
    aoVoltar: () -> Unit,
    viewModel: PesquisaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    PesquisaTela(
        estado = estado,
        termoInicial = viewModel.termoInicial,
        aoPesquisar = viewModel::pesquisar,
        aoAbrir = { aoAbrirMensagem(it.conversaId, it.id) },
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PesquisaTela(
    estado: PesquisaUiState,
    termoInicial: String,
    aoPesquisar: (String) -> Unit,
    aoAbrir: (Mensagem) -> Unit,
    aoVoltar: () -> Unit,
) {
    var termo by rememberSaveable { mutableStateOf(termoInicial) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancelar))
                    }
                },
                title = {
                    TextField(
                        value = termo,
                        onValueChange = { termo = it },
                        placeholder = { Text(stringResource(R.string.pesquisa_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { aoPesquisar(termo) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(foco),
                    )
                },
                actions = {
                    IconButton(onClick = { aoPesquisar(termo) }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.acao_pesquisar))
                    }
                },
            )
        },
    ) { margens ->
        val resultados = estado.resultados
        Column(Modifier.fillMaxSize().padding(margens)) {
            when {
                estado.buscando -> Aviso(stringResource(UiR.string.pesquisa_pesquisando))
                resultados == null -> Unit
                resultados.isEmpty() -> Aviso(estado.erro ?: stringResource(UiR.string.pesquisa_nenhum_resultado))
                else -> Resultados(resultados, estado, aoAbrir)
            }
        }
    }
}

@Composable
private fun Resultados(resultados: List<Mensagem>, estado: PesquisaUiState, aoAbrir: (Mensagem) -> Unit) {
    val grupos = remember(resultados) { agruparPorConversa(resultados) }
    LazyColumn(Modifier.fillMaxSize()) {
        grupos.forEach { (conversaId, mensagens) ->
            stickyHeader(key = "conversa-$conversaId") {
                CabecalhoConversa(
                    estado.titulos[conversaId] ?: stringResource(R.string.conversa_numero, conversaId.toInt()),
                    mensagens.size,
                )
            }
            items(mensagens, key = { it.id }) { mensagem ->
                LinhaResultadoPesquisa(mensagem, estado.termo, aoAbrir = { aoAbrir(mensagem) })
                HorizontalDivider(color = ConversaTema.cores.divisorLista)
            }
        }
    }
}

/** O nome da conversa e quantos resultados há nela. */
@Composable
private fun CabecalhoConversa(titulo: String, quantos: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "$quantos",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ConversaTema.cores.divisorLista)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = ConversaTema.cores.textoTerciario, modifier = Modifier.padding(24.dp))
}
