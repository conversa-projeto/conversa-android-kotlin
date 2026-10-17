package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.conversa.app.core.model.CORES_PASTA
import com.conversa.app.core.model.CodigoPermissao
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.LIMITE_NOME_PASTA
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Pasta
import com.conversa.app.core.model.argbDaPasta
import com.conversa.app.core.model.montarItensChat
import com.conversa.app.core.model.pastasDaMensagem
import com.conversa.app.core.ui.componentes.LocalPermissoesDeUso
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** As pastas da conversa direta, para as marcas das bolhas (MSG-23); tocar numa marca abre a pasta. */
@Immutable
data class PastasNaConversa(val pastas: List<Pasta> = emptyList(), val aoAbrir: (Pasta) -> Unit = {})

internal val LocalPastasNaConversa = compositionLocalOf { PastasNaConversa() }

/** Mensagens selecionadas (ENV-29): com alguma, tocar numa mensagem soma ou tira em vez de agir. */
@Immutable
data class SelecaoDeMensagens(
    val selecionadas: Set<Long> = emptySet(),
    val aoAlternar: (Long) -> Unit = {},
    val aoLimpar: () -> Unit = {},
    val aoPor: (Pasta) -> Unit = {},
) {
    val ativa: Boolean get() = selecionadas.isNotEmpty()
}

internal val LocalSelecaoDeMensagens = compositionLocalOf { SelecaoDeMensagens() }

/** O texto de cada falha, com o motivo do servidor (títulos do web). */
internal fun textoDaFalha(acao: AcaoPasta): Int = when (acao) {
    AcaoPasta.CRIAR -> R.string.pasta_falha_criar
    AcaoPasta.RENOMEAR -> R.string.pasta_falha_renomear
    AcaoPasta.MUDAR -> R.string.pasta_falha_mudar
    AcaoPasta.COR -> R.string.pasta_falha_cor
    AcaoPasta.EXCLUIR -> R.string.pasta_falha_excluir
    AcaoPasta.POR -> R.string.pasta_falha_por
    AcaoPasta.TIRAR -> R.string.pasta_falha_tirar
    AcaoPasta.CARREGAR -> R.string.pasta_falha_carregar
}

/** A cor da pasta; sem cor (ou fora de "#rrggbb"), a principal do tema (docs/design/cores.md §6.5). */
@Composable
internal fun corDaPasta(cor: String): Color = argbDaPasta(cor)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

/**
 * As marcas das pastas na bolha (MSG-23; web `MessageBubble.vue`): até três bolinhas na cor da
 * pasta. Na minha, atrás do ✓ ([status]), sem mudar a altura; na recebida, antes da hora, e
 * tocar numa delas abre a pasta.
 */
@Composable
internal fun MarcasDePasta(mensagem: Mensagem, propria: Boolean, status: @Composable () -> Unit) {
    val noChat = LocalPastasNaConversa.current
    val marcas = if (mensagem.pastas.isEmpty()) emptyList() else pastasDaMensagem(mensagem.pastas, noChat.pastas)
    if (propria) {
        val descricao = if (marcas.isEmpty()) null else stringResource(R.string.pasta_na_pasta, marcas.joinToString(", ") { it.nome })
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (descricao != null) Modifier.semantics { contentDescription = descricao } else Modifier,
        ) {
            if (marcas.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    marcas.forEach { Box(Modifier.size(12.dp).alpha(0.7f).clip(CircleShape).background(corDaPasta(it.cor))) }
                }
            }
            status()
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            marcas.forEach { pasta ->
                val rotulo = stringResource(R.string.pasta_marca, pasta.nome)
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = rotulo, role = Role.Button) { noChat.aoAbrir(pasta) }
                        .padding(3.dp),
                ) {
                    Box(Modifier.size(10.dp).alpha(0.8f).clip(CircleShape).background(corDaPasta(pasta.cor)))
                }
            }
        }
    }
}

/**
 * Uma mensagem que pode ser selecionada (ENV-29): com a seleção ativa, a faixa da largura toda
 * recebe o toque (soma ou tira) e a selecionada fica com o fundo da cor principal, como no web.
 */
@Composable
internal fun Selecionavel(mensagemId: Long, conteudo: @Composable () -> Unit) {
    val selecao = LocalSelecaoDeMensagens.current
    val selecionada = mensagemId in selecao.selecionadas
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selecionada) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent),
    ) {
        conteudo()
        if (selecao.ativa && mensagemId > 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .toggleable(value = selecionada, role = Role.Checkbox) { selecao.aoAlternar(mensagemId) },
            )
        }
    }
}

/** A barra das selecionadas no chat (web `MessageList.vue`): "N selecionadas", "Pôr na pasta" e ×. */
@Composable
internal fun BarraDeSelecao(
    quantas: Int,
    pastas: List<Pasta>,
    aoPor: (Pasta) -> Unit,
    aoLimpar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var escolhendo by remember { mutableStateOf(false) }
    PilulaDeSelecao(quantas, aoLimpar, modifier) {
        Box {
            TextButton(onClick = { escolhendo = true }) { Text(stringResource(R.string.pasta_por)) }
            DropdownMenu(expanded = escolhendo, onDismissRequest = { escolhendo = false }) {
                if (pastas.isEmpty()) {
                    Text(
                        stringResource(R.string.pasta_nenhuma),
                        style = MaterialTheme.typography.bodySmall,
                        color = ConversaTema.cores.textoTerciario,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(220.dp),
                    )
                }
                pastas.forEach { pasta ->
                    DropdownMenuItem(
                        text = { Text(pasta.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = corDaPasta(pasta.cor)) },
                        onClick = {
                            escolhendo = false
                            aoPor(pasta)
                        },
                    )
                }
            }
        }
    }
}

/** A pílula flutuante das selecionadas: a contagem, a ação e "Limpar seleção". */
@Composable
private fun PilulaDeSelecao(quantas: Int, aoLimpar: () -> Unit, modifier: Modifier, acao: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.border(1.dp, ConversaTema.cores.divisorLista, RoundedCornerShape(50)),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.pasta_selecionadas, quantas, quantas), style = MaterialTheme.typography.labelLarge)
            acao()
            IconButton(onClick = aoLimpar) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pasta_limpar_selecao)) }
        }
    }
}

/**
 * "Pastas de mensagens" (CON-17; web `PainelPastas.vue`): a lista com a cor, o ícone de
 * compartilhada e a contagem; "Nova pasta"; tocar abre a pasta. Só o dono tem o "⋮": renomear,
 * compartilhar (ou deixar), a cor e excluir, com a confirmação do web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolhaPastas(
    pastas: List<Pasta>,
    eu: Long,
    nomeOutraPessoa: String,
    viewModel: PastasViewModel,
    aoFechar: () -> Unit,
) {
    var criando by rememberSaveable { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf<Pasta?>(null) }
    var excluindo by remember { mutableStateOf<Pasta?>(null) }
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pasta_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    criando = true
                }) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.pasta_nova)) }
            }
            if (criando) {
                CampoNomeDaPasta(
                    inicial = "",
                    aoConfirmar = { nome ->
                        criando = false
                        viewModel.criar(nome)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (pastas.isEmpty() && !criando) {
                Text(
                    stringResource(R.string.pasta_vazio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConversaTema.cores.textoTerciario,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(pastas, key = { it.id }) { pasta ->
                    LinhaDaPasta(
                        pasta = pasta,
                        dono = pasta.donoId == eu,
                        nomeOutraPessoa = nomeOutraPessoa,
                        aoAbrir = {
                            aoFechar()
                            viewModel.abrir(pasta)
                        },
                        aoRenomear = { renomeando = pasta },
                        aoCompartilhar = { viewModel.alternarCompartilhada(pasta) },
                        aoCor = { viewModel.mudarCor(pasta, it) },
                        aoExcluir = { excluindo = pasta },
                    )
                }
            }
        }
    }
    renomeando?.let { pasta ->
        AlertDialog(
            onDismissRequest = { renomeando = null },
            title = { Text(stringResource(R.string.pasta_renomear)) },
            text = {
                CampoNomeDaPasta(pasta.nome, aoConfirmar = { nome ->
                    renomeando = null
                    viewModel.renomear(pasta, nome)
                })
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { renomeando = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
    excluindo?.let { pasta ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text(stringResource(R.string.pasta_excluir_titulo)) },
            text = {
                Text(
                    if (pasta.compartilhada) {
                        stringResource(R.string.pasta_excluir_texto_compartilhada, pasta.nome, nomeOutraPessoa)
                    } else {
                        stringResource(R.string.pasta_excluir_texto, pasta.nome)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    excluindo = null
                    viewModel.excluir(pasta)
                }) { Text(stringResource(R.string.pasta_excluir), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

/** O nome da pasta (até 60): Enter ou ✓ confirma; vazio não vale. */
@Composable
private fun CampoNomeDaPasta(inicial: String, aoConfirmar: (String) -> Unit, modifier: Modifier = Modifier) {
    val texto = rememberTextFieldState(inicial)
    val confirmar = { if (texto.text.isNotBlank()) aoConfirmar(texto.text.toString()) }
    // Já com o foco e o teclado, como o web (criar e renomear começam a digitar na hora).
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    OutlinedTextField(
        state = texto,
        modifier = modifier.fillMaxWidth().focusRequester(foco),
        placeholder = { Text(stringResource(R.string.pasta_nome)) },
        inputTransformation = InputTransformation.maxLength(LIMITE_NOME_PASTA),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { confirmar() },
        trailingIcon = {
            IconButton(onClick = confirmar, enabled = texto.text.isNotBlank()) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.salvar))
            }
        },
    )
}

@Composable
private fun LinhaDaPasta(
    pasta: Pasta,
    dono: Boolean,
    nomeOutraPessoa: String,
    aoAbrir: () -> Unit,
    aoRenomear: () -> Unit,
    aoCompartilhar: () -> Unit,
    aoCor: (String) -> Unit,
    aoExcluir: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoAbrir).padding(start = 20.dp, end = 4.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = corDaPasta(pasta.cor))
        Text(
            pasta.nome,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pasta.compartilhada) {
            Icon(
                Icons.Outlined.Group,
                contentDescription = stringResource(
                    if (dono) R.string.pasta_compartilhada_com else R.string.pasta_compartilhada_por,
                    nomeOutraPessoa,
                ),
                tint = ConversaTema.cores.textoTerciario,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(pasta.mensagens.toString(), style = MaterialTheme.typography.labelMedium, color = ConversaTema.cores.textoTerciario)
        if (dono) {
            Box {
                IconButton(onClick = {
                    menu = true
                }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.pasta_opcoes)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.pasta_renomear)) }, onClick = {
                        menu = false
                        aoRenomear()
                    })
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (pasta.compartilhada) {
                                    stringResource(R.string.pasta_deixar_de_compartilhar)
                                } else {
                                    stringResource(R.string.pasta_compartilhar_com, nomeOutraPessoa)
                                },
                            )
                        },
                        onClick = {
                            menu = false
                            aoCompartilhar()
                        },
                    )
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(200.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CORES_PASTA.forEach { cor ->
                            val escolhida = pasta.cor == cor
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(corDaPasta(cor))
                                    .then(
                                        if (escolhida) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable(
                                        onClickLabel = stringResource(
                                            if (cor.isEmpty()) R.string.pasta_cor_do_tema else R.string.pasta_cor,
                                        ),
                                        role = Role.RadioButton,
                                    ) {
                                        menu = false
                                        aoCor(cor)
                                    },
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pasta_excluir), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            aoExcluir()
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * A pasta aberta por cima do chat, sem campo (MSG-24; web `ChatDaPasta.vue`): "Voltar ao chat", o
 * nome e a contagem; só as mensagens dela, 50 por vez, rolando para cima traz as anteriores. O
 * toque longo abre "Ver na conversa", "Responder", "Selecionar" e "Tirar da pasta"; selecionadas,
 * saem de uma vez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TelaPastaAberta(
    aberta: PastaAbertaUi,
    eu: Long,
    acoesBolha: AcoesBolha,
    viewModel: PastasViewModel,
    aoVerNaConversa: (Mensagem) -> Unit,
    aoResponder: (Mensagem) -> Unit,
) {
    var menuDe by remember { mutableStateOf<Mensagem?>(null) }
    val podeResponder = LocalPermissoesDeUso.current.pode(CodigoPermissao.RESPONDER)
    fun voltar() {
        if (aberta.selecionadas.isNotEmpty()) viewModel.limparSelecaoNaPasta() else viewModel.fecharPasta()
    }
    Dialog(onDismissRequest = ::voltar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::fecharPasta) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pasta_voltar_ao_chat))
                        }
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = corDaPasta(aberta.pasta.cor),
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                aberta.pasta.nome,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val contagem = pluralStringResource(R.plurals.pasta_mensagens, aberta.pasta.mensagens, aberta.pasta.mensagens)
                            Text(
                                if (aberta.pasta.compartilhada) {
                                    stringResource(
                                        R.string.pasta_mensagens_compartilhada,
                                        contagem,
                                    )
                                } else {
                                    contagem
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ConversaTema.cores.textoTerciario,
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ListaDaPasta(aberta, eu, acoesBolha.copy(aoMenu = { menuDe = it }, aoResponder = aoResponder), viewModel)
                    if (aberta.selecionadas.isNotEmpty()) {
                        PilulaDeSelecao(
                            aberta.selecionadas.size,
                            viewModel::limparSelecaoNaPasta,
                            Modifier.align(Alignment.TopCenter).padding(8.dp),
                        ) {
                            TextButton(onClick = { viewModel.tirar(aberta.selecionadas) }) {
                                Text(stringResource(R.string.pasta_tirar), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    menuDe?.let { mensagem ->
        ModalBottomSheet(onDismissRequest = { menuDe = null }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                ItemDoMenuDaPasta(Icons.Outlined.Search, stringResource(R.string.pasta_ver_na_conversa)) {
                    menuDe = null
                    aoVerNaConversa(mensagem)
                }
                if (podeResponder && !mensagem.oculta) {
                    ItemDoMenuDaPasta(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.responder)) {
                        menuDe = null
                        aoResponder(mensagem)
                    }
                }
                ItemDoMenuDaPasta(Icons.Outlined.CheckCircle, stringResource(R.string.pasta_selecionar)) {
                    menuDe = null
                    viewModel.alternarSelecaoNaPasta(mensagem.id)
                }
                ItemDoMenuDaPasta(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.pasta_tirar), perigo = true) {
                    menuDe = null
                    viewModel.tirar(listOf(mensagem.id))
                }
            }
        }
    }
}

@Composable
private fun ListaDaPasta(aberta: PastaAbertaUi, eu: Long, acoes: AcoesBolha, viewModel: PastasViewModel) {
    val itens =
        remember(aberta.mensagens, eu) {
            montarItensChat(aberta.mensagens, null, grupo = false, eu = eu, zona = ZoneId.systemDefault()).asReversed()
        }
    val lista = rememberLazyListState()
    val carregarAnteriores by rememberUpdatedState(viewModel::carregarAnteriores)
    // Perto do topo (a lista é de baixo para cima): as anteriores.
    LaunchedEffect(lista, itens.size) {
        snapshotFlow { lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { itens.isNotEmpty() && it >= itens.size - 5 }
            .collect { carregarAnteriores() }
    }
    when {
        aberta.carregando && aberta.mensagens.isEmpty() -> Row(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.pasta_carregando),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        aberta.mensagens.isEmpty() -> Text(
            stringResource(R.string.pasta_aberta_vazia),
            style = MaterialTheme.typography.bodyMedium,
            color = ConversaTema.cores.textoTerciario,
            modifier = Modifier.padding(32.dp),
        )
        else -> androidx.compose.runtime.CompositionLocalProvider(
            LocalSelecaoDeMensagens provides SelecaoDeMensagens(aberta.selecionadas, viewModel::alternarSelecaoNaPasta),
        ) {
            LazyColumn(
                state = lista,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(itens, key = { it.chave }, contentType = { it::class }) { item ->
                    when (item) {
                        is ItemChat.Dia -> SeparadorDia(item)
                        ItemChat.NaoLidas -> Unit
                        is ItemChat.Bolha -> Selecionavel(item.mensagem.id) {
                            LinhaMensagem(item.mensagem, propria = item.mensagem.remetenteId == eu, mostrarRemetente = false, acoes = acoes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemDoMenuDaPasta(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    texto: String,
    perigo: Boolean = false,
    onClick: () -> Unit,
) {
    val cor = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icone, contentDescription = null, tint = cor)
        Text(texto, color = cor, style = MaterialTheme.typography.bodyLarge)
    }
}
