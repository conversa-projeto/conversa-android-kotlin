package com.conversa.app.feature.chat

import com.conversa.app.core.data.mapas.BuscaEnderecoFalhou
import com.conversa.app.core.data.mapas.MapasRepositorio
import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.conversa.app.core.model.distanciaReta
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** "Enviar localização" (ENV-30), com o comportamento do `LocalizacaoModal.vue`. */
class EnviarLocalizacaoViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val se = PontoMapa(-23.5503, -46.6339)
    private val masp = PontoMapa(-23.5614, -46.6559)
    private val mapas = mockk<MapasRepositorio> {
        coEvery { nomeDoLugar(any()) } returns null
        coEvery { calcularRota(any(), any()) } returns null
    }
    private var posicao: suspend () -> PontoMapa = { se }

    private fun criar() = EnviarLocalizacaoViewModel(mapas) { posicao() }.also { it.abrir() }

    private fun EnviarLocalizacaoViewModel.texto(indice: Int) = textos[indice].text.toString()

    @Test
    fun `o toque no mapa marca o local e o nome do lugar chega depois`() = runTest {
        coEvery { mapas.nomeDoLugar(se) } returns "Praça da Sé, São Paulo"
        val vm = criar()
        assertThat(vm.estado.value.pronta).isFalse()
        vm.definirPonto(vm.estado.value.ativo, se, buscarNome = true)
        // Enquanto o nome não chega, o campo mostra as coordenadas.
        assertThat(vm.texto(0)).isEqualTo("-23.55030, -46.63390")
        assertThat(vm.estado.value.pronta).isTrue()
        advanceUntilIdle()
        assertThat(vm.texto(0)).isEqualTo("Praça da Sé, São Paulo")
        assertThat(vm.estado.value.resultado()).isEqualTo(Localizacao(listOf(se.copy(nome = "Praça da Sé, São Paulo"))))
    }

    @Test
    fun `trajeto - a origem passa o toque para o destino e a rota sai so com os pontos parados`() = runTest {
        val rota = RotaCarro("_p~iF", 3100.0, 540.0)
        coEvery { mapas.calcularRota(se, masp) } returns rota
        val vm = criar()
        vm.trocarModo(ModoLocalizacao.TRAJETO)
        vm.definirPonto(vm.estado.value.ativo, se, buscarNome = false)
        assertThat(vm.estado.value.ativo).isEqualTo(1)
        // Dois destinos seguidos: só o último vira pedido de rota.
        vm.definirPonto(vm.estado.value.ativo, PontoMapa(-23.0, -46.0), buscarNome = false)
        advanceTimeBy(100)
        vm.definirPonto(1, masp, buscarNome = false)
        assertThat(vm.estado.value.calculando).isTrue()
        assertThat(vm.estado.value.pronta).isFalse()
        advanceTimeBy(EnviarLocalizacaoViewModel.ESPERA_ROTA_MS + 1)
        runCurrent()
        coVerify(exactly = 1) { mapas.calcularRota(any(), any()) }
        assertThat(vm.estado.value.resultado()).isEqualTo(Localizacao(listOf(se, masp), "_p~iF", 3100.0, 540.0))
        assertThat(vm.mapa.first()).isEqualTo(PedidoMapa.Ajustar)

        // De volta ao local: a rota some e vai só a origem.
        vm.trocarModo(ModoLocalizacao.LOCAL)
        assertThat(vm.estado.value.rota).isNull()
        assertThat(vm.estado.value.resultado()).isEqualTo(Localizacao(listOf(se)))
    }

    @Test
    fun `trajeto sem rota pelas ruas vai em linha reta`() = runTest {
        val vm = criar()
        vm.trocarModo(ModoLocalizacao.TRAJETO)
        vm.definirPonto(0, se, buscarNome = false)
        vm.definirPonto(1, masp, buscarNome = false)
        advanceUntilIdle()
        val resultado = vm.estado.value.resultado()!!
        assertThat(resultado.rota).isNull()
        assertThat(resultado.distancia).isEqualTo(kotlin.math.round(distanciaReta(se, masp)))
    }

    @Test
    fun `a busca le coordenadas na hora e o resto vai para o Nominatim`() = runTest {
        val achados = listOf(PontoMapa(-23.5503, -46.6339, "Praça da Sé"), PontoMapa(-22.9, -43.2, "Sé, Rio"))
        coEvery { mapas.buscarEndereco("Sé") } returns Result.success(achados)
        coEvery { mapas.buscarEndereco("lugar nenhum") } returns Result.success(emptyList())
        coEvery { mapas.buscarEndereco("falha") } returns Result.failure(BuscaEnderecoFalhou())
        val vm = criar()

        vm.textos[0].edit { replace(0, length, " -23,55; -46,63 ") }
        vm.buscar(0)
        assertThat(vm.estado.value.pontos[0]).isEqualTo(PontoMapa(-23.55, -46.63))
        assertThat(vm.mapa.first()).isEqualTo(PedidoMapa.Centralizar(PontoMapa(-23.55, -46.63)))
        coVerify(exactly = 0) { mapas.buscarEndereco(any()) }

        vm.textos[0].edit { replace(0, length, "Sé") }
        vm.buscar(0)
        assertThat(vm.estado.value.buscando).isTrue()
        advanceUntilIdle()
        assertThat(vm.estado.value.resultados).isEqualTo(achados)
        assertThat(vm.estado.value.campoResultados).isEqualTo(0)
        // Escolher um resultado não pede o nome de novo.
        vm.escolherResultado(0, achados[1])
        advanceUntilIdle()
        assertThat(vm.texto(0)).isEqualTo("Sé, Rio")
        assertThat(vm.estado.value.resultados).isEmpty()
        coVerify(exactly = 1) { mapas.nomeDoLugar(any()) }

        vm.textos[0].edit { replace(0, length, "lugar nenhum") }
        vm.buscar(0)
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoLocalizacao.NadaEncontrado)
        vm.textos[0].edit { replace(0, length, "falha") }
        vm.buscar(0)
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoLocalizacao.BuscaFalhou)
        assertThat(vm.estado.value.buscando).isFalse()
    }

    @Test
    fun `minha posicao - ao abrir falha calada, no botao avisa`() = runTest {
        posicao = { throw SemPosicaoException(FalhaPosicao.INDISPONIVEL) }
        val vm = criar()
        vm.usarMinhaPosicao(0, silencioso = true)
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isNull()
        assertThat(vm.estado.value.localizando).isNull()
        vm.usarMinhaPosicao(0)
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoLocalizacao.SemPosicao(FalhaPosicao.INDISPONIVEL))
        vm.posicaoNegada(0)
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoLocalizacao.SemPosicao(FalhaPosicao.NEGADA))

        posicao = { masp }
        vm.usarMinhaPosicao(0)
        assertThat(vm.estado.value.localizando).isEqualTo(0)
        advanceUntilIdle()
        assertThat(vm.estado.value.pontos[0]).isEqualTo(masp)
        assertThat(vm.estado.value.aviso).isNull()
        assertThat(vm.mapa.first()).isEqualTo(PedidoMapa.Centralizar(masp))
    }

    @Test
    fun `abrir de novo comeca do zero, mas girar a tela nao`() = runTest {
        val vm = criar()
        vm.definirPonto(0, se, buscarNome = false)
        assertThat(vm.abrir()).isFalse()
        assertThat(vm.estado.value.pontos[0]).isEqualTo(se)
        vm.fechar()
        assertThat(vm.abrir()).isTrue()
        assertThat(vm.estado.value.pontos[0]).isNull()
        assertThat(vm.texto(0)).isEmpty()
    }
}
