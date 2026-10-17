package com.conversa.app.core.data.mapas

import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/** O Nominatim e o OSRM num servidor de mentira: os pedidos e as falhas (ENV-30). */
class MapasRepositorioTest {
    private val servidor = MockWebServer()
    private lateinit var repo: MapasRepositorio

    @Before
    fun subir() {
        servidor.start()
        val cliente = OkHttpClient()
        repo = MapasRepositorio({ cliente }, Dispatchers.IO, servidor.url("/"), servidor.url("/"))
    }

    @After
    fun parar() = servidor.close()

    private fun responder(codigo: Int, corpo: String = "") = servidor.enqueue(MockResponse.Builder().code(codigo).body(corpo).build())

    @Test
    fun `busca o endereco e falha sem resposta`() = runTest {
        responder(200, """[{"lat":"-23.5503","lon":"-46.6339","display_name":"Praça da Sé"}]""")
        assertThat(repo.buscarEndereco("Praça da Sé").getOrThrow()).containsExactly(PontoMapa(-23.5503, -46.6339, "Praça da Sé"))
        val pedido = servidor.takeRequest()
        assertThat(pedido.url.encodedPath).isEqualTo("/search")
        assertThat(pedido.url.queryParameter("q")).isEqualTo("Praça da Sé")
        assertThat(pedido.headers["Authorization"]).isNull()

        responder(503)
        assertThat(repo.buscarEndereco("Sé").exceptionOrNull()).isInstanceOf(BuscaEnderecoFalhou::class.java)
        responder(200, "[]")
        assertThat(repo.buscarEndereco("lugar nenhum").getOrThrow()).isEmpty()
    }

    @Test
    fun `nome do lugar e rota ficam nulos quando o servico falha`() = runTest {
        responder(200, """{"display_name":"Rua Direita, São Paulo"}""")
        assertThat(repo.nomeDoLugar(PontoMapa(-23.5, -46.6))).isEqualTo("Rua Direita, São Paulo")
        assertThat(servidor.takeRequest().url.encodedPath).isEqualTo("/reverse")
        responder(500)
        assertThat(repo.nomeDoLugar(PontoMapa(-23.5, -46.6))).isNull()

        responder(200, """{"code":"Ok","routes":[{"geometry":"_p~iF","distance":3100.4,"duration":539.6}]}""")
        assertThat(repo.calcularRota(PontoMapa(-23.55, -46.63), PontoMapa(-23.56, -46.65))).isEqualTo(RotaCarro("_p~iF", 3100.0, 540.0))
        servidor.takeRequest()
        assertThat(servidor.takeRequest().url.encodedPath).isEqualTo("/route/v1/driving/-46.630000,-23.550000;-46.650000,-23.560000")
        responder(200, """{"code":"NoRoute"}""")
        assertThat(repo.calcularRota(PontoMapa(0.0, 0.0), PontoMapa(1.0, 1.0))).isNull()
    }
}
