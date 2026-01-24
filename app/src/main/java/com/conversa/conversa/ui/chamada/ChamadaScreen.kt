package com.conversa.conversa.ui.chamada

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.conversa.conversa.service.ChamadaService
import com.conversa.conversa.service.EstadoChamadaService
import com.conversa.conversa.ui.chamada.screens.ActiveCallScreen
import com.conversa.conversa.ui.chamada.screens.OutgoingCallScreen

/**
 * Composable principal que decide qual tela de chamada mostrar baseado no estado.
 * Observa o ChamadaService e renderiza a tela apropriada.
 */
@Composable
fun ChamadaScreen(
    chamadaService: ChamadaService?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (chamadaService == null) {
        // Service não conectado ainda
        onFinish()
        return
    }

    val estado by chamadaService.estadoFlow.collectAsState()
    val chamada by chamadaService.chamadaAtualFlow.collectAsState()
    val timer by chamadaService.timerFlow.collectAsState()

    when (estado) {
        EstadoChamadaService.RECEBENDO_CHAMADA -> {
            // IncomingCallScreen já existe e é usado no IncomingCallFragment
            // Aqui seria chamado via IncomingCallScreen diretamente se necessário
            IncomingCallScreen(
                callerName = chamada?.usuarios?.firstOrNull()?.usuarioNome ?: "Desconhecido",
                callerDescription = if (chamada?.tipo == 2) "Chamada em Grupo" else "Chamada de voz",
                onAccept = {
                    chamadaService.aceitarChamada()
                },
                onDecline = {
                    chamadaService.recusarChamada()
                    onFinish()
                },
                modifier = modifier
            )
        }

        EstadoChamadaService.INICIANDO_CHAMADA,
        EstadoChamadaService.CONECTANDO_AUDIO -> {
            // Tela de chamada sainte
            OutgoingCallScreen(
                callerName = chamada?.usuarios?.firstOrNull()?.usuarioNome ?: "Desconhecido",
                onCancel = {
                    chamadaService.finalizarChamada()
                    onFinish()
                },
                modifier = modifier
            )
        }

        EstadoChamadaService.EM_CHAMADA -> {
            // Tela de chamada ativa
            val participantes = chamadaService.getParticipantes()

            ActiveCallScreen(
                participants = participantes,
                timerText = timer,
                isMuted = chamadaService.isMuted(),
                isSpeakerOn = chamadaService.isSpeakerOn(),
                onToggleMute = {
                    chamadaService.toggleMute(!chamadaService.isMuted())
                },
                onToggleSpeaker = {
                    chamadaService.toggleSpeaker(!chamadaService.isSpeakerOn())
                },
                onAddParticipant = {
                    // API futura - botão sempre desabilitado
                },
                onEndCall = {
                    chamadaService.finalizarChamada()
                    onFinish()
                },
                modifier = modifier
            )
        }

        EstadoChamadaService.IDLE,
        EstadoChamadaService.FINALIZANDO -> {
            // Chamada finalizada - fechar Activity
            onFinish()
        }
    }
}
