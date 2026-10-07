package com.conversa.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.conversa.app.core.model.AtividadeConversa
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.RotuloDia
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.rotuloDia
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.IndicadorDigitando
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Quantos itens do topo faltam para pedir a página anterior. */
private const val MARGEM_PAGINA = 15

/** Longe do fim = mais de tantos itens acima do mais novo (mostra o botão "ir para o final"). */
private const val LONGE_DO_FIM = 6

@Composable
fun ChatRotaTela(
    aoVoltar: () -> Unit,
    aoMembros: (Long) -> Unit,
    aoAbrirConversa: (conversaId: Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = LocalAvisos.current
    val lista = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    val avisoChamadas = stringResource(R.string.chamadas_em_breve)
    // Chamadas entram na etapa 6; até lá, só o aviso.
    val aoLigar: (TipoChamada) -> Unit = { escopo.launch { avisos.showSnackbar(avisoChamadas) } }
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoChat.Erro -> avisos.mostrarErro(evento.mensagem)
            EventoChat.RolarAoFim -> lista.animateScrollToItem(0)
            is EventoChat.AbrirConversa -> aoAbrirConversa(evento.conversaId)
        }
    }
    ChatTela(
        estado = estado,
        lista = lista,
        acoes = AcoesChat(
            aoVoltar = aoVoltar,
            aoMembros = { aoMembros(viewModel.conversaId) },
            aoLigar = aoLigar,
            aoEnviar = viewModel::enviar,
            aoDigitar = viewModel::aoDigitar,
            aoCarregarAnteriores = viewModel::carregarAnteriores,
            aoVerMensagens = viewModel::marcarLidas,
            bolha = AcoesBolha(
                aoReenviar = viewModel::reenviar,
                aoDescartar = viewModel::descartar,
                aoMencao = viewModel::abrirDireta,
                aoLigar = aoLigar,
            ),
            aoIrAoFim = { escopo.launch { lista.animateScrollToItem(0) } },
        ),
    )
}

class AcoesChat(
    val aoVoltar: () -> Unit = {},
    val aoMembros: () -> Unit = {},
    val aoLigar: (TipoChamada) -> Unit = {},
    val aoEnviar: (String, () -> Unit) -> Unit = { _, _ -> },
    val aoDigitar: (String) -> Unit = {},
    val aoCarregarAnteriores: () -> Unit = {},
    val aoVerMensagens: (List<com.conversa.app.core.model.Mensagem>) -> Unit = {},
    val bolha: AcoesBolha = AcoesBolha(),
    val aoIrAoFim: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTela(estado: ChatUiState, lista: androidx.compose.foundation.lazy.LazyListState, acoes: AcoesChat) {
    // A lista é desenhada de baixo para cima (reverseLayout): o item 0 é a mensagem mais nova.
    val itens = remember(estado.itens) { estado.itens.asReversed() }
    val dono = LocalLifecycleOwner.current

    // Pede a página anterior perto do topo.
    LaunchedEffect(lista, itens.size) {
        snapshotFlow { lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= itens.size - MARGEM_PAGINA }
            .collect { acoes.aoCarregarAnteriores() }
    }
    // Marca como lidas as mensagens que aparecem na tela, só com o app visível (MSG-04).
    LaunchedEffect(lista, itens) {
        dono.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { lista.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { indices ->
                    acoes.aoVerMensagens(indices.mapNotNull { (itens.getOrNull(it) as? ItemChat.Bolha)?.mensagem })
                }
        }
    }

    // Ao abrir: primeira não lida no topo da tela; sem não lidas, o fim (MSG-05).
    var posicionou by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(itens, estado.pronto) {
        // Espera a decisão da "Últimas": o Room pode já ter mensagens antes disso (sincronização).
        if (posicionou || !estado.pronto || itens.isEmpty()) return@LaunchedEffect
        val linha = itens.indexOfFirst { it is ItemChat.NaoLidas }
        if (linha >= 0) lista.scrollToItem(linha, scrollOffset = -40) else lista.scrollToItem(0)
        posicionou = true
    }

    val longeDoFim by remember { derivedStateOf { lista.firstVisibleItemIndex > LONGE_DO_FIM } }
    // "Há novas mensagens": chegou mensagem de outra pessoa enquanto a pessoa estava longe do fim.
    var haNovas by remember { mutableStateOf(false) }
    val maisNova = (itens.firstOrNull() as? ItemChat.Bolha)?.mensagem
    LaunchedEffect(maisNova?.id) {
        if (maisNova != null && maisNova.remetenteId != estado.eu && longeDoFim) haNovas = true
    }
    LaunchedEffect(longeDoFim) { if (!longeDoFim) haNovas = false }

    Scaffold(
        topBar = { Cabecalho(estado, acoes) },
        bottomBar = { Campo(acoes) },
    ) { margens ->
        Box(Modifier.fillMaxSize().padding(margens).background(MaterialTheme.colorScheme.background)) {
            when {
                estado.carregando -> Carregando()
                itens.isEmpty() -> EstadoVazio(
                    titulo = stringResource(R.string.chat_vazio),
                    descricao = stringResource(R.string.chat_vazio_descricao),
                )
                else -> LazyColumn(
                    state = lista,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(itens, key = { it.chave }, contentType = { it::class }) { item ->
                        when (item) {
                            is ItemChat.Dia -> SeparadorDia(item)
                            ItemChat.NaoLidas -> SeparadorNaoLidas()
                            is ItemChat.Bolha -> LinhaMensagem(
                                mensagem = item.mensagem,
                                propria = item.mensagem.remetenteId == estado.eu,
                                mostrarRemetente = item.mostrarRemetente,
                                acoes = acoes.bolha,
                            )
                        }
                    }
                    if (estado.carregandoAnteriores) {
                        item(key = "carregando-anteriores") {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.carregando_anteriores),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = haNovas, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp).clickable {
                            haNovas = false
                            acoes.aoIrAoFim()
                        },
                    ) {
                        Text(
                            stringResource(R.string.ha_novas_mensagens),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                AnimatedVisibility(visible = longeDoFim, enter = fadeIn(), exit = fadeOut()) {
                    SmallFloatingActionButton(onClick = acoes.aoIrAoFim) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.ir_para_o_final))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho(estado: ChatUiState, acoes: AcoesChat) {
    val conversa = estado.conversa
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = acoes.aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(conversa?.titulo, conversa?.avatarUrl, tamanho = 38.dp, online = estado.online)
                Column {
                    Text(
                        conversa?.titulo.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val atividade = textoAtividade(estado.atividade)
                    when {
                        atividade != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IndicadorDigitando()
                            Text(
                                atividade,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        estado.online -> Subtitulo(stringResource(R.string.online))
                        estado.grupo && estado.membros.isNotEmpty() -> Subtitulo(estado.membros)
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { acoes.aoLigar(TipoChamada.AUDIO) }) {
                Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.chamada_de_voz))
            }
            IconButton(onClick = { acoes.aoLigar(TipoChamada.VIDEO) }) {
                Icon(Icons.Outlined.Videocam, contentDescription = stringResource(R.string.chamada_de_video))
            }
            if (estado.grupo) {
                IconButton(onClick = acoes.aoMembros) {
                    Icon(Icons.Outlined.Group, contentDescription = stringResource(R.string.membros_do_grupo))
                }
            }
        },
    )
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Digitando…", "Ana está digitando…", "Ana e Beto estão…", "… e outras N pessoas…" (textos do web). */
@Composable
private fun textoAtividade(atividade: AtividadeConversa): String? = when (atividade) {
    AtividadeConversa.Nenhuma -> null
    is AtividadeConversa.Direta -> stringResource(if (atividade.gravando) R.string.gravando else R.string.digitando)
    is AtividadeConversa.Grupo -> {
        val n = atividade.nomes
        val acao1 = stringResource(if (atividade.gravando) R.string.esta_gravando else R.string.esta_digitando)
        val acaoN = stringResource(if (atividade.gravando) R.string.estao_gravando else R.string.estao_digitando)
        when {
            atividade.outros > 0 -> pluralStringResource(
                R.plurals.e_outras_pessoas,
                atividade.outros,
                n[0],
                n[1],
                n[2],
                atividade.outros,
                acaoN,
            )
            n.size == 3 -> stringResource(R.string.tres_nomes, n[0], n[1], n[2], acaoN)
            n.size == 2 -> stringResource(R.string.dois_nomes, n[0], n[1], acaoN)
            else -> stringResource(R.string.um_nome, n[0], acao1)
        }
    }
}

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

@Composable
private fun SeparadorDia(item: ItemChat.Dia) {
    val texto = when (val rotulo = rotuloDia(item.data, Instant.now(), ZoneId.systemDefault())) {
        RotuloDia.Hoje -> stringResource(R.string.hoje)
        RotuloDia.Ontem -> stringResource(R.string.ontem)
        is RotuloDia.Data -> FORMATO_DIA.format(rotulo.data)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .background(ConversaTema.cores.separadorData, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Linha "Últimas" antes da primeira não lida (MSG-03). */
@Composable
private fun SeparadorNaoLidas() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = ConversaTema.cores.separadorNaoLidas)
        Text(
            stringResource(R.string.ultimas),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = ConversaTema.cores.separadorNaoLidas)
    }
}

/**
 * Campo de mensagem (ENV-01). O texto fica em estado local (síncrono) e só é limpo
 * depois que a mensagem foi gravada no Room: nunca se perde.
 */
@Composable
private fun Campo(acoes: AcoesChat) {
    var texto by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = texto,
            onValueChange = {
                texto = it
                acoes.aoDigitar(it)
            },
            placeholder = { Text(stringResource(R.string.digite_uma_mensagem)) },
            maxLines = 6,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ConversaTema.cores.campoEntrada,
                unfocusedContainerColor = ConversaTema.cores.campoEntrada,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = {
                val enviado = texto
                acoes.aoEnviar(enviado) { if (texto == enviado) texto = "" }
            },
            enabled = texto.isNotBlank(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.enviar))
        }
    }
}
