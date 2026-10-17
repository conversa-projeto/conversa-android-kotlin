package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Mercator dos tiles e o enquadramento do web (Leaflet `fitBounds`, `setView`). */
class ProjecaoMapaTest {
    @Test
    fun `ida e volta da projecao`() {
        val zoom = 16.0
        assertThat(xNoMapa(0.0, 0.0)).isWithin(1e-9).of(128.0)
        assertThat(yNoMapa(0.0, 0.0)).isWithin(1e-9).of(128.0)
        val x = xNoMapa(-46.6333, zoom)
        val y = yNoMapa(-23.5505, zoom)
        assertThat(lngDoX(x, zoom)).isWithin(1e-9).of(-46.6333)
        assertThat(latDoY(y, zoom)).isWithin(1e-9).of(-23.5505)
    }

    @Test
    fun `enquadramento - vazio, um ponto e varios`() {
        assertThat(enquadrar(emptyList(), 288.0, 160.0)).isEqualTo(ENQUADRAMENTO_PADRAO)
        assertThat(enquadrar(listOf(-23.5 to -46.6), 288.0, 160.0)).isEqualTo(Enquadramento(-23.5, -46.6, 16))
        val dois = enquadrar(listOf(-23.5 to -46.6, -23.6 to -46.7), 288.0, 160.0)
        // Os dois pontos cabem com a margem de 28 px.
        val z = dois.zoom.toDouble()
        assertThat(yNoMapa(-23.6, z) - yNoMapa(-23.5, z)).isAtMost(160.0 - 56)
        assertThat(xNoMapa(-46.6, z) - xNoMapa(-46.7, z)).isAtMost(288.0 - 56)
        // Um zoom a mais já não caberia.
        assertThat(yNoMapa(-23.6, z + 1) - yNoMapa(-23.5, z + 1)).isGreaterThan(160.0 - 56)
        assertThat(dois.lat).isWithin(0.01).of(-23.55)
        assertThat(dois.lng).isWithin(1e-9).of(-46.65)
        // Pontos muito perto: no máximo o 17.
        assertThat(enquadrar(listOf(-23.5 to -46.6, -23.50001 to -46.60001), 288.0, 160.0).zoom).isEqualTo(17)
    }

    @Test
    fun `endereco do tile da a volta no mundo`() {
        assertThat(enderecoDoTile(CamadaMapa.RUAS.enderecos[0].first, 2, -1, 1)).isEqualTo("https://tile.openstreetmap.org/2/3/1.png")
        assertThat(enderecoDoTile(CamadaMapa.SATELITE.enderecos[0].first, 3, 2, 5)).endsWith("/tile/3/5/2")
    }
}
