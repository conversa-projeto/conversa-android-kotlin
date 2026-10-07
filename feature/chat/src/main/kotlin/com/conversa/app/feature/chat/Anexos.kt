package com.conversa.app.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.PREFIXO_LOCAL
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.ehVideo
import com.conversa.app.core.model.formatarTamanho
import com.conversa.app.core.model.local
import com.conversa.app.core.ui.tema.ConversaTema

/** Ícone pela extensão/tipo (ANX-09). */
fun iconeDoArquivo(conteudo: Conteudo): ImageVector = when {
    conteudo.tipo == TipoConteudo.IMAGEM -> Icons.Outlined.Image
    conteudo.tipo == TipoConteudo.AUDIO || conteudo.tipo == TipoConteudo.GRAVACAO_AUDIO -> Icons.Outlined.AudioFile
    ehVideo(conteudo) -> Icons.Outlined.PlayCircle
    conteudo.extensao.equals("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/**
 * Imagem de anexo. Remota: busca a URL assinada e carrega com a chave de cache =
 * identificador (a URL muda a cada 10 min, o conteúdo não). Se falhar, esquece a URL
 * e tenta mais uma vez (URL vencida, ANX-14). Local (ainda enviando): o próprio arquivo.
 */
@Composable
fun ImagemAnexo(conteudo: Conteudo, acoes: AcoesBolha, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val contexto = LocalContext.current
    var tentativa by remember(conteudo.conteudo) { mutableIntStateOf(0) }
    val modelo by produceState<Any?>(null, conteudo.conteudo, tentativa) {
        value = if (conteudo.local) {
            conteudo.conteudo.removePrefix(PREFIXO_LOCAL).toUri()
        } else {
            acoes.urlDoAnexo(conteudo.conteudo)?.let { url ->
                ImageRequest.Builder(contexto)
                    .data(url)
                    .memoryCacheKey(conteudo.conteudo)
                    .diskCacheKey(conteudo.conteudo)
                    .build()
            }
        }
    }
    var falhou by remember(conteudo.conteudo, tentativa) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            falhou && tentativa > 0 -> Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = ConversaTema.cores.iconeDiscreto)
            modelo == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> AsyncImage(
                model = modelo,
                contentDescription = conteudo.nome.ifBlank { stringResource(R.string.conteudo_imagem) },
                contentScale = contentScale,
                onError = {
                    falhou = true
                    if (tentativa == 0 && !conteudo.local) {
                        acoes.esquecerUrl(conteudo.conteudo)
                        tentativa = 1
                    }
                },
            )
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
        ImagemAnexo(conteudo, acoes, Modifier.sizeIn(minWidth = 120.dp, minHeight = 90.dp, maxWidth = 260.dp, maxHeight = 320.dp))
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
            Box(
                Modifier
                    .widthIn(max = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ConversaTema.cores.campoEntrada),
            ) {
                Row(
                    Modifier.padding(6.dp),
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
                    Column(Modifier.widthIn(max = 80.dp)) {
                        Text(anexo.nome, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatarTamanho(anexo.tamanho),
                            style = MaterialTheme.typography.labelSmall,
                            color = ConversaTema.cores.textoTerciario,
                        )
                    }
                }
                IconButton(
                    onClick = { aoRemover(anexo.uri) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.remover_anexo, anexo.nome),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
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

/**
 * Visualizador em tela cheia (ANX-05): todas as imagens da conversa (menos as ocultas),
 * deslizando para os lados; pinça e duplo toque dão zoom; legenda embaixo.
 */
@Composable
fun VisualizadorImagens(imagens: List<ImagemDaConversa>, inicial: Int, acoes: AcoesBolha, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val paginas = rememberPagerState(initialPage = inicial.coerceIn(0, (imagens.size - 1).coerceAtLeast(0))) { imagens.size }
        Box(Modifier.fillMaxSize().background(ConversaTema.cores.fundoVisualizadorMidia)) {
            HorizontalPager(state = paginas, modifier = Modifier.fillMaxSize()) { pagina ->
                ImagemComZoom(imagens[pagina].conteudo, acoes)
            }
            IconButton(onClick = aoFechar, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fechar), tint = Color.White)
            }
            val atual = imagens.getOrNull(paginas.currentPage)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp),
            ) {
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
                        TextButton(onClick = { acoes.aoAbrirArquivo(atual.conteudo) }) {
                            Text(stringResource(R.string.abrir_com), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagemComZoom(conteudo: Conteudo, acoes: AcoesBolha) {
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
        ImagemAnexo(conteudo, acoes, Modifier.fillMaxSize())
    }
}
