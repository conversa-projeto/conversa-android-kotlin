package com.conversa.conversa.ui.chamada.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.conversa.ui.chamada.ParticipanteUI
import com.conversa.conversa.ui.chamada.components.*

/**
 * Tela de chamada ativa - suporta tanto chamada 1:1 quanto em grupo.
 * O layout se adapta automaticamente baseado no número de participantes.
 */
@Composable
fun ActiveCallScreen(
    participants: List<ParticipanteUI>,
    timerText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Grupo = 3+ participantes (eu + 2 outros)
    // Com 2 participantes (eu + 1), usa layout simples 1:1
    val isGroupCall = participants.size > 2

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
        if (isGroupCall) {
            // Layout para chamada em grupo
            GroupCallLayout(
                participants = participants,
                timerText = timerText,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onAddParticipant = onAddParticipant,
                onEndCall = onEndCall
            )
        } else {
            // Layout para chamada 1:1
            SimpleCallLayout(
                participant = participants.firstOrNull(),
                timerText = timerText,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onAddParticipant = onAddParticipant,
                onEndCall = onEndCall
            )
        }
    }
}

@Composable
private fun SimpleCallLayout(
    participant: ParticipanteUI?,
    timerText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit
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
            // Avatar grande
            ParticipantAvatar(
                name = participant?.nome ?: "Desconhecido",
                isSpeaking = participant?.isFalando ?: false,
                size = 112.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Nome do participante
            Text(
                text = participant?.nome ?: "Desconhecido",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Timer
            CallTimer(timerText = timerText)
        }

        // Controles
        CallControls(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onAddParticipant = onAddParticipant,
            onEndCall = onEndCall,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun GroupCallLayout(
    participants: List<ParticipanteUI>,
    timerText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header com título e timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chamada em Grupo",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            CallTimer(timerText = timerText)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de participantes (scrollável)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ParticipantsList(
                participants = participants,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Controles
        CallControls(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onAddParticipant = onAddParticipant,
            onEndCall = onEndCall,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
    }
}
