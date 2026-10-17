package com.conversa.app.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.conversa.app.core.model.CamadaMapa
import com.conversa.app.core.model.Enquadramento
import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.TAMANHO_TILE
import com.conversa.app.core.model.ZOOM_MAXIMO_MAPA
import com.conversa.app.core.model.decodificarRota
import com.conversa.app.core.model.enderecoDoTile
import com.conversa.app.core.model.enquadrar
import com.conversa.app.core.model.latDoY
import com.conversa.app.core.model.lngDoX
import com.conversa.app.core.model.xNoMapa
import com.conversa.app.core.model.yNoMapa
import com.conversa.app.core.ui.tema.ConversaTema
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Quantos pixels da tela cada pixel do mapa ocupa. Os tiles têm 256 px: na tela de alta
 * densidade ficariam com letras miúdas, e ampliados pela densidade inteira, borrados. O meio da
 * densidade (nunca menos que 1) fica legível e ainda nítido.
 */
internal fun fatorDoMapa(densidade: Float): Float = (densidade / 2f).coerceAtLeast(1f)

/** A folga de cima do enquadramento: o alfinete (38 dp) fica acima do ponto. */
private val ALTURA_ALFINETE = 40.dp

/**
 * Onde o mapa está olhando: o centro e o zoom (fracionário enquanto a pinça mexe), em pixels
 * do mapa. O [MapaDeTiles] desenha a partir daqui; os gestos e quem usa o mapa mudam por aqui.
 */
@Stable
class EstadoMapa(inicial: Enquadramento) {
    var lat by mutableDoubleStateOf(inicial.lat)
    var lng by mutableDoubleStateOf(inicial.lng)
    var zoom by mutableDoubleStateOf(inicial.zoom.toDouble())

    fun centralizar(ponto: PontoMapa, zoom: Double = this.zoom) {
        lat = ponto.lat
        lng = ponto.lng
        this.zoom = zoom.coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO_MAPA.toDouble())
    }

    fun enquadrar(enquadramento: Enquadramento) {
        lat = enquadramento.lat
        lng = enquadramento.lng
        zoom = enquadramento.zoom.toDouble()
    }

    /** Arrasta [dx], [dy] pixels do mapa e amplia [fator] em torno de [foco] (pixels a partir do centro). */
    internal fun mover(dx: Float, dy: Float, fator: Float, foco: Offset) {
        val antes = zoom
        val novo = (antes + ln(fator.toDouble()) / ln(2.0)).coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO_MAPA.toDouble())
        // O ponto sob o foco fica parado: em pixels do zoom novo, o centro anda para manter o foco.
        val escala = 2.0.pow(novo - antes)
        val fx = xNoMapa(lng, antes) + foco.x
        val fy = yNoMapa(lat, antes) + foco.y
        zoom = novo
        lng = lngDoX(fx * escala - foco.x - dx, novo)
        lat = latDoY(fy * escala - foco.y - dy, novo).coerceIn(-85.0, 85.0)
    }

    /** O ponto do mapa sob [toque] (pixels do mapa a partir do canto de cima, num mapa de [largura] × [altura]). */
    internal fun pontoEm(toque: Offset, largura: Float, altura: Float): PontoMapa {
        val x = xNoMapa(lng, zoom) + toque.x - largura / 2
        val y = yNoMapa(lat, zoom) + toque.y - altura / 2
        return PontoMapa(latDoY(y, zoom), lngDoX(x, zoom))
    }

    companion object {
        const val ZOOM_MINIMO = 2.0
    }
}

/** Os pontos que o enquadramento precisa ver: os marcadores e a linha da rota. */
internal fun pontosParaEnquadrar(localizacao: Localizacao): List<Pair<Double, Double>> =
    localizacao.pontos.map { it.lat to it.lng } + localizacao.rota?.let(::decodificarRota).orEmpty()

/** O enquadramento inicial de um mapa de [larguraTela] × [alturaTela] pixels da tela mostrando [localizacao]. */
internal fun Density.enquadramentoDe(localizacao: Localizacao, larguraTela: Float, alturaTela: Float): Enquadramento {
    val fator = fatorDoMapa(density)
    return enquadrar(
        pontosParaEnquadrar(localizacao),
        (larguraTela / fator).toDouble(),
        (alturaTela / fator).toDouble(),
        margemTopo = (ALTURA_ALFINETE.toPx() / fator).toDouble(),
    )
}

/**
 * O mapa desenhado com tiles (os mesmos endereços do web), a rota e os marcadores por cima.
 * [interativo]: arrastar, pinça, toque duplo amplia e [aoTocar] devolve o ponto tocado; sem
 * isso, é a imagem fixa da bolha. A atribuição exigida pelos tiles fica no canto.
 */
@Composable
internal fun MapaDeTiles(
    estado: EstadoMapa,
    localizacao: Localizacao?,
    modifier: Modifier = Modifier,
    camada: CamadaMapa = CamadaMapa.RUAS,
    interativo: Boolean = false,
    aoTocar: ((PontoMapa) -> Unit)? = null,
) {
    val fator = fatorDoMapa(LocalDensity.current.density)
    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Tudo em pixels do mapa; na hora de desenhar, vezes o fator.
        val largura = constraints.maxWidth / fator
        val altura = constraints.maxHeight / fator
        val gestos = if (interativo) {
            Modifier
                .pointerInput(estado, fator) {
                    detectTransformGestures { centroide, pan, zoom, _ ->
                        val foco = Offset(centroide.x / fator - largura / 2, centroide.y / fator - altura / 2)
                        estado.mover(pan.x / fator, pan.y / fator, zoom, foco)
                    }
                }
                .pointerInput(estado, aoTocar, fator) {
                    detectTapGestures(
                        onDoubleTap = { estado.mover(0f, 0f, 2f, Offset(it.x / fator - largura / 2, it.y / fator - altura / 2)) },
                        onTap = { toque -> aoTocar?.invoke(estado.pontoEm(toque / fator, largura, altura)) },
                    )
                }
        } else {
            Modifier
        }
        Box(Modifier.fillMaxSize().then(gestos)) {
            // Cada camada no seu zoom nativo máximo (acima dele, o tile é ampliado), como o Leaflet.
            camada.enderecos.forEach { (modelo, zoomNativo) -> CamadaDeTiles(estado, modelo, zoomNativo, largura, altura, fator) }
            if (localizacao != null) Sobreposicao(estado, localizacao, largura, altura, fator)
        }
        Text(
            stringResource(if (camada == CamadaMapa.RUAS) R.string.atribuicao_osm else R.string.atribuicao_esri),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 3.dp),
        )
    }
}

/** Põe o filho em ([x], [y]) com [lado] × [lado] pixels, sem depender do tamanho do pai (o tile pode passar dele). */
private fun Modifier.noPonto(x: Int, y: Int, lado: Int): Modifier = layout { medivel, _ ->
    val colocavel = medivel.measure(Constraints.fixed(lado, lado))
    layout(0, 0) { colocavel.place(x, y) }
}

@Composable
private fun CamadaDeTiles(estado: EstadoMapa, modelo: String, zoomNativo: Int, largura: Float, altura: Float, fator: Float) {
    val zoomTiles = floor(estado.zoom).toInt().coerceIn(0, zoomNativo)
    val escala = 2.0.pow(estado.zoom - zoomTiles)
    val lado = TAMANHO_TILE * escala
    // Canto de cima à esquerda da tela, em pixels do mapa no zoom dos tiles (já ampliados pela escala).
    val esquerda = xNoMapa(estado.lng, zoomTiles.toDouble()) * escala - largura / 2
    val topo = yNoMapa(estado.lat, zoomTiles.toDouble()) * escala - altura / 2
    val primeiroX = floor(esquerda / lado).toInt()
    val ultimoX = floor((esquerda + largura) / lado).toInt()
    val primeiroY = floor(topo / lado).toInt().coerceAtLeast(0)
    val ultimoY = floor((topo + altura) / lado).toInt().coerceAtMost((1 shl zoomTiles) - 1)
    // Um pixel a mais: sem riscos entre os tiles ampliados.
    val ladoTela = ceil(lado * fator).toInt() + 1
    for (x in primeiroX..ultimoX) {
        for (y in primeiroY..ultimoY) {
            AsyncImage(
                model = enderecoDoTile(modelo, zoomTiles, x, y),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.noPonto(
                    ((x * lado - esquerda) * fator).roundToInt(),
                    ((y * lado - topo) * fator).roundToInt(),
                    ladoTela,
                ),
            )
        }
    }
}

/** A rota (ou a reta tracejada) e os marcadores: o local na cor principal; o trajeto com A e B. */
@Composable
private fun Sobreposicao(estado: EstadoMapa, localizacao: Localizacao, largura: Float, altura: Float, fator: Float) {
    val principal = MaterialTheme.colorScheme.primary
    // A e B nas cores das chamadas (verde e vermelho do FMX, docs/design/cores.md), como o verde e o vermelho do web.
    val corA = ConversaTema.cores.chamadaAtender
    val corB = ConversaTema.cores.chamadaEncerrar
    val medidor = rememberTextMeasurer()
    val linha = remember(localizacao.rota) { localizacao.rota?.let(::decodificarRota).orEmpty() }
    Canvas(Modifier.fillMaxSize()) {
        val zoom = estado.zoom
        val cx = xNoMapa(estado.lng, zoom)
        val cy = yNoMapa(estado.lat, zoom)
        fun naTela(lat: Double, lng: Double) = Offset(
            ((xNoMapa(lng, zoom) - cx + largura / 2) * fator).toFloat(),
            ((yNoMapa(lat, zoom) - cy + altura / 2) * fator).toFloat(),
        )
        if (linha.size >= 2) {
            val caminho = Path()
            linha.forEachIndexed { i, (lat, lng) ->
                val p = naTela(lat, lng)
                if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y)
            }
            drawPath(
                caminho,
                principal.copy(alpha = 0.85f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else if (localizacao.trajeto) {
            val a = localizacao.pontos[0]
            val b = localizacao.pontos[1]
            drawLine(
                principal.copy(alpha = 0.85f),
                naTela(a.lat, a.lng),
                naTela(b.lat, b.lng),
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx())),
            )
        }
        localizacao.pontos.forEachIndexed { i, ponto ->
            val (cor, letra) = when {
                !localizacao.trajeto -> principal to null
                i == 0 -> corA to "A"
                else -> corB to "B"
            }
            alfinete(naTela(ponto.lat, ponto.lng), cor, letra, medidor)
        }
    }
}

/** O alfinete do web (28 × 38, ponta no ponto): letra branca no trajeto, bolinha branca no local. */
private fun DrawScope.alfinete(ponta: Offset, cor: Color, letra: String?, medidor: TextMeasurer) {
    val raio = 12.dp.toPx()
    val centro = Offset(ponta.x, ponta.y - 24.dp.toPx())
    val corpo = Path().apply {
        moveTo(ponta.x, ponta.y)
        lineTo(centro.x - raio * 0.75f, centro.y + raio * 0.65f)
        arcTo(Rect(centro, raio), startAngleDegrees = 140f, sweepAngleDegrees = 260f, forceMoveTo = false)
        close()
    }
    drawPath(corpo, cor)
    drawPath(corpo, Color.White, style = Stroke(width = 2.dp.toPx()))
    if (letra == null) {
        drawCircle(Color.White, radius = 4.5.dp.toPx(), center = centro)
    } else {
        val texto = medidor.measure(letra, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        drawText(texto, topLeft = Offset(centro.x - texto.size.width / 2f, centro.y - texto.size.height / 2f))
    }
}
