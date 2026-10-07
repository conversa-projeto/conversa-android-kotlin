package com.conversa.app.core.media

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** O player de verdade (Media3) no Robolectric: só o caminho de erro, que não depende de decodificar áudio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerMedia3Test {
    @Test
    fun `parar sem nunca ter tocado nao quebra`() {
        val player = PlayerMedia3(ApplicationProvider.getApplicationContext())
        player.pausar()
        player.buscar(1_000)
        player.parar()
        assertThat(player.estado.value).isEqualTo(EstadoAudio())
    }

    @Test
    fun `arquivo que nao existe avisa a falha com a chave e volta ao estado inicial`() {
        val player = PlayerMedia3(ApplicationProvider.getApplicationContext())
        val falhas = mutableListOf<String>()
        val coleta = CoroutineScope(Dispatchers.Unconfined)
        coleta.launch { player.falhas.collect { falhas += it } }

        player.tocar("42:7:1", File("/nao/existe/audio.wav").toURI().toString())
        assertThat(player.estado.value.chave).isEqualTo("42:7:1")

        // O erro vem da thread de reprodução do ExoPlayer e é entregue na principal.
        val limite = System.currentTimeMillis() + 10_000
        while (falhas.isEmpty() && System.currentTimeMillis() < limite) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }

        assertThat(falhas).containsExactly("42:7:1")
        assertThat(player.estado.value).isEqualTo(EstadoAudio())
        coleta.cancel()
    }
}
