package com.conversa.app.core.data.sistema

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.model.AlteracaoParametros
import com.conversa.app.core.model.ParametrosSistema
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarParametrosRequisicao
import com.conversa.app.core.network.dto.ParametrosDto
import com.conversa.app.core.network.dto.PermissaoDto
import com.conversa.app.core.network.dto.PermissaoUsuarioDto
import com.conversa.app.core.network.dto.PermissoesDto
import com.conversa.app.core.network.dto.UsuarioPermissoesDto
import com.conversa.app.core.network.json.ConversaJson
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Sistema e Acessos (TODO 8.5). */
class SistemaRepositorioTest {
    private val sessao = MutableStateFlow<Sessao?>(Sessao("tok", 7, "Ana"))
    private val sessoes = mockk<SessaoRepositorio> { every { this@mockk.sessao } returns this@SistemaRepositorioTest.sessao }
    private val api = mockk<ConversaApi>()
    private val repo = SistemaRepositorio(api, sessoes)

    @Test
    fun `minhas permissoes ficam presas ao usuario logado`() = runTest {
        coEvery { api.minhasPermissoes() } returns listOf("parametros", "permissoes")

        repo.carregarMinhasPermissoes()
        assertThat(repo.minhasPermissoes.first()).containsExactly("parametros", "permissoes")

        sessao.value = Sessao("tok2", 8, "Bruno")
        assertThat(repo.minhasPermissoes.first()).isEmpty()
    }

    @Test
    fun `parametros - nulos viram vazio, e o PATCH so leva o que mudou`() = runTest {
        coEvery { api.parametros() } returns ParametrosDto(fcmPrivateKeyConfigurada = true, gravacaoDias = 30, s3Bucket = "anexos")
        assertThat(repo.parametros().getOrThrow())
            .isEqualTo(ParametrosSistema(fcmChaveConfigurada = true, gravacaoDias = 30, s3Bucket = "anexos"))

        coEvery { api.alterarParametros(any()) } returns ParametrosDto(turnForcarRelay = false, transcritorUrl = "")
        repo.alterarParametros(AlteracaoParametros(turnForcarRelay = false, transcritorUrl = ""))

        val esperado = AlterarParametrosRequisicao(turnForcarRelay = false, transcritorUrl = "")
        coVerify { api.alterarParametros(esperado) }
        // No fio: só os dois campos (o ConversaJson não manda nulos); a URL vazia desliga o transcritor.
        val json = ConversaJson.encodeToJsonElement(AlterarParametrosRequisicao.serializer(), esperado).jsonObject
        assertThat(json.keys).containsExactly("turn_forcar_relay", "transcritor_url")
    }

    @Test
    fun `acessos - mapeia e, depois de conceder, rele as minhas`() = runTest {
        coEvery { api.permissoes() } returns PermissoesDto(
            permissoes = listOf(PermissaoDto("permissoes", "Conceder e retirar")),
            usuarios = listOf(UsuarioPermissoesDto(7, "Ana", "ana", listOf("parametros"))),
            modoAberto = true,
        )
        val acessos = repo.acessos().getOrThrow()
        assertThat(acessos.usuarios.single().permissoes).containsExactly("parametros")
        assertThat(acessos.modoAberto).isTrue()

        coEvery { api.concederPermissao(any()) } returns PermissaoUsuarioDto(7, "permissoes")
        coEvery { api.minhasPermissoes() } returns listOf("parametros", "permissoes")
        repo.conceder(7, "permissoes").getOrThrow()

        coVerify { api.concederPermissao(PermissaoUsuarioDto(7, "permissoes")) }
        assertThat(repo.minhasPermissoes.first()).containsExactly("parametros", "permissoes")
    }
}
