package com.conversa.app.feature.auth.servidor

import app.cash.turbine.test
import com.conversa.app.core.data.ResultadoTesteServidor
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ServidorViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val repositorio = mockk<ServidorRepositorio>(relaxUnitFun = true) {
        every { carregado } returns MutableStateFlow(true)
        every { atual } returns MutableStateFlow(null)
    }

    @Test
    fun `endereco invalido nao chama o servidor`() = runTest {
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("ftp://x")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.ENDERECO_INVALIDO)
        coVerify(exactly = 0) { repositorio.testar(any()) }
    }

    @Test
    fun `mostra o endereco normalizado enquanto digita`() = runTest {
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("192.168.2.5/api")
        assertThat(vm.estado.value.enderecoNormalizado).isEqualTo("https://192.168.2.5/")
    }

    @Test
    fun `testar mostra o resultado`() = runTest {
        coEvery { repositorio.testar(any()) } returns ResultadoTesteServidor.CertificadoInvalido
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.testar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.CERTIFICADO_INVALIDO)
        assertThat(vm.estado.value.testando).isFalse()
    }

    @Test
    fun `salvar so grava se o teste passar e avisa a tela`() = runTest {
        coEvery { repositorio.testar(any()) } returns ResultadoTesteServidor.Ok
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.eventos.fluxo.test {
            vm.salvar()
            assertThat(awaitItem()).isEqualTo(EventoServidor.Salvo)
        }
        coVerify { repositorio.salvar(ServerConfig.aPartirDe("https://conversa.local/")!!) }
    }

    @Test
    fun `salvar com servidor fora do ar nao grava`() = runTest {
        coEvery { repositorio.testar(any()) } returns ResultadoTesteServidor.Indisponivel
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.INDISPONIVEL)
        coVerify(exactly = 0) { repositorio.salvar(any()) }
    }

    @Test
    fun `carrega o endereco ja salvo`() = runTest {
        every { repositorio.atual } returns MutableStateFlow(ServerConfig.aPartirDe("https://ja.salvo/"))
        val vm = ServidorViewModel(repositorio)
        advanceUntilIdle()
        assertThat(vm.estado.value.endereco).isEqualTo("https://ja.salvo/")
    }
}
