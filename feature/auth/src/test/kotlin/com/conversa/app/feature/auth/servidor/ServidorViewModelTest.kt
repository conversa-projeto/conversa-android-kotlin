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
        every { certificadoAceito(any()) } returns null
    }

    @Test
    fun `endereco invalido nao chama o servidor`() = runTest {
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("ftp://x")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.ENDERECO_INVALIDO)
        coVerify(exactly = 0) { repositorio.testar(any(), any()) }
    }

    @Test
    fun `mostra o endereco normalizado enquanto digita`() = runTest {
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("192.168.2.5/api")
        assertThat(vm.estado.value.enderecoNormalizado).isEqualTo("https://192.168.2.5/")
    }

    @Test
    fun `testar mostra o resultado`() = runTest {
        coEvery { repositorio.testar(any(), any()) } returns ResultadoTesteServidor.CertificadoInvalido()
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.testar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.CERTIFICADO_INVALIDO)
        assertThat(vm.estado.value.testando).isFalse()
    }

    @Test
    fun `salvar so grava se o teste passar e avisa a tela`() = runTest {
        coEvery { repositorio.testar(any(), any()) } returns ResultadoTesteServidor.Ok
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
        coEvery { repositorio.testar(any(), any()) } returns ResultadoTesteServidor.Indisponivel
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.INDISPONIVEL)
        coVerify(exactly = 0) { repositorio.salvar(any(), any()) }
    }

    @Test
    fun `carrega o endereco ja salvo`() = runTest {
        every { repositorio.atual } returns MutableStateFlow(ServerConfig.aPartirDe("https://ja.salvo/"))
        val vm = ServidorViewModel(repositorio)
        advanceUntilIdle()
        assertThat(vm.estado.value.endereco).isEqualTo("https://ja.salvo/")
    }

    @Test
    fun `certificado proprio mostra a impressao e confiar salva o endereco com ele`() = runTest {
        val https = ServerConfig.aPartirDe("https://conversa.local/")!!
        coEvery { repositorio.testar(https, null) } returns ResultadoTesteServidor.CertificadoInvalido("AA:BB")
        coEvery { repositorio.testar(https, "AA:BB") } returns ResultadoTesteServidor.Ok
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.certificadoParaConfiar).isEqualTo("AA:BB")
        coVerify(exactly = 0) { repositorio.salvar(any(), any()) }

        vm.eventos.fluxo.test {
            vm.confiarNoCertificado()
            assertThat(awaitItem()).isEqualTo(EventoServidor.Salvo)
        }
        coVerify { repositorio.salvar(https, "AA:BB") }
        assertThat(vm.estado.value.certificadoParaConfiar).isNull()
    }

    @Test
    fun `nao confiar fecha a pergunta sem salvar`() = runTest {
        coEvery { repositorio.testar(any(), any()) } returns ResultadoTesteServidor.CertificadoInvalido("AA:BB")
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("conversa.local")
        vm.testar()
        advanceUntilIdle()
        vm.naoConfiarNoCertificado()
        assertThat(vm.estado.value.certificadoParaConfiar).isNull()
        coVerify(exactly = 0) { repositorio.salvar(any(), any()) }
    }

    @Test
    fun `so o ip sem resposta no https tenta http e mostra o endereco usado`() = runTest {
        val https = ServerConfig.aPartirDe("https://192.168.0.10/")!!
        val http = ServerConfig.aPartirDe("http://192.168.0.10/")!!
        coEvery { repositorio.testar(https, any()) } returns ResultadoTesteServidor.SemConexao("recusada")
        coEvery { repositorio.testar(http, any()) } returns ResultadoTesteServidor.Ok
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("192.168.0.10")
        vm.eventos.fluxo.test {
            vm.salvar()
            assertThat(awaitItem()).isEqualTo(EventoServidor.Salvo)
        }
        coVerify { repositorio.salvar(http, null) }
        assertThat(vm.estado.value.endereco).isEqualTo("http://192.168.0.10/")
    }

    @Test
    fun `com https digitado nao tenta http`() = runTest {
        coEvery { repositorio.testar(any(), any()) } returns ResultadoTesteServidor.SemConexao("recusada")
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("https://192.168.0.10")
        vm.salvar()
        advanceUntilIdle()
        assertThat(vm.estado.value.aviso).isEqualTo(AvisoServidor.SEM_CONEXAO)
        coVerify(exactly = 1) { repositorio.testar(any(), any()) }
    }

    @Test
    fun `porta so http com o ip digitado sem esquema tambem tenta http`() = runTest {
        val https = ServerConfig.aPartirDe("https://192.168.0.10:8080/")!!
        val http = ServerConfig.aPartirDe("http://192.168.0.10:8080/")!!
        coEvery { repositorio.testar(https, any()) } returns ResultadoTesteServidor.CertificadoInvalido(null)
        coEvery { repositorio.testar(http, any()) } returns ResultadoTesteServidor.Ok
        val vm = ServidorViewModel(repositorio)
        vm.alterarEndereco("192.168.0.10:8080")
        vm.eventos.fluxo.test {
            vm.salvar()
            assertThat(awaitItem()).isEqualTo(EventoServidor.Salvo)
        }
        coVerify { repositorio.salvar(http, null) }
    }
}
