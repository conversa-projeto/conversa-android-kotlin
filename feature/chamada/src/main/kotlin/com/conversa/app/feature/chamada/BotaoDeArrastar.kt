package com.conversa.app.feature.chamada

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quanto o dedo precisa se afastar do ponto onde tocou para o botão agir. */
internal val LIMIAR_ARRASTO = 140.dp

/**
 * Quanto o anel fica além da borda do botão. Menor que o [LIMIAR_ARRASTO]: o círculo cresce
 * mais devagar que o dedo, e é preciso arrastar com vontade.
 */
private val ALCANCE_DO_ANEL = 115.dp

/**
 * Botão da chamada recebida que só age ao ser **arrastado**, para qualquer lado, como o
 * discador da Samsung: ao tocar, ele cresce e aparece o anel de até onde arrastar; o círculo
 * cresce com o dedo e, ao chegar no anel, vibra e age. Só tocar não faz nada (não atende
 * no bolso). Para o TalkBack, o toque duplo age (a ação de clique da acessibilidade).
 */
@Composable
internal fun BotaoDeArrastar(
    icone: ImageVector,
    descricao: String,
    cor: Color,
    aoConcluir: () -> Unit,
    modifier: Modifier = Modifier,
    /** Avisa quando o dedo encosta (true) e solta (false), para a tela esconder o outro botão. */
    aoSegurar: (Boolean) -> Unit = {},
    tamanho: Dp = 72.dp,
) {
    val limiar = with(LocalDensity.current) { LIMIAR_ARRASTO.toPx() }
    val alcance = with(LocalDensity.current) { ALCANCE_DO_ANEL.toPx() }
    val concluir by rememberUpdatedState(aoConcluir)
    val segurar by rememberUpdatedState(aoSegurar)
    val haptico = LocalHapticFeedback.current
    val escopo = rememberCoroutineScope()
    var arrasto by remember { mutableFloatStateOf(0f) }
    var pressionado by remember { mutableStateOf(false) }
    var concluido by remember { mutableStateOf(false) }
    var volta by remember { mutableStateOf<Job?>(null) }
    val presenca by animateFloatAsState(if (pressionado || concluido) 1f else 0f, tween(DURACAO_TOQUE_MS), label = "presenca")
    val progresso = (arrasto / limiar).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(tamanho)
            .drawBehind {
                if (presenca == 0f) return@drawBehind
                val raio = size.minDimension / 2
                // O anel de até onde arrastar e o círculo que cresce com o dedo até preenchê-lo.
                drawCircle(cor.copy(alpha = ALFA_ANEL * presenca), radius = raio + alcance, style = Stroke(1.5.dp.toPx()))
                drawCircle(
                    cor.copy(alpha = lerp(ALFA_CIRCULO_INICIO, ALFA_CIRCULO_FIM, progresso) * presenca),
                    radius = lerp(raio * (1f + CRESCIMENTO_AO_TOCAR * presenca), raio + alcance, progresso),
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val toque = awaitFirstDown(requireUnconsumed = false)
                    if (concluido) return@awaitEachGesture
                    volta?.cancel()
                    pressionado = true
                    segurar(true)
                    val inicio = toque.position
                    while (true) {
                        val dedo = awaitPointerEvent().changes.firstOrNull { it.id == toque.id }
                        if (dedo == null || !dedo.pressed) break
                        dedo.consume()
                        arrasto = (dedo.position - inicio).getDistance()
                        if (arrasto >= limiar) {
                            concluido = true
                            haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                            concluir()
                            break
                        }
                    }
                    pressionado = false
                    if (!concluido) segurar(false)
                    volta = escopo.launch {
                        // Concluído: fica cheio enquanto a tela muda; se ela não mudar, volta a valer.
                        if (concluido) delay(ESPERA_DEPOIS_DE_CONCLUIR_MS)
                        animate(arrasto, 0f) { valor, _ -> arrasto = valor }
                        if (concluido) {
                            concluido = false
                            segurar(false)
                        }
                    }
                }
            }
            .semantics {
                contentDescription = descricao
                role = Role.Button
                onClick(label = descricao) {
                    concluir()
                    true
                }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    val escala = 1f + CRESCIMENTO_AO_TOCAR * presenca
                    scaleX = escala
                    scaleY = escala
                }
                .clip(CircleShape)
                .background(cor),
        ) {
            Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(tamanho * 0.45f))
        }
    }
}

private const val DURACAO_TOQUE_MS = 150
private const val ESPERA_DEPOIS_DE_CONCLUIR_MS = 1_500L
private const val CRESCIMENTO_AO_TOCAR = 0.15f
private const val ALFA_ANEL = 0.6f
private const val ALFA_CIRCULO_INICIO = 0.25f
private const val ALFA_CIRCULO_FIM = 0.45f
