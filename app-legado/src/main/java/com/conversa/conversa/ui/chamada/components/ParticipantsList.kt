package com.conversa.conversa.ui.chamada.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.conversa.ui.chamada.ParticipanteUI

@Composable
fun ParticipantsList(
    participants: List<ParticipanteUI>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(participants) { participant ->
            ParticipantItem(participant = participant)

            if (participant != participants.last()) {
                HorizontalDivider(
                    color = Color(0xFF334155),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ParticipantItem(
    participant: ParticipanteUI,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar com indicador de fala
        ParticipantAvatar(
            name = participant.nome,
            isSpeaking = participant.isFalando,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Informações do participante
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = participant.nome,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = participant.status,
                fontSize = 13.sp,
                color = when (participant.status) {
                    "Conectado" -> Color(0xFF22C55E)
                    "Aguardando..." -> Color(0xFFFBBF24)
                    else -> Color(0xFF94A3B8)
                }
            )
        }

        // Indicador de mute local
        if (participant.mutadoLocalmente) {
            Text(
                text = "🔇",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
