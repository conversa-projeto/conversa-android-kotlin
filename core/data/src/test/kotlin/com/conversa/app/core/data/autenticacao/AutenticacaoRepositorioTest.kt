package com.conversa.app.core.data.autenticacao

import com.conversa.app.core.data.MotivoFimSessao
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarDispositivoRequisicao
import com.conversa.app.core.network.dto.CadastroRequisicao
import com.conversa.app.core.network.dto.DispositivoDto
import com.conversa.app.core.network.dto.LoginRequisicao
import com.conversa.app.core.network.dto.LoginResposta
import com.conversa.app.core.network.dto.UsuarioDto
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.json.nuloExplicito
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AutenticacaoRepositorioTest {
    private val api = mockk<ConversaApi>()
    private val sessaoAtual = MutableStateFlow<Sessao?>(null)
    private val sessao = mockk<SessaoRepositorio>(relaxed = true) { every { sessao } returns sessaoAtual }
    private val preferencias = mockk<PreferenciasStore>(relaxed = true) { every { dispositivoId } returns flowOf(12L) }
    private val info = object : InfoDispositivo {
        override val nome = "Samsung " + "X".repeat(80)
        override val modelo = "SM-S918B" + "Y".repeat(80)
        override val versaoSo = "Android 16 (build muito longo)"
    }
    private val repositorio = AutenticacaoRepositorio(api, sessao, preferencias, info)

    private fun erroHttp(status: Int, corpo: String) =
        HttpException(Response.error<Any>(status, corpo.toResponseBody("application/json".toMediaType())))

    @Test
    fun `login apara o usuario, reenvia o dispositivo e salva a sessao`() = runTest {
        coEvery { api.login(any()) } returns LoginResposta(
            id = 7,
            nome = "Ana",
            email = "ana@x.com",
            dispositivo = DispositivoDto(id = 15),
            token = "jwt",
        )
        val salva = slot<Sessao>()
        coEvery { sessao.salvar(capture(salva)) } returns Unit

        val resultado = repositorio.entrar("  ana  ", "segredo")

        assertThat(resultado.isSuccess).isTrue()
        coVerify { api.login(LoginRequisicao("ana", "segredo", 12)) }
        coVerify { preferencias.salvarDispositivoId(15) }
        coVerify { preferencias.salvarUltimoLogin("ana") }
        assertThat(salva.captured).isEqualTo(Sessao(token = "jwt", usuarioId = 7, nome = "Ana", email = "ana@x.com", dispositivoId = 15))
    }

    @Test
    fun `login sem token e resposta invalida e nao salva nada`() = runTest {
        coEvery { api.login(any()) } returns LoginResposta(id = 7, nome = "Ana")

        val resultado = repositorio.entrar("ana", "x")

        assertThat(resultado.exceptionOrNull()).isInstanceOf(RespostaLoginInvalidaException::class.java)
        coVerify(exactly = 0) { sessao.salvar(any()) }
    }

    @Test
    fun `senha errada vem com a mensagem do servidor e nao e sessao expirada`() = runTest {
        coEvery { api.login(any()) } throws erroHttp(401, """{"error":"Senha incorreta!"}""")

        val erro = repositorio.entrar("ana", "x").exceptionOrNull()

        assertThat(erro).isInstanceOf(ErroApi.Servidor::class.java)
        assertThat((erro as ErroApi.Servidor).detalhe).isEqualTo("Senha incorreta!")
    }

    @Test
    fun `limite do nginx vira muitas tentativas`() = runTest {
        coEvery { api.login(any()) } throws HttpException(
            Response.error<Any>(503, "<html>503</html>".toResponseBody("text/html".toMediaType())),
        )
        assertThat(repositorio.entrar("ana", "x").exceptionOrNull()).isInstanceOf(ErroApi.MuitasTentativas::class.java)
    }

    @Test
    fun `cadastro com e-mail repetido vira erro proprio`() = runTest {
        coEvery { api.cadastrar(any()) } throws erroHttp(
            500,
            """{"error":"duplicate key value violates unique constraint \"usuario_email_key\""}""",
        )
        assertThat(repositorio.cadastrar("Ana", "ana", "a@x.com", "1234").exceptionOrNull())
            .isInstanceOf(EmailJaCadastradoException::class.java)
    }

    @Test
    fun `cadastro com login repetido mostra a mensagem do servidor`() = runTest {
        coEvery { api.cadastrar(any()) } throws erroHttp(400, """{"error":"Login já cadastrado!"}""")
        val erro = repositorio.cadastrar("Ana", "ana", "a@x.com", "1234").exceptionOrNull() as ErroApi.Servidor
        assertThat(erro.detalhe).isEqualTo("Login já cadastrado!")
    }

    @Test
    fun `cadastro apara os campos mas nao a senha`() = runTest {
        coEvery { api.cadastrar(any()) } returns UsuarioDto(id = 1, nome = "Ana")
        repositorio.cadastrar(" Ana ", " ana ", " a@x.com ", " 12 34 ").getOrThrow()
        coVerify { api.cadastrar(CadastroRequisicao("Ana", "ana", "a@x.com", " 12 34 ")) }
    }

    @Test
    fun `registrar dispositivo corta os textos nos limites das colunas`() = runTest {
        sessaoAtual.value = Sessao(token = "t", usuarioId = 7, nome = "Ana", dispositivoId = 15)
        val enviado = slot<AlterarDispositivoRequisicao>()
        coEvery { api.alterarDispositivo(capture(enviado)) } returns DispositivoDto(id = 15)

        repositorio.registrarDispositivo().getOrThrow()

        with(enviado.captured) {
            assertThat(id).isEqualTo(15)
            assertThat(nome!!.length).isEqualTo(50)
            assertThat(modelo!!.length).isEqualTo(50)
            assertThat(versaoSo!!.length).isEqualTo(15)
            assertThat(plataforma).isEqualTo("android")
            assertThat(tokenFcm).isNull()
        }
    }

    @Test
    fun `sair limpa o token de push e encerra a sessao mesmo sem rede`() = runTest {
        sessaoAtual.value = Sessao(token = "t", usuarioId = 7, nome = "Ana", dispositivoId = 15)
        coEvery { api.alterarDispositivo(any()) } throws IOException("sem rede")

        repositorio.sair()

        coVerifyOrder {
            api.alterarDispositivo(AlterarDispositivoRequisicao(id = 15, tokenFcm = nuloExplicito))
            sessao.encerrar(MotivoFimSessao.SAIU)
        }
    }
}
