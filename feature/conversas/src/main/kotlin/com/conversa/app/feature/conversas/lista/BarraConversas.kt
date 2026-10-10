package com.conversa.app.feature.conversas.lista

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.conversas.R

/**
 * Barra de título da lista com a pesquisa dentro dela (pedido de 2026-10-09): só a lupa à
 * direita; ao tocar, ela se estica até ocupar a barra, com o campo já focado. Dentro:
 * "←" (ou o voltar do sistema) fecha e limpa o filtro, "×" apaga o texto e o ícone de
 * "Pesquisar em todos os chats" (8.2) segue com o termo. Com um termo guardado (volta de
 * outra tela), já abre aberta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarraConversas(titulo: String, termoInicial: String, aoAlterar: (String) -> Unit, aoPesquisarEmTodos: (String) -> Unit) {
    var aberta by rememberSaveable { mutableStateOf(termoInicial.isNotBlank()) }
    // Texto do campo em estado local (síncrono): passar pelo ViewModel atrasa um quadro e o cursor pula.
    var termo by rememberSaveable { mutableStateOf(termoInicial) }
    val progresso by animateFloatAsState(if (aberta) 1f else 0f, tween(DURACAO_MS), label = "pesquisa")
    val foco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    val alterar = { novo: String ->
        termo = novo
        aoAlterar(novo)
    }
    val fechar = {
        aberta = false
        if (termo.isNotEmpty()) alterar("")
    }
    BackHandler(enabled = aberta, onBack = fechar)
    LaunchedEffect(aberta) {
        if (aberta) {
            foco.requestFocus()
            teclado?.show()
        }
    }
    TopAppBar(
        title = { Text(titulo, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 1f - progresso }) },
        actions = {
            BoxWithConstraints {
                // Da lupa até a barra inteira (as ações da TopAppBar ficam a 4 dp de cada borda).
                val largura = lerp(TAMANHO_LUPA, maxWidth - 8.dp, progresso)
                Row(
                    Modifier
                        .width(largura)
                        .height(TAMANHO_LUPA)
                        .clip(RoundedCornerShape(TAMANHO_LUPA / 2))
                        .background(ConversaTema.cores.campoEntrada.copy(alpha = progresso)),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (aberta) {
                        CampoAberto(
                            termo = termo,
                            modifier = Modifier.weight(1f).graphicsLayer { alpha = progresso },
                            foco = foco,
                            acoes = AcoesCampo(
                                aoAlterar = alterar,
                                aoFechar = fechar,
                                aoLimpar = {
                                    alterar("")
                                    foco.requestFocus()
                                },
                                aoPesquisarEmTodos = { aoPesquisarEmTodos(termo) },
                                aoConfirmar = { teclado?.hide() },
                            ),
                        )
                    } else {
                        IconButton(onClick = { aberta = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.abrir_pesquisa))
                        }
                    }
                }
            }
        },
    )
}

private class AcoesCampo(
    val aoAlterar: (String) -> Unit,
    val aoFechar: () -> Unit,
    val aoLimpar: () -> Unit,
    val aoPesquisarEmTodos: () -> Unit,
    val aoConfirmar: () -> Unit,
)

@Composable
private fun CampoAberto(termo: String, modifier: Modifier, foco: FocusRequester, acoes: AcoesCampo) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = acoes.aoFechar) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fechar_pesquisa))
        }
        val estilo = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
        val placeholder = stringResource(R.string.pesquisar)
        BasicTextField(
            value = termo,
            onValueChange = acoes.aoAlterar,
            singleLine = true,
            textStyle = estilo,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { acoes.aoConfirmar() }),
            modifier = Modifier.weight(1f).focusRequester(foco),
            decorationBox = { campo ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (termo.isEmpty()) Text(placeholder, style = estilo, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    campo()
                }
            },
        )
        if (termo.isNotEmpty()) {
            IconButton(onClick = acoes.aoLimpar) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limpar_busca))
            }
        }
        // Como o web: o botão ao lado do campo pesquisa nas mensagens de todos os chats (8.2).
        IconButton(onClick = acoes.aoPesquisarEmTodos) {
            Icon(Icons.Outlined.ManageSearch, contentDescription = stringResource(R.string.pesquisar_em_todos))
        }
    }
}

private val TAMANHO_LUPA = 48.dp
private const val DURACAO_MS = 250
