package com.conversa.conversa.ui.chamada

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.conversa.conversa.service.ChamadaService
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
    onMinimize: () -> Unit = {},
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

    // Calcula o nome do outro participante baseado nos dados observados
    // Isso garante que o Compose re-renderiza quando chamada é atualizada
    val outroParticipanteNome = chamada?.usuarios
        ?.filter { it.usuarioId != chamadaService.getUsuarioIdAtual() }
        ?.let { outros ->
            when {
                outros.size > 1 -> "Chamada em Grupo"
                outros.isNotEmpty() -> outros.first().usuarioNome
                else -> "Carregando..."
            }
        } ?: "Carregando..."

    when (estado) {
        ChamadaService.EstadoChamadaService.RECEBENDO_CHAMADA -> {
            // Tela de chamada recebida - mostra nome do outro participante
            IncomingCallScreen(
                callerName = outroParticipanteNome,
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

        ChamadaService.EstadoChamadaService.INICIANDO_CHAMADA,
        ChamadaService.EstadoChamadaService.CONECTANDO_AUDIO -> {
            // Tela de chamada sainte - mostra nome do outro participante
            OutgoingCallScreen(
                callerName = outroParticipanteNome,
                onCancel = {
                    chamadaService.finalizarChamada()
                    onFinish()
                },
                modifier = modifier
            )
        }

        ChamadaService.EstadoChamadaService.EM_CHAMADA -> {
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
                onMinimize = onMinimize,
                modifier = modifier
            )
        }

        ChamadaService.EstadoChamadaService.IDLE,
        ChamadaService.EstadoChamadaService.FINALIZANDO -> {
            // Chamada finalizada - fechar Activity
            onFinish()
        }
    }
}
