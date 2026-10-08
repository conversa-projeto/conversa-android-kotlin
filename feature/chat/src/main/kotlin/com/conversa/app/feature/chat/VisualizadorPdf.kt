package com.conversa.app.feature.chat

import android.graphics.Bitmap
import android.graphics.Color as CorAndroid
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.conversa.app.core.ui.tema.ConversaTema
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Páginas de um PDF desenhadas sob demanda. O `PdfRenderer` abre uma página por vez
 * (daí a trava); as desenhadas ficam num cache limitado em bytes.
 */
private class LeitorPdf(arquivo: File) : AutoCloseable {
    private val descritor = ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderizador = try {
        PdfRenderer(descritor)
    } catch (e: Exception) {
        descritor.close()
        throw e
    }
    private val trava = Mutex()
    private val cache = object : LruCache<Int, Bitmap>(LIMITE_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    val paginas: Int = renderizador.pageCount

    /** Altura ÷ largura de cada página (o espaço já fica reservado antes de desenhar). */
    val proporcoes: List<Float> = (0 until paginas).map { indice -> renderizador.openPage(indice).use { it.height.toFloat() / it.width } }

    suspend fun pagina(indice: Int, largura: Int): Bitmap = cache.get(indice) ?: trava.withLock {
        withContext(Dispatchers.IO) {
            cache.get(indice) ?: renderizador.openPage(indice).use { pagina ->
                val altura = (largura * pagina.height.toFloat() / pagina.width).toInt().coerceAtLeast(1)
                createBitmap(largura, altura).also {
                    it.eraseColor(CorAndroid.WHITE)
                    pagina.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(indice, it)
                }
            }
        }
    }

    override fun close() {
        cache.evictAll()
        renderizador.close()
        descritor.close()
    }

    companion object {
        const val LIMITE_CACHE_BYTES = 48 * 1024 * 1024
    }
}

/**
 * Visualizador de PDF (TODO 4.10, FC-411, como o `VisualizadorPdf.vue` do web): páginas
 * desenhadas sob demanda, pinça e duplo toque para zoom, "Página X de Y", Baixar,
 * Abrir com… e Fechar. PDF protegido ou inválido: a mensagem do web e o "Abrir com…".
 */
@Composable
fun VisualizadorPdf(arquivo: File, nome: String, aoBaixar: () -> Unit, aoAbrirCom: () -> Unit, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val leitor by produceState<Result<LeitorPdf>?>(null, arquivo) {
            value = withContext(Dispatchers.IO) { runCatching { LeitorPdf(arquivo) } }
            awaitDispose { value?.getOrNull()?.close() }
        }
        val lista = rememberLazyListState()
        val atual by remember { derivedStateOf { lista.firstVisibleItemIndex + 1 } }
        Column(Modifier.fillMaxSize().background(ConversaTema.cores.fundoVisualizadorMidia).statusBarsPadding().navigationBarsPadding()) {
            // Segunda camada do fundo do FMX na barra: quase opaca, o título do chat atrás não aparece.
            Row(
                Modifier.fillMaxWidth().background(ConversaTema.cores.fundoVisualizadorMidia).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nome,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    leitor?.getOrNull()?.let {
                        Text(
                            stringResource(R.string.pagina_de, atual, it.paginas),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                IconButton(onClick = aoBaixar) {
                    Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.baixar), tint = Color.White)
                }
                IconButton(onClick = aoAbrirCom) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.abrir_com), tint = Color.White)
                }
                IconButton(onClick = aoFechar) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fechar), tint = Color.White)
                }
            }
            val resultado = leitor
            when {
                resultado == null -> Mensagem(stringResource(R.string.carregando_pdf)) { CircularProgressIndicator(color = Color.White) }
                resultado.isFailure -> Mensagem(stringResource(R.string.pdf_falhou)) {
                    TextButton(onClick = aoAbrirCom) { Text(stringResource(R.string.abrir_com), color = Color.White) }
                }
                else -> PaginasPdf(resultado.getOrThrow(), lista)
            }
        }
    }
}

@Composable
private fun Mensagem(texto: String, extra: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        extra()
        Text(
            texto,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * As páginas, uma embaixo da outra. Zoom com pinça (até 4×) e duplo toque (1× ↔ 2×); com
 * zoom, arrastar move para os lados e o vertical continua rolando a lista.
 */
@Composable
private fun PaginasPdf(leitor: LeitorPdf, lista: androidx.compose.foundation.lazy.LazyListState) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val larguraPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var escala by remember { mutableFloatStateOf(1f) }
        var deslocamentoX by remember { mutableFloatStateOf(0f) }
        val estado = rememberTransformableState { zoom, mover, _ ->
            escala = (escala * zoom).coerceIn(1f, ESCALA_MAXIMA)
            val limite = larguraPx * (escala - 1f) / 2f
            deslocamentoX = (deslocamentoX + mover.x).coerceIn(-limite, limite)
            lista.dispatchRawDelta(-mover.y / escala)
        }
        LazyColumn(
            state = lista,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        escala = if (escala > 1f) 1f else 2f
                        deslocamentoX = 0f
                    })
                }
                // Sem zoom, o arrasto de um dedo fica com a rolagem; a pinça sempre funciona.
                .transformable(estado, canPan = { escala > 1f })
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamentoX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            items(leitor.paginas) { indice ->
                PaginaPdf(leitor, indice, larguraPx)
            }
        }
    }
}

@Composable
private fun PaginaPdf(leitor: LeitorPdf, indice: Int, larguraPx: Int) {
    val imagem by produceState<Bitmap?>(null, indice, larguraPx) {
        value = runCatching { leitor.pagina(indice, larguraPx.coerceAtMost(LARGURA_MAXIMA_PX)) }.getOrNull()
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / leitor.proporcoes[indice]).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val pronta = imagem
        if (pronta == null) {
            CircularProgressIndicator(Modifier.padding(16.dp))
        } else {
            Image(
                pronta.asImageBitmap(),
                contentDescription = stringResource(R.string.pagina_de, indice + 1, leitor.paginas),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val ESCALA_MAXIMA = 4f

/** Acima disso a página fica nítida o bastante e o bitmap não pesa tanto. */
private const val LARGURA_MAXIMA_PX = 1600
