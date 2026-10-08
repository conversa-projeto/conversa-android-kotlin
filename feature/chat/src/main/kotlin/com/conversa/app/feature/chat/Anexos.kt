package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.media.ReprodutorVideo
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.PREFIXO_LOCAL
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.ehVideo
import com.conversa.app.core.model.formatarTamanho
import com.conversa.app.core.model.local
import com.conversa.app.core.ui.componentes.AnexoRemoto
import com.conversa.app.core.ui.componentes.QuadroVideo
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.launch

/** Ícone pela extensão/tipo (ANX-09). */
fun iconeDoArquivo(conteudo: Conteudo): ImageVector = when {
    conteudo.tipo == TipoConteudo.IMAGEM -> Icons.Outlined.Image
    conteudo.tipo == TipoConteudo.AUDIO || conteudo.tipo == TipoConteudo.GRAVACAO_AUDIO -> Icons.Outlined.AudioFile
    ehVideo(conteudo) -> Icons.Outlined.PlayCircle
    conteudo.extensao.equals("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/**
 * Imagem de anexo. Remota: [AnexoRemoto] (o Coil obtém a URL assinada, renova se vencer
 * e guarda no cache pelo identificador, ANX-14). Local (ainda enviando): o próprio arquivo.
 */
@Composable
fun ImagemAnexo(conteudo: Conteudo, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val modelo = remember(conteudo.conteudo) {
        if (conteudo.local) conteudo.conteudo.removePrefix(PREFIXO_LOCAL).toUri() else AnexoRemoto(conteudo.conteudo)
    }
    var estado by remember(conteudo.conteudo) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = modelo,
            contentDescription = conteudo.nome.ifBlank { stringResource(R.string.conteudo_imagem) },
            contentScale = contentScale,
            onState = { estado = it },
        )
        when (estado) {
            is AsyncImagePainter.State.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            is AsyncImagePainter.State.Error ->
                Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = ConversaTema.cores.iconeDiscreto)
            else -> Unit
        }
    }
}

/** Bolha só de imagem (ANX-04): proporção preservada, hora por cima, toque abre o visualizador. */
@Composable
fun BolhaImagem(mensagem: Mensagem, propria: Boolean, progresso: Float?, acoes: AcoesBolha) {
    val conteudo = mensagem.conteudos.first()
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (propria) ConversaTema.cores.bolhaPropria else ConversaTema.cores.bolhaOutro)
            .clickable { acoes.aoAbrirImagem(mensagem, conteudo) },
    ) {
        ImagemAnexo(conteudo, Modifier.sizeIn(minWidth = 120.dp, minHeight = 90.dp, maxWidth = 260.dp, maxHeight = 320.dp))
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(horaDa(mensagem), style = MaterialTheme.typography.labelSmall, color = Color.White)
                if (propria) IconeStatus(com.conversa.app.core.model.statusEntrega(mensagem))
            }
        }
        if (progresso != null) {
            LinearProgressIndicator(progress = { progresso }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

/** Linha de arquivo dentro da bolha (ANX-09): ícone, nome e "Abrir". */
@Composable
fun LinhaArquivo(conteudo: Conteudo, cor: Color, acoes: AcoesBolha) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.05f))
            .clickable(enabled = !conteudo.local) { acoes.aoAbrirArquivo(conteudo) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(iconeDoArquivo(conteudo), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text(
            conteudo.nome.ifBlank { stringResource(R.string.conteudo_arquivo) },
            color = cor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!conteudo.local) {
            Text(stringResource(R.string.abrir), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            // "Download" do web: salva em Downloads/Conversa.
            IconButton(onClick = { acoes.aoBaixar(conteudo) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.baixar),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Fila de anexos acima do campo (ANX-03): miniatura ou ícone, nome, tamanho e "×". */
@Composable
fun FilaAnexos(fila: List<AnexoLocal>, aoRemover: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fila.forEach { anexo ->
            Row(
                Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ConversaTema.cores.campoEntrada)
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (anexo.tipo == TipoConteudo.IMAGEM) {
                    AsyncImage(
                        model = anexo.uri.toUri(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    val icone = iconeDoArquivo(Conteudo(null, 0, anexo.tipo, "", anexo.nome, anexo.nome.substringAfterLast('.', "")))
                    Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(anexo.nome, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        formatarTamanho(anexo.tamanho),
                        style = MaterialTheme.typography.labelSmall,
                        color = ConversaTema.cores.textoTerciario,
                    )
                }
                IconButton(onClick = { aoRemover(anexo.uri) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.remover_anexo, anexo.nome),
                        tint = ConversaTema.cores.iconeDiscreto,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Uma imagem do visualizador: o conteúdo e o texto da mesma mensagem (legenda). */
data class ImagemDaConversa(val mensagem: Mensagem, val conteudo: Conteudo) {
    val legenda: String get() = mensagem.conteudos.filter { it.tipo == TipoConteudo.TEXTO }.joinToString("\n") { it.conteudo }
}

/** Todas as imagens da conversa, em ordem cronológica, menos as de mensagens ocultas (ANX-05). */
fun imagensDaConversa(itens: List<ItemChat>): List<ImagemDaConversa> = itens.asSequence()
    .filterIsInstance<ItemChat.Bolha>()
    .map { it.mensagem }
    .filterNot { it.oculta }
    .flatMap { mensagem ->
        mensagem.conteudos.filter { it.tipo == TipoConteudo.IMAGEM || ehVideo(it) }.map { ImagemDaConversa(mensagem, it) }
    }
    .toList()

/**
 * Vídeo na bolha (ANX-04, como o web): o primeiro quadro com o play; o toque abre o
 * visualizador, onde ele toca. Enviando (arquivo local), só a prévia.
 */
@Composable
fun VideoNaBolha(mensagem: Mensagem, conteudo: Conteudo, acoes: AcoesBolha) {
    Column {
        QuadroDaBolha(mensagem, conteudo, acoes)
        if (!conteudo.local) {
            TextButton(onClick = { acoes.aoBaixar(conteudo) }) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.baixar_video),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun QuadroDaBolha(mensagem: Mensagem, conteudo: Conteudo, acoes: AcoesBolha) {
    Box(
        Modifier
            .size(width = 240.dp, height = 160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(enabled = !conteudo.local) { acoes.aoAbrirImagem(mensagem, conteudo) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = QuadroVideo(conteudo.conteudo),
            contentDescription = conteudo.nome.ifBlank { stringResource(R.string.conteudo_video) },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Visualizador em tela cheia (ANX-05): todas as imagens e vídeos da conversa (menos os
 * ocultos), deslizando para os lados; imagens com pinça e duplo toque; vídeo com os
 * controles do Media3; embaixo, quem mandou, a legenda e a tira de miniaturas.
 */
@Composable
fun VisualizadorImagens(imagens: List<ImagemDaConversa>, inicial: Int, acoes: AcoesBolha, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val paginas = rememberPagerState(initialPage = inicial.coerceIn(0, (imagens.size - 1).coerceAtLeast(0))) { imagens.size }
        val escopo = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().background(ConversaTema.cores.fundoVisualizadorMidia)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = paginas, modifier = Modifier.fillMaxSize()) { pagina ->
                    val conteudo = imagens[pagina].conteudo
                    if (ehVideo(conteudo)) {
                        VideoNoVisualizador(conteudo, ativo = paginas.currentPage == pagina, acoes)
                    } else {
                        ImagemComZoom(conteudo)
                    }
                }
                // Fundo escuro no "×": continua visível sobre imagens claras e com zoom.
                IconButton(
                    onClick = aoFechar,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fechar), tint = Color.White)
                }
            }
            val atual = imagens.getOrNull(paginas.currentPage)
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(12.dp)) {
                if (atual != null) {
                    Text(
                        "${atual.mensagem.remetente} · ${horaDa(atual.mensagem)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (atual.legenda.isNotBlank()) {
                        Text(
                            atual.legenda,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 120.dp),
                        )
                    }
                    if (!atual.conteudo.local) {
                        Row {
                            TextButton(onClick = { acoes.aoAbrirArquivo(atual.conteudo) }) {
                                Text(stringResource(R.string.abrir_com), color = Color.White)
                            }
                            TextButton(onClick = { acoes.aoCompartilhar(atual.conteudo) }) {
                                Text(stringResource(R.string.compartilhar), color = Color.White)
                            }
                            TextButton(onClick = { acoes.aoBaixar(atual.conteudo) }) {
                                Text(stringResource(R.string.baixar), color = Color.White)
                            }
                        }
                    }
                }
                if (imagens.size > 1) {
                    TiraMiniaturas(imagens, paginas.currentPage) { escopo.launch { paginas.animateScrollToPage(it) } }
                }
            }
        }
    }
}

/** Vídeo do visualizador: busca a URL assinada (ou o arquivo local) e toca com o Media3. */
@Composable
private fun VideoNoVisualizador(conteudo: Conteudo, ativo: Boolean, acoes: AcoesBolha) {
    val uri by produceState<String?>(null, conteudo.conteudo) { value = acoes.urlDoVideo(conteudo) }
    var falhou by remember(conteudo.conteudo) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val endereco = uri
        when {
            falhou -> Text(stringResource(R.string.video_falhou), color = Color.White)
            endereco == null -> CircularProgressIndicator()
            else -> ReprodutorVideo(endereco, ativo, aoFalhar = { falhou = true }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Miniaturas embaixo do visualizador (como o web): a atual em destaque; o toque vai até ela. */
@Composable
private fun TiraMiniaturas(imagens: List<ImagemDaConversa>, atual: Int, aoEscolher: (Int) -> Unit) {
    val lista = rememberLazyListState(initialFirstVisibleItemIndex = (atual - 2).coerceAtLeast(0))
    LaunchedEffect(atual) { lista.animateScrollToItem((atual - 2).coerceAtLeast(0)) }
    LazyRow(state = lista, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        itemsIndexed(imagens, key = { _, item -> "${item.mensagem.id}:${item.conteudo.ordem}" }) { indice, item ->
            val selecionada = indice == atual
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (selecionada) 2.dp else 0.dp, if (selecionada) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    .alpha(if (selecionada) 1f else 0.6f)
                    .clickable { aoEscolher(indice) },
                contentAlignment = Alignment.Center,
            ) {
                if (ehVideo(item.conteudo)) {
                    AsyncImage(
                        QuadroVideo(item.conteudo.conteudo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    ImagemAnexo(item.conteudo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
private fun ImagemComZoom(conteudo: Conteudo) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    val estado = rememberTransformableState { zoom, mover, _ ->
        escala = (escala * zoom).coerceIn(1f, 5f)
        deslocamento = if (escala == 1f) Offset.Zero else deslocamento + mover
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    escala = if (escala > 1f) 1f else 2.5f
                    deslocamento = Offset.Zero
                })
            }
            // Sem zoom, o arrasto fica com o pager (trocar de imagem); a pinça sempre funciona.
            .transformable(estado, canPan = { escala > 1f })
            .graphicsLayer(scaleX = escala, scaleY = escala, translationX = deslocamento.x, translationY = deslocamento.y),
        contentAlignment = Alignment.Center,
    ) {
        ImagemAnexo(conteudo, Modifier.fillMaxSize())
    }
}
