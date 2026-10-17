package com.conversa.app.core.network.mapas

import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Os endereços e as respostas do Nominatim e do OSRM, como o web (`services/mapas.ts`). */
class ServicosDeMapaTest {
    @Test
    fun `monta os enderecos do web`() {
        assertThat(urlBuscaEndereco("Praça da Sé, São Paulo").toString()).isEqualTo(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=pt-BR&q=Pra%C3%A7a%20da%20S%C3%A9%2C%20S%C3%A3o%20Paulo",
        )
        assertThat(urlNomeDoLugar(PontoMapa(-23.5503, -46.6339)).toString()).isEqualTo(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=pt-BR&lat=-23.550300&lon=-46.633900",
        )
        assertThat(urlRota(PontoMapa(-23.55, -46.63), PontoMapa(-23.5614, -46.6559)).toString()).isEqualTo(
            "https://router.project-osrm.org/route/v1/driving/-46.630000,-23.550000;-46.655900,-23.561400?overview=full&geometries=polyline",
        )
        // Coordenada pequena não vira notação científica.
        assertThat(urlNomeDoLugar(PontoMapa(0.0000001, 1e-7)).queryParameter("lat")).isEqualTo("0.000000")
    }

    @Test
    fun `so os servicos de mapa ficam fora do log`() {
        assertThat(ehServicoDeMapa("tile.openstreetmap.org")).isTrue()
        assertThat(ehServicoDeMapa("nominatim.openstreetmap.org")).isTrue()
        assertThat(ehServicoDeMapa("server.arcgisonline.com")).isTrue()
        assertThat(ehServicoDeMapa("router.project-osrm.org")).isTrue()
        assertThat(ehServicoDeMapa("localhost")).isFalse()
        assertThat(ehServicoDeMapa("conversa.exemplo.com")).isFalse()
        assertThat(ehServicoDeMapa("falsoopenstreetmap.org")).isFalse()
    }

    @Test
    fun `le a busca de endereco`() {
        val corpo = """
            [{"place_id":1,"lat":"-23.5503","lon":"-46.6339","display_name":"Praça da Sé, Sé, São Paulo"},
             {"lat":"x","lon":"-46.6"},
             {"lat":"-22.9","lon":"-43.2","display_name":"  "}]
        """.trimIndent()
        assertThat(lerBuscaEndereco(corpo)).containsExactly(
            PontoMapa(-23.5503, -46.6339, "Praça da Sé, Sé, São Paulo"),
            PontoMapa(-22.9, -43.2, null),
        ).inOrder()
        assertThat(lerBuscaEndereco("[]")).isEmpty()
        assertThat(lerBuscaEndereco("<html>")).isEmpty()
        val longo = "a".repeat(400)
        assertThat(lerBuscaEndereco("""[{"lat":"1","lon":"2","display_name":"$longo"}]""").single().nome).hasLength(300)
    }

    @Test
    fun `le o nome do lugar`() {
        assertThat(
            lerNomeDoLugar("""{"lat":"-23.5","lon":"-46.6","display_name":"Rua Direita, São Paulo"}"""),
        ).isEqualTo("Rua Direita, São Paulo")
        assertThat(lerNomeDoLugar("""{"error":"Unable to geocode"}""")).isNull()
        assertThat(lerNomeDoLugar("texto")).isNull()
    }

    @Test
    fun `le a rota de carro`() {
        val corpo = """{"code":"Ok","routes":[{"geometry":"_p~iF~ps|U","distance":2512.7,"duration":431.4,"legs":[]}],"waypoints":[]}"""
        assertThat(lerRota(corpo)).isEqualTo(RotaCarro("_p~iF~ps|U", 2513.0, 431.0))
        assertThat(lerRota("""{"code":"NoRoute","routes":[]}""")).isNull()
        assertThat(lerRota("""{"code":"Ok","routes":[]}""")).isNull()
        assertThat(lerRota("erro")).isNull()
    }
}
