package com.conversa.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.conversa.app.core.model.AnexoDaConversa
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.DirecaoAnexos
import com.conversa.app.core.model.FiltroAnexos
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.dataDoAnexo
import com.conversa.app.core.model.ehVideo
import com.conversa.app.core.model.formatarTamanho
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoErro
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.QuadroVideo
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.launch

/** Quantos itens antes do fim já buscam a próxima página. */
private const val ANTECEDENCIA = 12

/**
 * Anexos da conversa (8.6, ANX-13): "Ver anexos" do perfil da pessoa ou dos dados do
 * grupo. Imagens em grade; o resto em lista (nome, tamanho · data, quem mandou). Cada um
 * com "Abrir mensagem" e "Baixar"; o toque abre (imagem e vídeo no visualizador do chat).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnexosDaConversaRotaTela(
    aoVoltar: () -> Unit,
    aoAbrirMensagem: (conversaId: Long, mensagemId: Long) -> Unit,
    viewModel: AnexosDaConversaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val semApp = stringResource(R.string.nenhum_app_para_abrir)
    var pdfAberto by remember { mutableStateOf<EventoAnexos.AbrirPdf?>(null) }
    var salvarComo by remember { mutableStateOf<Conteudo?>(null) }
    val escolherOndeSalvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val conteudo = salvarComo
        salvarComo = null
        if (uri != null && conteudo != null) viewModel.salvarEm(uri.toString(), conteudo)
    }
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoAnexos.AbrirArquivo -> if (!abrirComOutroApp(contexto, evento.arquivo, evento.mime)) avisos.mostrarErro(semApp)
            is EventoAnexos.AbrirPdf -> pdfAberto = evento
            is EventoAnexos.Compartilhar -> compartilharArquivo(contexto, evento.arquivo, null)
            is EventoAnexos.Baixando -> avisos.showSnackbar(recursos.getString(R.string.baixando, evento.nome))
            is EventoAnexos.EscolherOndeSalvar -> {
                salvarComo = evento.conteudo
                escolherOndeSalvar.launch(evento.conteudo.nome.ifBlank { evento.conteudo.conteudo })
            }
            is EventoAnexos.Erro -> avisos.mostrarErro(evento.mensagem)
        }
    }
    pdfAberto?.let { pdf ->
        VisualizadorPdf(
            arquivo = pdf.arquivo,
            nome = pdf.conteudo.nome.ifBlank { pdf.arquivo.name },
            aoBaixar = { viewModel.baixar(pdf.conteudo) },
            aoAbrirCom = { if (!abrirComOutroApp(contexto, pdf.arquivo, "application/pdf")) escopo.launch { avisos.mostrarErro(semApp) } },
            aoFechar = { pdfAberto = null },
        )
    }

    // Imagens e vídeos abrem no visualizador do chat, navegáveis entre os carregados (como o web).
    var aberto by remember { mutableStateOf<Long?>(null) }
    val acoesVisualizador = remember(viewModel) {
        AcoesBolha(
            aoAbrirArquivo = viewModel::abrir,
            aoBaixar = viewModel::baixar,
            aoCompartilhar = viewModel::compartilhar,
            urlDoVideo = viewModel::urlDoVideo,
        )
    }
    val galeria = remember(estado.itens) { estado.itens.filter { it.tipo == TipoConteudo.IMAGEM || ehVideo(it.paraConteudo()) } }
    aberto?.let { anexoId ->
        val inicial = galeria.indexOfFirst { it.anexoId == anexoId }
        val imagens = remember(galeria) { galeria.map { it.paraImagem() } }
        if (inicial < 0) {
            LaunchedEffect(Unit) { aberto = null }
        } else {
            VisualizadorImagens(
                imagens = imagens,
                inicial = inicial,
                acoes = acoesVisualizador,
                aoFechar = { aberto = null },
                aoAbrirMensagem = { imagem ->
                    aberto = null
                    aoAbrirMensagem(viewModel.conversaId, imagem.mensagemId)
                },
            )
        }
    }
    val abrir: (AnexoDaConversa) -> Unit = { item ->
        if (item in galeria) aberto = item.anexoId else viewModel.abrir(item.paraConteudo())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.anexos_titulo))
                        if (estado.titulo.isNotBlank()) {
                            Text(
                                estado.titulo,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
            )
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens)) {
            Filtros(estado, viewModel::mudarDirecao, viewModel::mudarFiltro)
            HorizontalDivider()
            Box(Modifier.fillMaxSize()) {
                val acoesItem = AcoesItemAnexo(
                    aoAbrir = abrir,
                    aoAbrirMensagem = { aoAbrirMensagem(it.conversaId.takeIf { c -> c > 0 } ?: viewModel.conversaId, it.mensagemId) },
                    aoBaixar = { viewModel.baixar(it.paraConteudo()) },
                    aoChegarNoFim = viewModel::carregarMais,
                )
                when {
                    estado.itens.isEmpty() && estado.carregando -> Carregando()
                    estado.itens.isEmpty() && estado.erro != null -> EstadoErro(estado.erro!!, viewModel::tentarDeNovo)
                    estado.itens.isEmpty() -> EstadoVazio(titulo = stringResource(R.string.anexos_vazio), icone = Icons.Outlined.AttachFile)
                    estado.filtro == FiltroAnexos.IMAGENS -> GradeImagens(estado.itens, acoesItem)
                    else -> ListaAnexos(estado.itens, acoesItem)
                }
                if (estado.carregando && estado.itens.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }
        }
    }
}

private class AcoesItemAnexo(
    val aoAbrir: (AnexoDaConversa) -> Unit,
    val aoAbrirMensagem: (AnexoDaConversa) -> Unit,
    val aoBaixar: (AnexoDaConversa) -> Unit,
    val aoChegarNoFim: () -> Unit,
)

/** Como imagem do visualizador: quem mandou e quando. */
private fun AnexoDaConversa.paraImagem() =
    ImagemDaConversa(
        mensagemId = mensagemId,
        conteudo = paraConteudo(),
        autor = autorNome,
        hora = criadoEm?.let {
            dataDoAnexo(it)
        }.orEmpty(),
    )

@Composable
private fun Filtros(estado: AnexosUiState, aoDirecao: (DirecaoAnexos) -> Unit, aoFiltro: (FiltroAnexos) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DirecaoAnexos.entries.forEach { direcao ->
                FilterChip(
                    selected = estado.direcao == direcao,
                    onClick = { aoDirecao(direcao) },
                    label = { Text(stringResource(textoDaDirecao(direcao))) },
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FiltroAnexos.entries.forEach { filtro ->
                FilterChip(
                    selected = estado.filtro == filtro,
                    onClick = { aoFiltro(filtro) },
                    label = { Text(stringResource(textoDoFiltro(filtro))) },
                )
            }
        }
    }
}

private fun textoDaDirecao(direcao: DirecaoAnexos): Int = when (direcao) {
    DirecaoAnexos.TODOS -> R.string.anexos_todos
    DirecaoAnexos.ENVIADOS -> R.string.anexos_enviados
    DirecaoAnexos.RECEBIDOS -> R.string.anexos_recebidos
}

private fun textoDoFiltro(filtro: FiltroAnexos): Int = when (filtro) {
    FiltroAnexos.TODOS -> R.string.anexos_todos
    FiltroAnexos.IMAGENS -> R.string.anexos_imagens
    FiltroAnexos.ARQUIVOS -> R.string.anexos_arquivos
    FiltroAnexos.AUDIOS -> R.string.anexos_audios
    FiltroAnexos.GRAVACOES -> R.string.anexos_gravacoes
}

@Composable
private fun GradeImagens(itens: List<AnexoDaConversa>, acoes: AcoesItemAnexo) {
    val grade = rememberLazyGridState()
    val noFim by remember {
        derivedStateOf {
            (grade.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                grade.layoutInfo.totalItemsCount - ANTECEDENCIA
        }
    }
    LaunchedEffect(noFim, itens.size) { if (noFim) acoes.aoChegarNoFim() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = grade,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(itens, key = { "${it.anexoId}:${it.mensagemId}" }) { item ->
            Box(Modifier.aspectRatio(1f).clickable(onClickLabel = item.nome.ifBlank { null }) { acoes.aoAbrir(item) }) {
                ImagemAnexo(item.paraConteudo(), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // As ações no canto, como o web (sobre fundo escuro para aparecer em qualquer foto).
                Row(Modifier.align(Alignment.TopStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BotaoSobreImagem(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.abrir_mensagem)) {
                        acoes.aoAbrirMensagem(item)
                    }
                    BotaoSobreImagem(Icons.Outlined.Download, stringResource(R.string.baixar)) { acoes.aoBaixar(item) }
                }
            }
        }
    }
}

@Composable
private fun BotaoSobreImagem(icone: ImageVector, descricao: String, aoTocar: () -> Unit) {
    // Tamanho fixo: o IconButton impõe 48 dp de área e, na célula pequena, um cobria o outro.
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClickLabel = descricao, onClick = aoTocar)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ListaAnexos(itens: List<AnexoDaConversa>, acoes: AcoesItemAnexo) {
    val lista = rememberLazyListState()
    val noFim by remember {
        derivedStateOf {
            (lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                lista.layoutInfo.totalItemsCount - ANTECEDENCIA
        }
    }
    LaunchedEffect(noFim, itens.size) { if (noFim) acoes.aoChegarNoFim() }
    LazyColumn(state = lista, modifier = Modifier.fillMaxSize()) {
        items(itens, key = { "${it.anexoId}:${it.mensagemId}" }) { item ->
            LinhaAnexo(item, acoes)
            HorizontalDivider()
        }
    }
}

@Composable
private fun LinhaAnexo(item: AnexoDaConversa, acoes: AcoesItemAnexo) {
    val conteudo = item.paraConteudo()
    val detalhe = listOfNotNull(formatarTamanho(item.tamanho), item.criadoEm?.let { dataDoAnexo(it) }).joinToString(" · ")
    ListItem(
        headlineContent = {
            Text(item.nome.ifBlank { stringResource(R.string.anexos_sem_nome) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(detalhe)
                Text(item.autorNome, color = ConversaTema.cores.textoTerciario, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { Miniatura(item, conteudo) },
        trailingContent = {
            Row {
                IconButton(onClick = { acoes.aoAbrirMensagem(item) }) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = stringResource(R.string.abrir_mensagem))
                }
                IconButton(onClick = { acoes.aoBaixar(item) }) {
                    Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.baixar))
                }
            }
        },
        modifier = Modifier.clickable { acoes.aoAbrir(item) },
    )
}

/** Imagem e vídeo com a miniatura; arquivo, áudio e gravação com o ícone do tipo. */
@Composable
private fun Miniatura(item: AnexoDaConversa, conteudo: Conteudo) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(48.dp).clip(forma).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            item.tipo == TipoConteudo.IMAGEM -> ImagemAnexo(conteudo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ehVideo(conteudo) -> {
                AsyncImage(
                    QuadroVideo(conteudo.conteudo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Fundo escuro: o ▶ branco some em quadros claros.
                Box(Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            else -> {
                val icone = when (item.tipo) {
                    TipoConteudo.AUDIO -> Icons.Outlined.AudioFile
                    TipoConteudo.GRAVACAO_AUDIO -> Icons.Outlined.Mic
                    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                }
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
