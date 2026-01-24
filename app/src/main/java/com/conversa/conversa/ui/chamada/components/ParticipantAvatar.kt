package com.conversa.conversa.ui.chamada.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ParticipantAvatar(
    name: String,
    isSpeaking: Boolean = false,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = if (isSpeaking) {
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            infiniteRepeatable(
                animation = tween(0),
                repeatMode = RepeatMode.Restart
            )
        },
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Borda pulsante verde quando está falando
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E).copy(alpha = 0.3f))
            )
        }

        // Avatar com gradiente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSpeaking) 4.dp else 0.dp)
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
                text = name.take(1).uppercase(),
                fontSize = (size.value / 2.5f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Indicador VAD (Voice Activity Detection)
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(size / 4)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
        }
    }
}
