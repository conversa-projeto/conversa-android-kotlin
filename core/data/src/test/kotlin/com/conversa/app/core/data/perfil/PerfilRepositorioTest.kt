package com.conversa.app.core.data.perfil

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.AnexoEnviado
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarSenhaRequisicao
import com.conversa.app.core.network.dto.AlterarUsuarioRequisicao
import com.conversa.app.core.network.dto.UsuarioDto
import com.conversa.app.core.network.json.nuloExplicito
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** Perfil do usuário logado (TODO 8.3). */
class PerfilRepositorioTest {
    private val estado = MutableStateFlow<Sessao?>(Sessao("tok", 7, "Ana", email = "ana@exemplo.test", avatarIdentificador = "velha"))
    private val sessoes = mockk<SessaoRepositorio>(relaxed = true) {
        every { sessao } returns estado
        coEvery { salvar(any()) } answers { estado.value = firstArg() }
    }
    private val api = mockk<ConversaApi>()
    private val anexos = mockk<AnexosRepositorio>()
    private val repo = PerfilRepositorio(api, sessoes, anexos)

    @Test
    fun `dados vao sem espacos e a sessao fica com o que o servidor devolveu`() = runTest {
        coEvery { api.alterarUsuario(any()) } returns UsuarioDto(7, "Ana Souza", email = "ana@novo.test")

        repo.alterarDados(" Ana Souza ", " ana@novo.test ")

        coVerify { api.alterarUsuario(AlterarUsuarioRequisicao(7, nome = "Ana Souza", email = "ana@novo.test")) }
        assertThat(estado.value!!.nome).isEqualTo("Ana Souza")
        assertThat(estado.value!!.email).isEqualTo("ana@novo.test")
    }

    @Test
    fun `senha vai com senha_atual e a nova, e nada muda na sessao`() = runTest {
        coEvery { api.alterarSenha(any()) } returns JsonObject(emptyMap())

        repo.alterarSenha("velha123", "nova123")

        coVerify { api.alterarSenha(AlterarSenhaRequisicao("velha123", "nova123")) }
        assertThat(estado.value!!.avatarIdentificador).isEqualTo("velha")
    }

    @Test
    fun `trocar a foto sobe o JPEG e poe o anexo no perfil, remover manda null explicito`() = runTest {
        coEvery { anexos.enviar(any(), TipoConteudo.IMAGEM, any()) } returns Result.success(
            AnexoEnviado(55, "nova-foto", TipoConteudo.IMAGEM, "avatar.jpg", "jpg", 3),
        )
        val pedido = slot<AlterarUsuarioRequisicao>()
        coEvery { api.alterarUsuario(capture(pedido)) } returns UsuarioDto(7, "Ana")

        repo.trocarFoto(byteArrayOf(1, 2, 3))
        assertThat(pedido.captured.avatarAnexoId).isEqualTo(JsonPrimitive(55L))
        assertThat(estado.value!!.avatarIdentificador).isEqualTo("nova-foto")

        repo.removerFoto()
        assertThat(pedido.captured.avatarAnexoId).isEqualTo(nuloExplicito)
        assertThat(estado.value!!.avatarIdentificador).isNull()
    }
}
