package com.conversa.app.feature.chamada

import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.webrtc.EtapaVideo
import com.conversa.app.core.webrtc.MidiaLocal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A chamada numa linha do registro do app. */
class ResumoDaChamadaTest {
    @Test
    fun `sem chamada`() {
        assertThat(EstadoChamada().resumoParaRegistro()).isEqualTo("sem chamada")
    }

    @Test
    fun `fase, chamada, midia e o que esta acontecendo`() {
        val estado = EstadoChamada(
            fase = FaseChamada.ATIVA,
            chamadaId = 42,
            tipo = TipoChamada.VIDEO,
            remetenteId = 7,
            midiaLocal = MidiaLocal.entries.last(),
            microfoneLigado = false,
            cameraLigada = true,
            telas = setOf(9),
            etapaVideo = EtapaVideo.ENVIANDO,
            pedidoVideo = PedidoVideo(9, "Nome que não entra"),
        )

        val resumo = estado.resumoParaRegistro()

        assertThat(resumo).startsWith("ativa chamada 42 (video, ligou: 7); mídia ${MidiaLocal.entries.last().name.lowercase()}")
        assertThat(resumo).contains("microfone mudo, câmera ligada")
        assertThat(resumo).contains("compartilhando a tela: 9")
        assertThat(resumo).contains("ligando o vídeo: enviando")
        assertThat(resumo).contains("pedido de vídeo de 9")
        assertThat(resumo).doesNotContain("Nome que não entra")
    }
}
