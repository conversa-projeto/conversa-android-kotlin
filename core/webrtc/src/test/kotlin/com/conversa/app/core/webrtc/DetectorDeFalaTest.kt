package com.conversa.app.core.webrtc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectorDeFalaTest {
    @Test
    fun `acima do limiar fala e segura 400 ms depois de parar`() {
        val detector = DetectorDeFala()

        assertThat(detector.medir(0.01, 0)).isFalse()
        assertThat(detector.medir(0.05, 100)).isTrue()
        // Pausa entre palavras: continua marcado.
        assertThat(detector.medir(0.0, 300)).isTrue()
        assertThat(detector.medir(0.0, 499)).isTrue()
        assertThat(detector.medir(0.0, 500)).isFalse()
        assertThat(detector.medir(0.03, 900)).isTrue()
    }

    @Test
    fun `exatamente no limiar nao conta`() {
        assertThat(DetectorDeFala().medir(0.02, 0)).isFalse()
    }
}
