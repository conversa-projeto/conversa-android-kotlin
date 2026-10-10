package com.conversa.app.core.ui.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema

/** Larguras das linhas de um parágrafo (frações da largura), como as do texto que vai aparecer. */
val LINHAS_DE_TEXTO = listOf(1f, 0.92f, 0.97f, 0.64f)

/**
 * Esqueleto de carregamento ("shimmer"): formas cinza no lugar do conteúdo, que pulsam de leve
 * enquanto um brilho passa na diagonal. Para o que demora a aparecer (Markdown, diagrama).
 * Cores só com tokens (`docs/design/cores.md`): formas em `divisorLista`; o brilho é o `surface`
 * no tema claro e o `onSurface` fraco no escuro (nos dois, mais claro que a forma).
 */
@Composable
fun EsqueletoCarregando(
    modifier: Modifier = Modifier,
    /** Uma forma por linha, com a largura em fração; uma linha só e alta vira um bloco. */
    linhas: List<Float> = LINHAS_DE_TEXTO,
    alturaLinha: Dp = 12.dp,
    espaco: Dp = 10.dp,
) {
    val descricao = stringResource(R.string.carregando)
    val forma = ConversaTema.cores.divisorLista
    val fundo = MaterialTheme.colorScheme.surface
    val brilho = if (fundo.luminance() > 0.5f) fundo.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val transicao = rememberInfiniteTransition(label = "esqueleto")
    val avanco by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DURACAO_BRILHO_MS, easing = LinearEasing)),
        label = "brilho",
    )
    val opacidade by transicao.animateFloat(
        initialValue = 1f,
        targetValue = OPACIDADE_MINIMA,
        animationSpec = infiniteRepeatable(tween(DURACAO_PULSO_MS), RepeatMode.Reverse),
        label = "pulso",
    )
    val altura = alturaLinha * linhas.size + espaco * (linhas.size - 1).coerceAtLeast(0)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(altura)
            .semantics { contentDescription = descricao }
            // Fora da tela: o brilho só pinta por cima das formas (SrcAtop).
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = opacidade
            },
    ) {
        val alturaPx = alturaLinha.toPx()
        val passo = alturaPx + espaco.toPx()
        val canto = CornerRadius(minOf(alturaPx / 2, 6.dp.toPx()))
        linhas.forEachIndexed { indice, fracao ->
            drawRoundRect(forma, Offset(0f, indice * passo), Size(size.width * fracao, alturaPx), canto)
        }
        // A faixa de brilho entra pela esquerda e sai pela direita, inclinada.
        val faixa = size.width * LARGURA_FAIXA
        val x = -faixa + avanco * (size.width + faixa * 2)
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, brilho, Color.Transparent),
                start = Offset(x - faixa / 2, 0f),
                end = Offset(x + faixa / 2, size.height.coerceAtMost(faixa)),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }
}

private const val DURACAO_BRILHO_MS = 1_200
private const val DURACAO_PULSO_MS = 700
private const val OPACIDADE_MINIMA = 0.55f
private const val LARGURA_FAIXA = 0.45f

@Preview(showBackground = true)
@Composable
private fun EsqueletoCarregandoPreview() {
    ConversaTema { EsqueletoCarregando() }
}
