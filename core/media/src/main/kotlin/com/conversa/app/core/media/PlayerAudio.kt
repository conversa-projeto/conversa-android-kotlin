package com.conversa.app.core.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** O que o player está tocando. [chave] identifica o áudio na tela (ex.: mensagem + ordem). */
data class EstadoAudio(
    val chave: String? = null,
    val tocando: Boolean = false,
    val posicaoMs: Long = 0,
    /** `null` até o player ler a duração do arquivo. */
    val duracaoMs: Long? = null,
)

/**
 * Player único do app (ANX-10, TODO 4.5): **um áudio por vez**. Tocar outro para o
 * anterior. Chamadas (etapa 6) e a saída da conversa chamam [parar].
 */
interface PlayerAudio {
    val estado: StateFlow<EstadoAudio>

    /** Chave do áudio que não pôde ser tocado (arquivo inválido, formato não suportado). */
    val falhas: SharedFlow<String>

    /** Começa [uri] (arquivo local) do início, no lugar do que estiver tocando. */
    fun tocar(chave: String, uri: String)

    fun pausar()

    fun continuar()

    fun buscar(posicaoMs: Long)

    fun parar()
}

/**
 * [PlayerAudio] com o Media3 (ExoPlayer), criado na primeira vez que toca. Pede o foco
 * de áudio (pausa se outro app tocar) e pausa quando o fone é desconectado.
 * Usado sempre na thread principal (exigência do ExoPlayer).
 */
@Singleton
class PlayerMedia3 @Inject constructor(@ApplicationContext private val contexto: Context) : PlayerAudio {
    private val _estado = MutableStateFlow(EstadoAudio())
    override val estado: StateFlow<EstadoAudio> = _estado.asStateFlow()

    private val _falhas = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val falhas: SharedFlow<String> = _falhas.asSharedFlow()

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var acompanhamento: Job? = null
    private var instancia: ExoPlayer? = null

    private val ouvinte = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _estado.update { it.copy(tocando = isPlaying) }
            if (isPlaying) acompanharPosicao() else atualizarPosicao()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = instancia ?: return
            when (playbackState) {
                Player.STATE_READY -> if (player.duration != C.TIME_UNSET) _estado.update { it.copy(duracaoMs = player.duration) }
                // Terminou: volta ao início, parado (como o web).
                Player.STATE_ENDED -> {
                    player.pause()
                    player.seekTo(0)
                    _estado.update { it.copy(tocando = false, posicaoMs = 0) }
                }
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w("Áudio não tocou: %s", error.errorCodeName)
            _estado.value.chave?.let { _falhas.tryEmit(it) }
            parar()
        }
    }

    // Criado na thread principal (quem chama é a tela): o ExoPlayer usa o looper dela.
    private fun player(): ExoPlayer = instancia ?: ExoPlayer.Builder(contexto)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            // handleAudioFocus
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also {
            it.addListener(ouvinte)
            instancia = it
        }

    override fun tocar(chave: String, uri: String) {
        val player = player()
        _estado.value = EstadoAudio(chave = chave)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    override fun pausar() {
        instancia?.pause()
    }

    override fun continuar() {
        instancia?.play()
    }

    override fun buscar(posicaoMs: Long) {
        val player = instancia ?: return
        player.seekTo(posicaoMs.coerceAtLeast(0))
        atualizarPosicao()
    }

    override fun parar() {
        acompanhamento?.cancel()
        instancia?.run {
            stop()
            clearMediaItems()
        }
        _estado.value = EstadoAudio()
    }

    /** O ExoPlayer não avisa a posição: lê a cada [INTERVALO_POSICAO_MS] enquanto toca. */
    private fun acompanharPosicao() {
        acompanhamento?.cancel()
        acompanhamento = escopo.launch {
            while (isActive) {
                atualizarPosicao()
                delay(INTERVALO_POSICAO_MS)
            }
        }
    }

    private fun atualizarPosicao() {
        val player = instancia ?: return
        _estado.update { it.copy(posicaoMs = player.currentPosition) }
        if (!player.isPlaying) acompanhamento?.cancel()
    }

    private companion object {
        const val INTERVALO_POSICAO_MS = 200L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MidiaModulo {
    @Binds
    abstract fun playerAudio(player: PlayerMedia3): PlayerAudio
}
