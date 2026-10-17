package com.conversa.app.core.network.dto

import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** O JSON do conteúdo tipo 9 (contrato §10.16), lido e escrito como o web. */
class LocalizacaoJsonTest {
    @Test
    fun `le o local e o trajeto, e recusa o que nao e localizacao`() {
        assertThat(lerLocalizacao("""{"pontos":[{"lat":-23.55,"lng":-46.63,"nome":"Praça da Sé"}]}"""))
            .isEqualTo(Localizacao(listOf(PontoMapa(-23.55, -46.63, "Praça da Sé"))))
        val trajeto = lerLocalizacao(
            """{"pontos":[{"lat":-23.55,"lng":-46.63},{"lat":-23.56,"lng":-46.65}],"rota":"_p~iF","distancia":2500,"duracao":420}""",
        )!!
        assertThat(trajeto.trajeto).isTrue()
        assertThat(trajeto.distancia).isEqualTo(2500.0)
        assertThat(trajeto.rota).isEqualTo("_p~iF")
        assertThat(lerLocalizacao("")).isNull()
        assertThat(lerLocalizacao("texto")).isNull()
        assertThat(lerLocalizacao("{}")).isNull()
        assertThat(lerLocalizacao("""{"pontos":[]}""")).isNull()
    }

    @Test
    fun `escreve na ordem do web, inteiros e sem null`() {
        val local = Localizacao(listOf(PontoMapa(-23.5, -46.6, null)))
        assertThat(localizacaoParaJson(local)).isEqualTo("""{"pontos":[{"lat":-23.5,"lng":-46.6}]}""")
        val trajeto = Localizacao(
            listOf(PontoMapa(-23.5, -46.6, "Casa"), PontoMapa(-23.6, -46.7, " Trabalho ")),
            rota = "_p~iF",
            distancia = 12_345.4,
            duracao = 1_080.6,
        )
        assertThat(localizacaoParaJson(trajeto)).isEqualTo(
            """{"pontos":[{"lat":-23.5,"lng":-46.6,"nome":"Casa"},{"lat":-23.6,"lng":-46.7,"nome":"Trabalho"}],"rota":"_p~iF","distancia":12345,"duracao":1081}""",
        )
        // Sem rota (o OSRM falhou): só a distância em linha reta.
        assertThat(localizacaoParaJson(trajeto.copy(rota = null, duracao = null)))
            .endsWith(""""distancia":12345}""")
        // O que se escreve, se lê igual.
        assertThat(lerLocalizacao(localizacaoParaJson(trajeto))!!.pontos[1].nome).isEqualTo("Trabalho")
    }
}
