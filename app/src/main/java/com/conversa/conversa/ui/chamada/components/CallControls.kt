package com.conversa.conversa.ui.chamada.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Linha de controles principais (3 botões)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botão Mute
            CallActionButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Mutado" else "Mudo",
                backgroundColor = if (isMuted) Color(0xFFEF4444) else Color(0xFF4A5568),
                isActive = isMuted,
                onClick = onToggleMute
            )

            // Botão Speaker
            CallActionButton(
                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = if (isSpeakerOn) "Speaker" else "Fone",
                backgroundColor = if (isSpeakerOn) Color(0xFF22C55E) else Color(0xFF4A5568),
                isActive = isSpeakerOn,
                onClick = onToggleSpeaker
            )

            // Botão Adicionar (SEMPRE DESABILITADO)
            CallActionButton(
                icon = Icons.Default.PersonAdd,
                label = "Adicionar",
                backgroundColor = Color(0xFF4A5568),
                enabled = false,
                onClick = onAddParticipant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Botão Encerrar (grande e vermelho)
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Encerrar chamada",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
