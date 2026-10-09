package com.conversa.app.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.ui.R as UiR
import com.conversa.app.core.ui.componentes.LinhaResultadoPesquisa
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * Cabeçalho em modo pesquisa (8.2, PES-01, como o `ChatHeader.vue`): o campo "Pesquisar nesta
 * conversa" com o foco; "Pesquisar" no teclado (ou a lupa) busca; voltar fecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarraPesquisa(acoes: AcoesChat) {
    var termo by rememberSaveable { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    BackHandler(onBack = acoes.aoFecharPesquisa)
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = acoes.aoFecharPesquisa) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fechar_pesquisa))
            }
        },
        title = {
            TextField(
                value = termo,
                onValueChange = { termo = it },
                placeholder = { Text(stringResource(R.string.pesquisar_nesta_conversa)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { acoes.aoPesquisar(termo) }),
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
            IconButton(onClick = { acoes.aoPesquisar(termo) }) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.acao_pesquisar))
            }
        },
    )
}

/**
 * Os resultados, por cima da conversa (mais recentes primeiro): quem mandou, a data e o trecho
 * com o termo destacado. O toque vai até a mensagem.
 */
@Composable
internal fun ResultadosPesquisa(pesquisa: PesquisaNaConversa, aoAbrir: (Mensagem) -> Unit, modifier: Modifier = Modifier) {
    val resultados = pesquisa.resultados
    if (!pesquisa.buscando && resultados == null) return
    Surface(modifier.fillMaxWidth().heightIn(max = 360.dp), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        when {
            pesquisa.buscando -> Aviso(stringResource(UiR.string.pesquisa_pesquisando))
            resultados.isNullOrEmpty() -> Aviso(pesquisa.erro ?: stringResource(UiR.string.pesquisa_nenhum_resultado))
            else -> LazyColumn {
                items(resultados, key = { it.id }) { mensagem ->
                    LinhaResultadoPesquisa(mensagem, pesquisa.termo, aoAbrir = { aoAbrir(mensagem) })
                    HorizontalDivider(color = ConversaTema.cores.divisorLista)
                }
            }
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = ConversaTema.cores.textoTerciario,
        modifier = Modifier.padding(16.dp),
    )
}
