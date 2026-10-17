package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Duração e onda do áudio (ANX-10), com as regras do `utils/dadosAudio.ts` do web. */
class DadosAudioTest {
    @Test
    fun `picos - o maior de cada trecho, relativo ao mais alto`() {
        val amostras = floatArrayOf(0.1f, 0.2f, 0.5f, 0.4f, 0.05f, 0.25f)
        assertThat(picos(amostras, barras = 3)).containsExactly(40, 100, 50).inOrder()
        // Silêncio: tudo zero, sem dividir por zero.
        assertThat(picos(FloatArray(10), barras = 4)).containsExactly(0, 0, 0, 0)
        // Menos amostras que barras: as que sobram ficam em zero (como o web).
        assertThat(picos(floatArrayOf(0.5f, 1f), barras = 4)).containsExactly(50, 100, 0, 0).inOrder()
        assertThat(picos(FloatArray(1000) { it / 1000f })).hasSize(BARRAS_ONDA)
    }

    @Test
    fun `duracao com 2 casas e intensidade como texto`() {
        assertThat(arredondarDuracao(44.954)).isEqualTo(44.95)
        assertThat(intensidadeDoTexto(intensidadeParaTexto(listOf(12, 0, 100)))).containsExactly(12, 0, 100).inOrder()
        assertThat(intensidadeDoTexto("")).isNull()
        assertThat(intensidadeDoTexto(null)).isNull()
    }

    @Test
    fun `velocidade - 1x, 1,5x, 2x, 3x e volta, e o valor estranho vira 1x`() {
        assertThat(proximaVelocidade(1f)).isEqualTo(1.5f)
        assertThat(proximaVelocidade(3f)).isEqualTo(1f)
        assertThat(proximaVelocidade(1.25f)).isEqualTo(1f)
        assertThat(VELOCIDADES_AUDIO.map(::rotuloVelocidade)).containsExactly("1x", "1,5x", "2x", "3x").inOrder()
        assertThat(velocidadeValida(1.25f)).isEqualTo(1f)
        assertThat(velocidadeValida(2f)).isEqualTo(2f)
    }

    @Test
    fun `tempo do player - atual sobre total, como o web`() {
        assertThat(textoDoTempoDoAudio(null, 12)).isEqualTo("00:12")
        assertThat(textoDoTempoDoAudio(5, 12)).isEqualTo("00:05 / 00:12")
        assertThat(textoDoTempoDoAudio(5, null)).isEqualTo("00:05")
        assertThat(textoDoTempoDoAudio(null, null)).isEqualTo("--:--")
    }
}
