package com.conversa.conversa.ui.chamada.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.conversa.ui.chamada.ParticipanteUI
import com.conversa.conversa.ui.chamada.components.*
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * Tela de chamada ativa - suporta tanto chamada 1:1 quanto em grupo.
 * O layout se adapta automaticamente baseado no número de participantes.
 *
 * Vídeo: se um participante tiver `videoTrack`, renderiza com SurfaceViewRenderer;
 * caso contrário mostra o avatar. O vídeo local (câmera própria) aparece como PiP.
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
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    localVideoTrack: VideoTrack? = null,
    eglBaseContext: EglBase.Context? = null,
    isVideoOn: Boolean = false,
    onToggleVideo: (() -> Unit)? = null
) {
    // Grupo = 2+ outros participantes (excluindo eu)
    val isGroupCall = participants.size > 1

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
            GroupCallLayout(
                participants = participants,
                timerText = timerText,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isVideoOn = isVideoOn,
                eglBaseContext = eglBaseContext,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleVideo = onToggleVideo,
                onAddParticipant = onAddParticipant,
                onEndCall = onEndCall
            )
        } else {
            SimpleCallLayout(
                participant = participants.firstOrNull(),
                timerText = timerText,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isVideoOn = isVideoOn,
                eglBaseContext = eglBaseContext,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleVideo = onToggleVideo,
                onAddParticipant = onAddParticipant,
                onEndCall = onEndCall
            )
        }

        // Botão de minimizar no canto superior esquerdo
        MinimizeButton(
            onClick = onMinimize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Preview da câmera local (PiP) no canto superior direito
        if (localVideoTrack != null && eglBaseContext != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 104.dp, height = 148.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                WebRTCVideoRenderer(
                    videoTrack = localVideoTrack,
                    eglBaseContext = eglBaseContext,
                    mirror = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SimpleCallLayout(
    participant: ParticipanteUI?,
    timerText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoOn: Boolean,
    eglBaseContext: EglBase.Context?,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: (() -> Unit)?,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit
) {
    val remoteTrack = participant?.videoTrack
    val temVideoRemoto = remoteTrack != null && eglBaseContext != null

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo: vídeo remoto em tela cheia (se houver)
        if (temVideoRemoto) {
            WebRTCVideoRenderer(
                videoTrack = remoteTrack,
                eglBaseContext = eglBaseContext!!,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Seção superior - Avatar (só sem vídeo) e informações
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                if (!temVideoRemoto) {
                    ParticipantAvatar(
                        name = participant?.nome ?: "Desconhecido",
                        isSpeaking = participant?.isFalando ?: false,
                        size = 112.dp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                Text(
                    text = participant?.nome ?: "Desconhecido",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                CallTimer(timerText = timerText)
            }

            CallControls(
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isVideoOn = isVideoOn,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleVideo = onToggleVideo,
                onAddParticipant = onAddParticipant,
                onEndCall = onEndCall,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun GroupCallLayout(
    participants: List<ParticipanteUI>,
    timerText: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isVideoOn: Boolean,
    eglBaseContext: EglBase.Context?,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: (() -> Unit)?,
    onAddParticipant: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        // Grade de participantes: vídeo (se houver) ou avatar
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            participants.forEach { p ->
                ParticipantVideoTile(
                    participant = p,
                    eglBaseContext = eglBaseContext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CallControls(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isVideoOn = isVideoOn,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onToggleVideo = onToggleVideo,
            onAddParticipant = onAddParticipant,
            onEndCall = onEndCall,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
    }
}

/** Tile de um participante: renderiza o vídeo remoto se existir, senão o avatar. */
@Composable
private fun ParticipantVideoTile(
    participant: ParticipanteUI,
    eglBaseContext: EglBase.Context?,
    modifier: Modifier = Modifier
) {
    val track = participant.videoTrack
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
    ) {
        if (track != null && eglBaseContext != null) {
            WebRTCVideoRenderer(
                videoTrack = track,
                eglBaseContext = eglBaseContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ParticipantAvatar(
                    name = participant.nome,
                    isSpeaking = participant.isFalando,
                    size = 72.dp
                )
            }
        }

        Text(
            text = if (participant.status == "Aguardando") "${participant.nome} · aguardando" else participant.nome,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Botão de minimizar a chamada.
 */
@Composable
private fun MinimizeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Minimizar chamada",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
