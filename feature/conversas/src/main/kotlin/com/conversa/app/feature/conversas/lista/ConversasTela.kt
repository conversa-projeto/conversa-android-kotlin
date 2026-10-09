package com.conversa.app.feature.conversas.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.PreviaConversa
import com.conversa.app.core.model.RotuloData
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoErro
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.FolhaPerfilUsuario
import com.conversa.app.core.ui.componentes.IndicadorDigitando
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.componentes.textoDaPrevia
import com.conversa.app.core.ui.componentes.textoDoRotulo
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.conversas.R

/** Tudo o que a lista pode pedir. */
class AcoesConversas(
    val aoAbrir: (Long) -> Unit = {},
    val aoAbrirContato: (Long) -> Unit = {},
    val aoAlterarTermo: (String) -> Unit = {},
    val aoAlternarArquivadas: () -> Unit = {},
    val aoAtualizar: () -> Unit = {},
    val aoNovaConversa: () -> Unit = {},
    val aoFixar: (Long) -> Unit = {},
    val aoDesafixar: (Long) -> Unit = {},
    val aoMover: (Long, Int) -> Unit = { _, _ -> },
    val aoArquivar: (Long, Boolean) -> Unit = { _, _ -> },
    val aoMembros: (Long) -> Unit = {},
    /** "Pesquisar em todos os chats" (8.2), com o termo do campo. */
    val aoPesquisarEmTodos: (String) -> Unit = {},
    /** "Ligar" do perfil da pessoa (8.4): voz na direta. */
    val aoLigar: (usuarioId: Long, conversaId: Long) -> Unit = { _, _ -> },
)

@Composable
fun ConversasRotaTela(
    aoAbrirConversa: (Long) -> Unit,
    aoNovaConversa: () -> Unit,
    aoMembros: (Long) -> Unit,
    aoPesquisarEmTodos: (String) -> Unit,
    aoLigar: (usuarioId: Long, conversaId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversasViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = LocalAvisos.current
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoConversas.Abrir -> aoAbrirConversa(evento.conversaId)
            is EventoConversas.Erro -> avisos.mostrarErro(evento.mensagem)
        }
    }
    ConversasTela(
        estado = estado,
        acoes = AcoesConversas(
            aoAbrir = aoAbrirConversa,
            aoAbrirContato = viewModel::abrirContato,
            aoAlterarTermo = viewModel::alterarTermo,
            aoAlternarArquivadas = viewModel::alternarArquivadas,
            aoAtualizar = viewModel::atualizar,
            aoNovaConversa = aoNovaConversa,
            aoFixar = viewModel::fixar,
            aoDesafixar = viewModel::desafixar,
            aoMover = viewModel::mover,
            aoArquivar = viewModel::arquivar,
            aoMembros = aoMembros,
            aoPesquisarEmTodos = aoPesquisarEmTodos,
            aoLigar = aoLigar,
        ),
        modifier = modifier,
    )
}

/**
 * Lista de conversas (CON-01…05): busca, fixadas, demais, "Arquivadas (N)" recolhível
 * e "Nova conversa" (contatos sem direta). Toque longo abre o menu da conversa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversasTela(estado: ConversasUiState, acoes: AcoesConversas, modifier: Modifier = Modifier) {
    var menuDe by rememberSaveable { mutableStateOf<Long?>(null) }
    // Direta: o toque no avatar abre o perfil da pessoa (8.4), como o web.
    var perfilDe by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier,
        // Dentro da tela principal: a barra inferior já cuida da área do sistema embaixo.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.conversas_titulo)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = acoes.aoNovaConversa) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.nova_conversa))
            }
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens)) {
            CampoBusca(estado.termo, acoes.aoAlterarTermo, acoes.aoPesquisarEmTodos)
            PullToRefreshBox(
                isRefreshing = estado.atualizando,
                onRefresh = acoes.aoAtualizar,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    estado.carregando -> Carregando()
                    estado.erro != null -> EstadoErro(mensagem = estado.erro, aoTentarDeNovo = acoes.aoAtualizar)
                    estado.principais.isEmpty() && estado.arquivadas.isEmpty() && estado.novaConversa.isEmpty() ->
                        if (estado.termo.isBlank()) {
                            EstadoVazio(
                                titulo = stringResource(R.string.conversas_vazio),
                                descricao = stringResource(R.string.conversas_vazio_descricao),
                                icone = Icons.AutoMirrored.Outlined.Chat,
                            )
                        } else {
                            EstadoVazio(titulo = stringResource(R.string.busca_vazia), icone = Icons.Outlined.Search)
                        }
                    else -> Lista(estado, acoes, aoMenu = { menuDe = it }, aoVerPerfil = { perfilDe = it })
                }
            }
        }
    }

    val itemDoMenu = menuDe?.let { id -> (estado.principais + estado.arquivadas).firstOrNull { it.id == id } }
    if (itemDoMenu != null) {
        MenuConversa(itemDoMenu, acoes, aoFechar = { menuDe = null })
    }
    val itemDoPerfil = perfilDe?.let { id -> (estado.principais + estado.arquivadas).firstOrNull { it.id == id } }
    itemDoPerfil?.ficha?.let { ficha ->
        FolhaPerfilUsuario(
            ficha = ficha,
            aoFechar = { perfilDe = null },
            aoLigar = {
                perfilDe = null
                acoes.aoLigar(ficha.id, itemDoPerfil.id)
            },
        )
    }
}

@Composable
private fun CampoBusca(termoInicial: String, aoAlterar: (String) -> Unit, aoPesquisarEmTodos: (String) -> Unit) {
    // Texto do campo em estado local (síncrono): passar pelo combine/stateIn do ViewModel
    // atrasa um quadro e o cursor pula enquanto se digita.
    var termo by rememberSaveable { mutableStateOf(termoInicial) }
    OutlinedTextField(
        value = termo,
        onValueChange = {
            termo = it
            aoAlterar(it)
        },
        placeholder = { Text(stringResource(R.string.pesquisar)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (termo.isNotEmpty()) {
                    IconButton(onClick = {
                        termo = ""
                        aoAlterar("")
                    }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limpar_busca)) }
                }
                // Como o web: o botão ao lado do campo pesquisa nas mensagens de todos os chats (8.2).
                IconButton(onClick = { aoPesquisarEmTodos(termo) }) {
                    Icon(Icons.Outlined.ManageSearch, contentDescription = stringResource(R.string.pesquisar_em_todos))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Lista(estado: ConversasUiState, acoes: AcoesConversas, aoMenu: (Long) -> Unit, aoVerPerfil: (Long) -> Unit) {
    // Lista sobre a superfície branca (como no FMX): o fundo do avatar (#F5F5F5) precisa de contraste.
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        items(estado.principais, key = { "c${it.id}" }) { item ->
            LinhaConversa(
                item,
                mostrarArquivada = estado.termo.isNotBlank(),
                aoAbrir = acoes.aoAbrir,
                aoMenu = aoMenu,
                aoVerPerfil = aoVerPerfil,
            )
        }
        if (estado.arquivadas.isNotEmpty()) {
            item(key = "arquivadas") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.arquivadas_n, estado.arquivadas.size)) },
                    leadingContent = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            if (estado.arquivadasAbertas) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = acoes.aoAlternarArquivadas),
                )
            }
            if (estado.arquivadasAbertas) {
                items(estado.arquivadas, key = { "a${it.id}" }) { item ->
                    LinhaConversa(item, mostrarArquivada = false, aoAbrir = acoes.aoAbrir, aoMenu = aoMenu, aoVerPerfil = aoVerPerfil)
                }
            }
        }
        if (estado.novaConversa.isNotEmpty()) {
            item(key = "nova") {
                Text(
                    stringResource(R.string.nova_conversa),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(estado.novaConversa, key = { "p${it.id}" }) { contato ->
                ListItem(
                    headlineContent = { Text(contato.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(contato.detalhe, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Avatar(contato.nome, contato.avatarUrl, online = contato.online) },
                    modifier = Modifier.clickable { acoes.aoAbrirContato(contato.id) },
                )
            }
        }
        // Espaço para o botão flutuante não cobrir o último item.
        item(key = "fim") { Box(Modifier.size(88.dp)) }
    }
}

@Composable
private fun LinhaConversa(
    item: ItemConversa,
    mostrarArquivada: Boolean,
    aoAbrir: (Long) -> Unit,
    aoMenu: (Long) -> Unit,
    aoVerPerfil: (Long) -> Unit,
) {
    val rotuloMenu = stringResource(R.string.mais_opcoes)
    val rotuloPerfil = stringResource(com.conversa.app.core.ui.R.string.perfil_usuario_ver)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { aoAbrir(item.id) },
                onLongClick = { aoMenu(item.id) },
                onLongClickLabel = rotuloMenu,
            )
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(rotuloMenu) {
                        aoMenu(item.id)
                        true
                    },
                    item.ficha?.let {
                        CustomAccessibilityAction(rotuloPerfil) {
                            aoVerPerfil(item.id)
                            true
                        }
                    },
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Na direta, o avatar abre o perfil da pessoa (o web usa o botão "Ver perfil"). Ripple redondo
        // sem recortar: a bolinha de online fica no canto.
        val toqueNoAvatar = if (item.ficha != null) {
            Modifier.clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 24.dp),
                onClickLabel = rotuloPerfil,
            ) { aoVerPerfil(item.id) }
        } else {
            Modifier
        }
        Avatar(nome = item.titulo, url = item.avatarUrl, tamanho = 48.dp, online = item.online, modifier = toqueNoAvatar)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.naoLidas > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.grupo) Etiqueta(stringResource(R.string.etiqueta_grupo))
                if (mostrarArquivada && item.arquivada) Etiqueta(stringResource(R.string.etiqueta_arquivada))
                if (item.fixada) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.fixada),
                        modifier = Modifier.size(16.dp),
                        tint = ConversaTema.cores.iconeDiscreto,
                    )
                }
                Box(Modifier.weight(1f))
                item.rotulo?.let {
                    Text(
                        textoDoRotulo(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.naoLidas > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (item.digitando) {
                        IndicadorDigitando(Modifier.padding(vertical = 6.dp))
                    } else {
                        Text(
                            textoDaPrevia(item.previa),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.naoLidas > 0) Contador(item.naoLidas)
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Contador(quantidade: Int) {
    val descricao = pluralStringResource(R.plurals.nao_lidas, quantidade, quantidade)
    Text(
        if (quantidade > 99) "99+" else quantidade.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = ConversaTema.cores.textoBadge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 8.dp)
            .widthIn(min = 20.dp)
            .background(ConversaTema.cores.badgeNaoLidas, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { this.contentDescription = descricao },
    )
}

/** Menu da conversa (CON-05): fixar/desafixar, mover, arquivar/desarquivar e membros. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuConversa(item: ItemConversa, acoes: AcoesConversas, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            item.titulo,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val fechar = { acao: () -> Unit ->
            {
                acao()
                aoFechar()
            }
        }
        if (!item.arquivada) {
            if (item.fixada) {
                OpcaoMenu(stringResource(R.string.desafixar), Icons.Outlined.PushPin, fechar { acoes.aoDesafixar(item.id) })
                if (item.podeSubir) {
                    OpcaoMenu(
                        stringResource(R.string.mover_cima),
                        Icons.Outlined.ArrowUpward,
                        fechar {
                            acoes.aoMover(item.id, -1)
                        },
                    )
                }
                if (item.podeDescer) {
                    OpcaoMenu(
                        stringResource(R.string.mover_baixo),
                        Icons.Outlined.ArrowDownward,
                        fechar {
                            acoes.aoMover(item.id, +1)
                        },
                    )
                }
            } else {
                OpcaoMenu(stringResource(R.string.fixar), Icons.Outlined.PushPin, fechar { acoes.aoFixar(item.id) })
            }
            OpcaoMenu(stringResource(R.string.arquivar), Icons.Outlined.Archive, fechar { acoes.aoArquivar(item.id, true) })
        } else {
            OpcaoMenu(stringResource(R.string.desarquivar), Icons.Outlined.Unarchive, fechar { acoes.aoArquivar(item.id, false) })
        }
        if (item.grupo) OpcaoMenu(stringResource(R.string.membros_titulo), Icons.Outlined.Group, fechar { acoes.aoMembros(item.id) })
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun OpcaoMenu(texto: String, icone: ImageVector, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(texto) },
        leadingContent = { Icon(icone, contentDescription = null) },
        modifier = Modifier.clickable(onClick = aoTocar),
    )
}

@Preview(showBackground = true)
@Composable
private fun ConversasTelaPreview() {
    ConversaTema {
        ConversasTela(
            estado = ConversasUiState(
                carregando = false,
                principais = listOf(
                    ItemConversa(
                        1, "Projeto Alpha", null, true, false, true, false, 3,
                        PreviaConversa.Texto(
                            "Ana: Código (kotlin)",
                        ),
                        RotuloData.Hora("09:05"), false,
                    ),
                    ItemConversa(2, "Bruno Lima", null, false, true, false, false, 0, PreviaConversa.Imagem, RotuloData.Ontem, true),
                    ItemConversa(3, "Carla", null, false, false, false, false, 0, PreviaConversa.SemMensagens, null, false),
                ),
                arquivadas = listOf(
                    ItemConversa(
                        4, "Antiga", null, false, false, false, true, 0, PreviaConversa.SemTexto,
                        RotuloData.Data(
                            "01/09/26",
                        ),
                        false,
                    ),
                ),
                novaConversa = listOf(ItemContato(9, "Daniel", "daniel", null, true)),
            ),
            acoes = AcoesConversas(),
        )
    }
}
