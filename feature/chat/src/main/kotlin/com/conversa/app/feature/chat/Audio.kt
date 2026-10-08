package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.StatusTranscricao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.formatarDuracao
import com.conversa.app.core.model.local
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * O áudio da conversa ([AudioNaConversa]) chega às bolhas por aqui, como fluxo: só as
 * bolhas de áudio leem, então a posição (várias vezes por segundo) não recompõe a lista.
 */
val LocalAudio = staticCompositionLocalOf<StateFlow<AudioNaConversa>> { MutableStateFlow(AudioNaConversa()) }

/**
 * Player na bolha (ANX-10): play/pause, barra com arraste e `mm:ss`. Tipo 4 (arquivo)
 * mostra o nome; tipo 5 (gravação), só o player. Áudio de outra pessoa ainda não ouvido:
 * botão verde (cor `waveform` do FMX).
 */
@Composable
fun PlayerNaBolha(mensagem: Mensagem, conteudo: Conteudo, propria: Boolean, cor: Color, acoes: AcoesBolha) {
    val audio by LocalAudio.current.collectAsStateWithLifecycle()
    val chave = chaveDoAudio(mensagem, conteudo)
    val atual = audio.chave == chave
    val tocando = atual && audio.tocando
    val baixando = audio.baixando == chave
    val duracao = audio.duracoes[chave]
    val posicao = if (atual) audio.posicaoMs else 0
    val naoOuvido = !propria && !mensagem.reproduzida
    // Enquanto arrasta, a barra segue o dedo; o player só pula quando solta.
    var arrastando by remember(chave) { mutableStateOf<Float?>(null) }
    val fracao = arrastando ?: if (duracao != null && duracao > 0) (posicao.toFloat() / duracao).coerceIn(0f, 1f) else 0f

    Column(Modifier.widthIn(min = 220.dp)) {
        if (conteudo.tipo == TipoConteudo.AUDIO && conteudo.nome.isNotBlank()) {
            Text(
                conteudo.nome,
                color = cor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { acoes.aoAlternarAudio(mensagem, conteudo) }, enabled = !baixando) {
                when {
                    baixando -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    tocando -> Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pausar_audio), tint = cor)
                    else -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.ouvir_audio),
                        tint = if (naoOuvido) ConversaTema.cores.waveform else cor,
                    )
                }
            }
            BarraAudio(
                fracao = fracao,
                // Só dá para pular no áudio que está no player (como o web).
                habilitada = atual,
                corTrilha = cor.copy(alpha = 0.2f),
                aoArrastar = { arrastando = it },
                aoSoltar = {
                    arrastando?.let { acoes.aoBuscarAudio(chave, it) }
                    arrastando = null
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }
        Text(
            // Tocando ou parado no meio: a posição; senão, a duração (como o web).
            formatarDuracao(
                when {
                    tocando || posicao > 0 -> posicao / 1000
                    duracao != null -> duracao / 1000
                    else -> null
                },
            ),
            color = cor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 48.dp),
        )
        if (!conteudo.local) TranscricaoNaBolha(conteudo, audio, cor, acoes)
    }
}

/**
 * Transcrição embaixo do áudio (ANX-12, textos do web): o texto (ou "nenhuma fala"),
 * "Transcrevendo..." com a consulta a cada 3 s, ou o botão "Transcrever" / "Tentar de
 * novo". Sem transcritor no servidor, o botão some.
 */
@Composable
private fun TranscricaoNaBolha(conteudo: Conteudo, audio: AudioNaConversa, cor: Color, acoes: AcoesBolha) {
    val identificador = conteudo.conteudo
    Column(Modifier.padding(start = 12.dp, top = 2.dp).widthIn(max = 260.dp)) {
        when (conteudo.transcricaoStatus) {
            StatusTranscricao.CONCLUIDA -> Text(
                conteudo.transcricao.ifBlank { stringResource(R.string.nenhuma_fala_reconhecida) },
                color = cor.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
            )
            StatusTranscricao.PROCESSANDO -> {
                LaunchedEffect(identificador) { acoes.aoAcompanharTranscricao(identificador) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = cor)
                    Text(
                        stringResource(R.string.transcrevendo),
                        color = cor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            else -> if (!audio.transcricaoDesligada) {
                val pedindo = identificador in audio.pedindoTranscricao
                Text(
                    stringResource(
                        if (conteudo.transcricaoStatus == StatusTranscricao.ERRO) R.string.transcrever_de_novo else R.string.transcrever,
                    ),
                    color = cor.copy(alpha = if (pedindo) 0.4f else 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable(enabled = !pedindo, role = Role.Button) {
                        acoes.aoTranscrever(identificador)
                    }.padding(vertical = 4.dp),
                )
            }
        }
        audio.errosTranscricao[identificador]?.let {
            Text(it, color = cor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Barra fina de progresso (como a do web): tocar ou arrastar escolhe a posição. A área
 * de toque é mais alta que a barra desenhada. Leitores de tela veem um controle de valor.
 */
@Composable
internal fun BarraAudio(
    fracao: Float,
    habilitada: Boolean,
    corTrilha: Color,
    aoArrastar: (Float) -> Unit,
    aoSoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corCheia = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fracao, 0f..1f)
                if (habilitada) {
                    setProgress { valor ->
                        aoArrastar(valor.coerceIn(0f, 1f))
                        aoSoltar()
                        true
                    }
                }
            }
            .pointerInput(habilitada) {
                if (!habilitada) return@pointerInput
                detectTapGestures { ponto ->
                    aoArrastar((ponto.x / size.width).coerceIn(0f, 1f))
                    aoSoltar()
                }
            }
            .pointerInput(habilitada) {
                if (!habilitada) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { aoArrastar((it.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = aoSoltar,
                    onDragCancel = aoSoltar,
                ) { mudanca, _ -> aoArrastar((mudanca.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(corTrilha))
        Box(Modifier.fillMaxWidth(fracao).height(4.dp).clip(RoundedCornerShape(2.dp)).background(corCheia))
    }
}
