package com.conversa.app.core.network.http

import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.auth.EventosSessao
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.network.dto.LoginRequisicao
import com.conversa.app.core.network.json.ConversaJson
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Interceptadores + mapeamento de erros contra um servidor falso (MockWebServer). */
class RedeTest {
    private val servidor = MockWebServer()
    private val config = MutableStateFlow<ServerConfig?>(null)
    private var token: String? = "tok"
    private val eventos = EventosSessao()
    private lateinit var api: ConversaApi
    private lateinit var cliente: OkHttpClient

    @Before
    fun preparar() {
        servidor.start()
        config.value = ServerConfig.aPartirDe(servidor.url("/").toString())
        val provedor = object : ServerConfigProvider {
            override val atual = config
        }
        cliente = OkHttpClient.Builder()
            .readTimeout(1, TimeUnit.SECONDS)
            .addInterceptor(EnderecoInterceptor(provedor))
            .addInterceptor(AutenticacaoInterceptor({ token }, eventos, provedor))
            .build()
        api = Retrofit.Builder()
            .baseUrl(BASE_FICTICIA)
            .client(cliente)
            .addConverterFactory(ConversaJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ConversaApi::class.java)
    }

    @After
    fun encerrar() = servidor.close()

    private fun json(codigo: Int, corpo: String) = MockResponse.Builder()
        .code(codigo)
        .addHeader("Content-Type", "application/json")
        .body(corpo)
        .build()

    @Test
    fun `sucesso troca a base ficticia e manda o token`() = runTest {
        servidor.enqueue(json(200, "[1,2]"))
        val online = chamarApi { api.contatosOnline() }.getOrThrow()
        assertThat(online).containsExactly(1L, 2L)
        val requisicao = servidor.takeRequest()
        assertThat(requisicao.url.encodedPath).isEqualTo("/api/contatos/online")
        assertThat(requisicao.headers["Authorization"]).isEqualTo("Bearer tok")
        assertThat(requisicao.headers[AutenticacaoInterceptor.CABECALHO_PUBLICA]).isNull()
    }

    @Test
    fun `token so vai para a api do servidor, nunca para o storage nem para outro host`() {
        servidor.enqueue(MockResponse.Builder().code(200).build())
        servidor.enqueue(MockResponse.Builder().code(200).build())
        // URL assinada do MinIO no mesmo host: com Authorization o MinIO recusa.
        cliente.newCall(okhttp3.Request.Builder().url(servidor.url("/storage/chat/x?X-Amz-Signature=a")).build()).execute().close()
        assertThat(servidor.takeRequest().headers["Authorization"]).isNull()
        // Mesmo caminho /api/ mas outro host (porta diferente): também sem token.
        val outro = MockWebServer()
        outro.start()
        outro.enqueue(MockResponse.Builder().code(200).build())
        cliente.newCall(okhttp3.Request.Builder().url(outro.url("/api/x")).build()).execute().close()
        assertThat(outro.takeRequest().headers["Authorization"]).isNull()
        outro.close()
        // A API do servidor configurado, direto: com token.
        cliente.newCall(okhttp3.Request.Builder().url(servidor.url("/api/contatos/online")).build()).execute().close()
        assertThat(servidor.takeRequest().headers["Authorization"]).isEqualTo("Bearer tok")
    }

    @Test
    fun `trocar o servidor vale na proxima requisicao sem recriar o Retrofit`() = runTest {
        val outro = MockWebServer()
        outro.start()
        try {
            servidor.enqueue(json(200, "[1]"))
            outro.enqueue(json(200, "[2]"))
            assertThat(chamarApi { api.contatosOnline() }.getOrThrow()).containsExactly(1L)
            config.value = ServerConfig.aPartirDe(outro.url("/").toString())
            assertThat(chamarApi { api.contatosOnline() }.getOrThrow()).containsExactly(2L)
            assertThat(outro.takeRequest().url.encodedPath).isEqualTo("/api/contatos/online")
        } finally {
            outro.close()
        }
    }

    @Test
    fun `query e mantida`() = runTest {
        servidor.enqueue(json(200, "[]"))
        chamarApi { api.mensagens(42, mensagensPrevias = 80) }
        val url = servidor.takeRequest().url
        assertThat(url.queryParameter("conversa")).isEqualTo("42")
        assertThat(url.queryParameter("mensagensprevias")).isEqualTo("80")
    }

    @Test
    fun `401 autenticado vira sessao expirada e avisa o app`() = runTest {
        servidor.enqueue(json(401, """{"error":"Token inválido ou expirado"}"""))
        var avisou = false
        val coleta = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launchColeta { avisou = true }
        val erro = chamarApi { api.conversas() }.exceptionOrNull()
        assertThat(erro).isInstanceOf(ErroApi.SessaoExpirada::class.java)
        assertThat(avisou).isTrue()
        coleta.cancel()
    }

    private fun kotlinx.coroutines.CoroutineScope.launchColeta(bloco: () -> Unit) =
        launch { eventos.sessaoExpirada.collect { bloco() } }

    @Test
    fun `login e rota publica - sem token e 401 nao encerra a sessao`() = runTest {
        servidor.enqueue(json(401, """{"error":"Senha incorreta!"}"""))
        var avisou = false
        val coleta = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launchColeta { avisou = true }
        val erro = chamarApi { api.login(LoginRequisicao("ana", "x")) }.exceptionOrNull()
        assertThat(erro).isInstanceOf(ErroApi.Servidor::class.java)
        assertThat((erro as ErroApi.Servidor).mensagemAmigavel()).isEqualTo("Senha incorreta!")
        assertThat(avisou).isFalse()
        val requisicao = servidor.takeRequest()
        assertThat(requisicao.headers["Authorization"]).isNull()
        assertThat(requisicao.headers[AutenticacaoInterceptor.CABECALHO_PUBLICA]).isNull()
        coleta.cancel()
    }

    @Test
    fun `erro do servidor traz a mensagem do campo error`() = runTest {
        servidor.enqueue(json(403, """{"error":"Acesso negado!"}"""))
        val erro = chamarApi { api.conversas() }.exceptionOrNull() as ErroApi.Servidor
        assertThat(erro.status).isEqualTo(403)
        assertThat(erro.mensagemAmigavel()).isEqualTo("Acesso negado!")
    }

    @Test
    fun `500 nao mostra o texto cru do Postgres`() = runTest {
        servidor.enqueue(json(500, """{"error":"duplicate key value violates unique constraint"}"""))
        val erro = chamarApi { api.conversas() }.exceptionOrNull() as ErroApi
        assertThat(erro.mensagemAmigavel()).doesNotContain("duplicate")
    }

    @Test
    fun `pagina html do nginx vira servidor indisponivel`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(502).addHeader("Content-Type", "text/html").body("<html>Bad Gateway</html>").build())
        assertThat(chamarApi { api.conversas() }.exceptionOrNull()).isInstanceOf(ErroApi.ServidorIndisponivel::class.java)
    }

    @Test
    fun `503 html no login vira muitas tentativas`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(503).addHeader("Content-Type", "text/html").body("<html>503</html>").build())
        assertThat(chamarApi { api.login(LoginRequisicao("a", "b")) }.exceptionOrNull()).isInstanceOf(ErroApi.MuitasTentativas::class.java)
    }

    @Test
    fun `sem servidor configurado`() = runTest {
        config.value = null
        assertThat(chamarApi { api.conversas() }.exceptionOrNull()).isInstanceOf(ErroApi.ServidorNaoConfigurado::class.java)
    }

    @Test
    fun `tempo esgotado vira sem conexao`() = runTest {
        servidor.enqueue(MockResponse.Builder().bodyDelay(3, TimeUnit.SECONDS).body("[]").build())
        assertThat(chamarApi { api.contatosOnline() }.exceptionOrNull()).isInstanceOf(ErroApi.SemConexao::class.java)
    }

    @Test
    fun `json inesperado vira resposta invalida`() = runTest {
        servidor.enqueue(json(200, """{"nao":"lista"}"""))
        assertThat(chamarApi { api.contatosOnline() }.exceptionOrNull()).isInstanceOf(ErroApi.RespostaInvalida::class.java)
    }
}
