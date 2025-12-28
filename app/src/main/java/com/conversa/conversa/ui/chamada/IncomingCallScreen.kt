package com.conversa.conversa.ui.chamada

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerDescription: String = "Chamada em Grupo",
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    var acceptButtonScale by remember { mutableFloatStateOf(1f) }
    var declineButtonScale by remember { mutableFloatStateOf(1f) }
    var acceptButtonOpacity by remember { mutableFloatStateOf(1f) }
    var declineButtonOpacity by remember { mutableFloatStateOf(1f) }
    var isActionTriggered by remember { mutableStateOf(false) }

    val animatedAcceptScale by animateFloatAsState(
        targetValue = acceptButtonScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "acceptScale"
    )

    val animatedDeclineScale by animateFloatAsState(
        targetValue = declineButtonScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "declineScale"
    )

    val animatedAcceptOpacity by animateFloatAsState(
        targetValue = acceptButtonOpacity,
        animationSpec = tween(durationMillis = 150),
        label = "acceptOpacity"
    )

    val animatedDeclineOpacity by animateFloatAsState(
        targetValue = declineButtonOpacity,
        animationSpec = tween(durationMillis = 150),
        label = "declineOpacity"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Seção Superior - Informações do Chamador
            CallerInfoSection(
                callerName = callerName,
                callerDescription = callerDescription
            )

            // Seção Inferior - Botões de Ação
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão Recusar
                ActionButton(
                    label = "Recusar",
                    colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626)),
                    iconRotation = 135f,
                    scale = animatedDeclineScale,
                    opacity = animatedDeclineOpacity,
                    enabled = !isActionTriggered,
                    onDrag = { dragAmount ->
                        if (isActionTriggered) return@ActionButton

                        val distance = dragAmount
                        val threshold = 120f
                        val progress = (distance / threshold).coerceIn(0f, 1.2f)

                        // Curva de easing para crescimento mais natural
                        val easedProgress = if (progress < 1f) {
                            progress * progress * (3f - 2f * progress) // Smoothstep
                        } else {
                            1f + (progress - 1f) * 0.5f // Crescimento mais lento após threshold
                        }

                        declineButtonScale = 1f + (easedProgress * 0.6f)
                        acceptButtonOpacity = 1f - (progress * 0.7f).coerceIn(0f, 0.7f)

                        if (distance >= threshold && !isActionTriggered) {
                            isActionTriggered = true
                            onDecline()
                        }
                    },
                    onDragEnd = {
                        if (!isActionTriggered) {
                            declineButtonScale = 1f
                            acceptButtonOpacity = 1f
                        }
                    }
                )

                Spacer(modifier = Modifier.width(80.dp))

                // Botão Atender
                ActionButton(
                    label = "Atender",
                    colors = listOf(Color(0xFF22C55E), Color(0xFF16A34A)),
                    iconRotation = 0f,
                    scale = animatedAcceptScale,
                    opacity = animatedAcceptOpacity,
                    enabled = !isActionTriggered,
                    onDrag = { dragAmount ->
                        if (isActionTriggered) return@ActionButton

                        val distance = dragAmount
                        val threshold = 120f
                        val progress = (distance / threshold).coerceIn(0f, 1.2f)

                        // Curva de easing para crescimento mais natural
                        val easedProgress = if (progress < 1f) {
                            progress * progress * (3f - 2f * progress) // Smoothstep
                        } else {
                            1f + (progress - 1f) * 0.5f // Crescimento mais lento após threshold
                        }

                        acceptButtonScale = 1f + (easedProgress * 0.6f)
                        declineButtonOpacity = 1f - (progress * 0.7f).coerceIn(0f, 0.7f)

                        if (distance >= threshold && !isActionTriggered) {
                            isActionTriggered = true
                            onAccept()
                        }
                    },
                    onDragEnd = {
                        if (!isActionTriggered) {
                            acceptButtonScale = 1f
                            declineButtonOpacity = 1f
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CallerInfoSection(
    callerName: String,
    callerDescription: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 80.dp)
    ) {
        // Avatar com animação de flutuação
        Box(
            modifier = Modifier
                .size(112.dp)
                .offset(y = floatOffset.dp),
            contentAlignment = Alignment.Center
        ) {
            // Avatar com gradiente
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6),
                                Color(0xFFD946EF)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\uD83D\uDC64",
                    fontSize = 48.sp
                )
            }

            // Indicador de status online
            OnlineStatusIndicator(
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nome
        Text(
            text = callerName,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Descrição
        Text(
            text = callerDescription,
            fontSize = 18.sp,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Indicador de chamada
        CallingIndicator()
    }
}

@Composable
private fun OnlineStatusIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(Color(0xFF22C55E))
            .padding(2.dp)
            .clip(CircleShape)
            .background(Color(0xFF0F172A))
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color(0xFF22C55E))
    )
}

@Composable
private fun CallingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "calling")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "callingAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = alpha))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Chamando...",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8).copy(alpha = alpha)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    colors: List<Color>,
    iconRotation: Float,
    scale: Float,
    opacity: Float,
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                alpha = opacity
            }
        ) {
            // Ondas animadas
            WaveAnimations(colors = colors, scale = scale)

            // Botão principal
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .shadow(
                        elevation = (16.dp.value + (scale - 1f) * 8.dp.value).dp,
                        shape = CircleShape,
                        ambientColor = colors[0].copy(alpha = 0.4f),
                        spotColor = colors[0].copy(alpha = 0.6f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = colors,
                            radius = 100f + (scale - 1f) * 50f
                        )
                    )
                    .pointerInput(enabled) {
                        if (enabled) {
                            detectDragGestures(
                                onDragStart = {
                                    totalDragDistance = 0f
                                },
                                onDrag = { change, dragDistance ->
                                    change.consume()
                                    totalDragDistance += abs(dragDistance.x) + abs(dragDistance.y) * 0.5f
                                    onDrag(totalDragDistance)
                                },
                                onDragEnd = {
                                    onDragEnd()
                                    totalDragDistance = 0f
                                },
                                onDragCancel = {
                                    onDragEnd()
                                    totalDragDistance = 0f
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\uD83D\uDCDE",
                    fontSize = (32 + (scale - 1f) * 8).sp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = iconRotation
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.graphicsLayer {
                alpha = opacity
            }
        )
    }
}

@Composable
private fun WaveAnimations(colors: List<Color>, scale: Float = 1f) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    // Criar 4 ondas com delays diferentes
    val waves = listOf(0f, 0.5f, 1f, 1.5f)

    waves.forEach { delay ->
        val waveScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing, delayMillis = (delay * 500).toInt()),
                repeatMode = RepeatMode.Restart
            ),
            label = "waveScale$delay"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing, delayMillis = (delay * 500).toInt()),
                repeatMode = RepeatMode.Restart
            ),
            label = "waveAlpha$delay"
        )

        Canvas(
            modifier = Modifier
                .size(80.dp)
                .scale(waveScale)
                .graphicsLayer {
                    this.alpha = if (scale > 1.1f) 0f else 1f
                }
        ) {
            drawCircle(
                color = colors[0].copy(alpha = alpha * 0.8f),
                radius = size.minDimension / 2,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}
