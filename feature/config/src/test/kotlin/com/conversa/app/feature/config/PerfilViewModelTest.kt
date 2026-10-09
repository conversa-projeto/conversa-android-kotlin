package com.conversa.app.feature.config

import android.net.Uri
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Perfil (TODO 8.3): validações e mensagens do web, foto e URL renovada. */
class PerfilViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val sessao = MutableStateFlow<Sessao?>(Sessao("tok", 7, "Ana", email = "ana@exemplo.test", avatarIdentificador = "foto"))
    private val perfil = mockk<PerfilRepositorio>(relaxed = true) {
        every { this@mockk.sessao } returns this@PerfilViewModelTest.sessao
        coEvery { urlDaFoto("foto") } returns Result.success("https://s/foto")
    }
    private val preparador = mockk<PreparadorFotoDePerfil>()
    private var agora = Instant.parse("2026-10-09T12:00:00Z")
    private val relogio = object : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = agora
    }

    private fun criar() = PerfilViewModel(perfil, preparador, relogio)

    @Test
    fun `senha - conferencias do web antes de mandar, e o erro do servidor aparece como veio`() = runTest {
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }

        vm.salvarSenha("", "123456", "123456")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoSenha).isEqualTo(Aviso(ok = false, recurso = R.string.perfil_senha_campos))
        vm.salvarSenha("velha", "123", "123")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoSenha).isEqualTo(Aviso(ok = false, recurso = R.string.perfil_senha_curta))
        vm.salvarSenha("velha", "123456", "654321")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoSenha).isEqualTo(Aviso(ok = false, recurso = R.string.perfil_senha_nao_confere))
        coVerify(exactly = 0) { perfil.alterarSenha(any(), any()) }

        coEvery { perfil.alterarSenha("errada", "123456") } returns Result.failure(ErroApi.Servidor(400, "Senha atual incorreta!"))
        vm.salvarSenha("errada", "123456", "123456")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoSenha).isEqualTo(Aviso(ok = false, texto = "Senha atual incorreta!"))

        coEvery { perfil.alterarSenha("certa", "123456") } returns Result.success(Unit)
        vm.salvarSenha("certa", "123456", "123456")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoSenha).isEqualTo(Aviso(ok = true, recurso = R.string.perfil_senha_alterada))
        assertThat(vm.estado.value.senhasTrocadas).isEqualTo(1)
    }

    @Test
    fun `dados - nome e email obrigatorios, sucesso com a mensagem do web`() = runTest {
        coEvery { perfil.alterarDados("Ana", "ana@x.test") } returns Result.success(Unit)
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }

        vm.salvarDados(" ", "ana@x.test")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoDados).isEqualTo(Aviso(ok = false, recurso = R.string.perfil_preencha_dados))

        vm.salvarDados("Ana", "ana@x.test")
        advanceUntilIdle()
        assertThat(vm.estado.value.avisoDados).isEqualTo(Aviso(ok = true, recurso = R.string.perfil_dados_salvos))
    }

    @Test
    fun `foto ilegivel avisa sem mandar nada`() = runTest {
        val uri = mockk<Uri>()
        coEvery { preparador.preparar(uri) } returns null
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }

        vm.trocarFoto(uri)
        advanceUntilIdle()

        assertThat(vm.estado.value.avisoFoto).isEqualTo(Aviso(ok = false, recurso = R.string.perfil_foto_ilegivel))
        coVerify(exactly = 0) { perfil.trocarFoto(any()) }
    }

    @Test
    fun `url da foto vencida e renovada no maximo a cada 30 s`() = runTest {
        val vm = criar()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()
        assertThat(vm.estado.value.fotoUrl).isEqualTo("https://s/foto")

        vm.fotoFalhou()
        vm.fotoFalhou()
        agora = agora.plusSeconds(29)
        vm.fotoFalhou()
        agora = agora.plusSeconds(2)
        vm.fotoFalhou()
        advanceUntilIdle()

        verify(exactly = 2) { perfil.esquecerUrl("foto") }
    }
}
