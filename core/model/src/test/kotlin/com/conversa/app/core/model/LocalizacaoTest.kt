package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Localização e trajeto (conteúdo tipo 9), com os casos do `localizacao.test.ts` do web. */
class LocalizacaoTest {
    private val casa = PontoMapa(-23.5, -46.6, "Casa")
    private val trabalho = PontoMapa(-23.6, -46.7, "Trabalho")

    @Test
    fun `distancia, tempo e coordenadas como o web`() {
        assertThat(formatarDistancia(850.0)).isEqualTo("850 m")
        assertThat(formatarDistancia(12_345.0)).isEqualTo("12,3 km")
        assertThat(formatarDistancia(150_000.0)).isEqualTo("150 km")
        assertThat(formatarTempoTrajeto(30.0)).isEqualTo("1 min")
        assertThat(formatarTempoTrajeto(5_400.0)).isEqualTo("1 h 30 min")
        assertThat(formatarTempoTrajeto(7_200.0)).isEqualTo("2 h")
        assertThat(nomePonto(PontoMapa(-23.5, -46.6))).isEqualTo("-23.50000, -46.60000")
        // Entre (0,0) e (0,1): cerca de 111 km.
        assertThat(distanciaReta(PontoMapa(0.0, 0.0), PontoMapa(0.0, 1.0))).isWithin(500.0).of(111_195.0)
    }

    @Test
    fun `resumo, descricao e links`() {
        val trajeto = Localizacao(listOf(casa, trabalho), rota = "x", distancia = 12_345.0, duracao = 1_080.0)
        assertThat(resumoLocalizacao(trajeto)).isEqualTo("Casa → Trabalho · 12,3 km")
        assertThat(descricaoTrajeto(trajeto)).isEqualTo(DescricaoTrajeto.DeCarro("12,3 km", "18 min"))
        assertThat(descricaoTrajeto(trajeto.copy(rota = null, duracao = null))).isEqualTo(DescricaoTrajeto.LinhaReta("12,3 km"))
        assertThat(descricaoTrajeto(trajeto.copy(distancia = null, duracao = null))).isEqualTo(DescricaoTrajeto.SoTrajeto)
        assertThat(linkMapa(trajeto))
            .isEqualTo("https://www.google.com/maps/dir/?api=1&origin=-23.5%2C-46.6&destination=-23.6%2C-46.7&travelmode=driving")
        assertThat(linkMapa(Localizacao(listOf(casa)))).isEqualTo("https://www.google.com/maps/search/?api=1&query=-23.5%2C-46.6")
        assertThat(resumoLocalizacao(Localizacao(listOf(casa)))).isEqualTo("Casa")
    }

    @Test
    fun `coordenadas digitadas na busca`() {
        assertThat(lerCoordenadas("-23.55, -46.63")).isEqualTo(PontoMapa(-23.55, -46.63))
        assertThat(lerCoordenadas("-23,55; -46,63")).isEqualTo(PontoMapa(-23.55, -46.63))
        assertThat(lerCoordenadas("-23.55 -46.63")).isEqualTo(PontoMapa(-23.55, -46.63))
        assertThat(lerCoordenadas("Praça da Sé")).isNull()
        assertThat(lerCoordenadas("95, 10")).isNull()
    }

    @Test
    fun `polyline do OSRM com precisao 5`() {
        assertThat(decodificarRota("_p~iF~ps|U_ulLnnqC_mqNvxq`@"))
            .containsExactly(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453).inOrder()
        assertThat(decodificarRota("")).isEmpty()
    }
}
