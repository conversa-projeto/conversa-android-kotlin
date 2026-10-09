package com.conversa.app.feature.config

import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.data.sistema.SistemaRepositorio
import com.conversa.app.core.model.PreferenciaTema
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Aba Configurações (TODO 8.5). */
class ConfiguracoesViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val tema = MutableStateFlow(PreferenciaTema.SISTEMA)
    private val perfil = mockk<PerfilRepositorio>(relaxed = true) {
        every { sessao } returns MutableStateFlow(Sessao("tok", 7, "Ana", email = "ana@exemplo.test", avatarIdentificador = "foto"))
        coEvery { urlDaFoto("foto") } returns Result.success("https://s/foto")
    }
    private val servidor = mockk<ServidorRepositorio> {
        every { atual } returns MutableStateFlow(ServerConfig.aPartirDe("https://conversa.exemplo.test"))
    }
    private val autenticacao = mockk<AutenticacaoRepositorio>(relaxed = true)
    private val preferencias = mockk<PreferenciasRepositorio> {
        every { this@mockk.tema } returns this@ConfiguracoesViewModelTest.tema
        coEvery { alterarTema(any()) } answers { this@ConfiguracoesViewModelTest.tema.value = firstArg() }
    }

    private val sistema = mockk<SistemaRepositorio> {
        every { minhasPermissoes } returns MutableStateFlow(setOf("parametros"))
        coEvery { carregarMinhasPermissoes() } returns Result.success(setOf("parametros"))
    }

    private fun criar() = ConfiguracoesViewModel(perfil, servidor, autenticacao, preferencias, sistema, Clock.systemUTC())

    @Test
    fun `mostra quem esta logado, a foto, o servidor, o tema e so o Sistema para quem so tem parametros`() = runTest {
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        vm.alterarTema(PreferenciaTema.ESCURO)
        advanceUntilIdle()

        assertThat(vm.estado.value).isEqualTo(
            ConfiguracoesUiState(
                nome = "Ana",
                email = "ana@exemplo.test",
                fotoUrl = "https://s/foto",
                servidor = "https://conversa.exemplo.test/",
                tema = PreferenciaTema.ESCURO,
                podeSistema = true,
                podeAcessos = false,
            ),
        )
    }

    @Test
    fun `sair so uma vez, mesmo com toques repetidos`() = runTest {
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }

        vm.sair()
        vm.sair()
        advanceUntilIdle()

        assertThat(vm.estado.value.saindo).isTrue()
        coVerify(exactly = 1) { autenticacao.sair() }
        coVerify { sistema.carregarMinhasPermissoes() }
    }
}
