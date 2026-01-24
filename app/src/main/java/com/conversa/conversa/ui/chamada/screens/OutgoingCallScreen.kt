package com.conversa.conversa.ui.chamada.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.conversa.ui.chamada.components.ParticipantAvatar

/**
 * Tela de chamada sainte - aguardando o outro participante atender
 */
@Composable
fun OutgoingCallScreen(
    callerName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Seção superior - Avatar e informações
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                // Avatar com animação de flutuação
                val infiniteTransition = rememberInfiniteTransition(label = "float")
                val floatOffset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "floatOffset"
                )

                Box(
                    modifier = Modifier.offset(y = floatOffset.dp)
                ) {
                    ParticipantAvatar(
                        name = callerName,
                        isSpeaking = false,
                        size = 112.dp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Nome do destinatário
                Text(
                    text = callerName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Texto "Chamando..." com animação
                CallingText()

                Spacer(modifier = Modifier.height(24.dp))

                // Pontos animados
                AnimatedDots()
            }

            // Botão cancelar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(elevation = 12.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar chamada",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Cancelar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CallingText() {
    val infiniteTransition = rememberInfiniteTransition(label = "calling")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "callingAlpha"
    )

    Text(
        text = "Chamando...",
        fontSize = 18.sp,
        color = Color(0xFF94A3B8).copy(alpha = alpha)
    )
}

@Composable
private fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        repeat(6) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1200,
                        easing = LinearEasing,
                        delayMillis = index * 200
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dotScale$index"
            )

            Box(
                modifier = Modifier
                    .size((6 + scale * 4).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6).copy(alpha = scale))
            )

            if (index < 5) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
