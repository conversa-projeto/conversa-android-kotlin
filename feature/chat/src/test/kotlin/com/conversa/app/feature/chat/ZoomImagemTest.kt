package com.conversa.app.feature.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomImagemTest {
    private val tamanho = Size(1000f, 2000f)
    private val centro = Offset(500f, 1000f)

    /** Onde o ponto [p] da imagem (sem zoom) aparece na tela, com a origem do `graphicsLayer` no centro. */
    private fun ZoomImagem.naTela(p: Offset) = centro + (p - centro) * escala + deslocamento

    @Test
    fun `pinca no centro amplia sem deslocar`() {
        val zoom = ZoomImagem().aplicar(centro, 2f, Offset.Zero, tamanho)

        assertThat(zoom.escala).isEqualTo(2f)
        assertThat(zoom.deslocamento).isEqualTo(Offset.Zero)
    }

    @Test
    fun `pinca fora do centro mantem sob os dedos o ponto que estava la`() {
        val foco = Offset(250f, 500f)

        val zoom = ZoomImagem().aplicar(foco, 2f, Offset.Zero, tamanho)

        assertThat(zoom.naTela(foco)).isEqualTo(foco)
        // Pinçar de novo noutro ponto, já ampliado, também o mantém no lugar.
        val outro = Offset(700f, 1200f)
        val pontoDaImagem = centro + (outro - centro - zoom.deslocamento) / zoom.escala
        val maior = zoom.aplicar(outro, 1.5f, Offset.Zero, tamanho)
        assertThat(maior.escala).isEqualTo(3f)
        assertThat(maior.naTela(pontoDaImagem).x).isWithin(0.01f).of(outro.x)
        assertThat(maior.naTela(pontoDaImagem).y).isWithin(0.01f).of(outro.y)
    }

    @Test
    fun `arrasto nao deixa a imagem ampliada sair da tela`() {
        val zoom = ZoomImagem().aplicar(centro, 2f, Offset(5000f, -5000f), tamanho)

        // Em 2x sobra meia tela de cada lado.
        assertThat(zoom.deslocamento).isEqualTo(Offset(500f, -1000f))
    }

    @Test
    fun `voltar a 1x volta ao centro e o zoom para em 5x`() {
        val ampliado = ZoomImagem().aplicar(Offset(100f, 100f), 3f, Offset.Zero, tamanho)

        assertThat(ampliado.aplicar(Offset(100f, 100f), 0.1f, Offset.Zero, tamanho)).isEqualTo(ZoomImagem())
        assertThat(ampliado.aplicar(centro, 10f, Offset.Zero, tamanho).escala).isEqualTo(5f)
    }
}
