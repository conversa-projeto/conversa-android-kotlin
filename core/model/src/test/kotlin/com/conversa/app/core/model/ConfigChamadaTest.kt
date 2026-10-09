package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Qualidade das chamadas (TODO 8.5): padrões do web no celular e o texto guardado. */
class ConfigChamadaTest {
    @Test
    fun `padrao do celular - processamento ligado, 32 kbps, 360p a 15 fps, banda automatica`() {
        val padrao = ConfigChamada.PADRAO

        assertThat(padrao.reducaoRuido && padrao.cancelamentoEco && padrao.ganhoAutomatico).isTrue()
        assertThat(padrao.qualidadeAudio.bitrate).isEqualTo(32_000)
        assertThat(padrao.resolucao.largura to padrao.resolucao.altura).isEqualTo(640 to 360)
        assertThat(padrao.quadros).isEqualTo(15)
        assertThat(padrao.banda.bitrate).isNull()
    }

    @Test
    fun `vai e volta pelo texto`() {
        val config = ConfigChamada(
            reducaoRuido = false,
            cancelamentoEco = true,
            ganhoAutomatico = false,
            qualidadeAudio = QualidadeAudio.MUSICA,
            resolucao = ResolucaoVideo.P1080,
            quadros = 30,
            banda = BandaVideo.ECONOMICA,
        )

        assertThat(config.paraTexto()).isEqualTo("ruido=0;eco=1;ganho=0;audio=musica;resolucao=1080;fps=30;banda=economico")
        assertThat(ConfigChamada.deTexto(config.paraTexto())).isEqualTo(config)
    }

    @Test
    fun `o que falta ou nao se reconhece fica no padrao`() {
        assertThat(ConfigChamada.deTexto(null)).isEqualTo(ConfigChamada.PADRAO)
        assertThat(ConfigChamada.deTexto("lixo;;=x;fps=60;audio=hifi;eco=talvez")).isEqualTo(ConfigChamada.PADRAO)
        assertThat(ConfigChamada.deTexto("eco=0;fps=24")).isEqualTo(ConfigChamada(cancelamentoEco = false, quadros = 24))
    }
}
