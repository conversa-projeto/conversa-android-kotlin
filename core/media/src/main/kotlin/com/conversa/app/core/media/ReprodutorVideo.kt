package com.conversa.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Vídeo com os controles do Media3 (ANX-05, TODO 4.4), para o visualizador. [uri] é a
 * URL assinada (o ExoPlayer lê por partes, não baixa tudo antes) ou o arquivo local.
 * Só toca com [ativo] (a página visível do visualizador); ao sair, o player é liberado.
 * Pede o foco de áudio: um áudio da conversa que estiver tocando pausa sozinho.
 */
@Composable
fun ReprodutorVideo(uri: String, ativo: Boolean, aoFalhar: () -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(contexto)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                // handleAudioFocus
                true,
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
    }
    DisposableEffect(player) {
        val ouvinte = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = aoFalhar()
        }
        player.addListener(ouvinte)
        onDispose {
            player.removeListener(ouvinte)
            player.release()
        }
    }
    // Começa a tocar ao chegar na página e pausa ao sair dela.
    LaunchedEffect(player, ativo) { player.playWhenReady = ativo }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = modifier,
    )
}
