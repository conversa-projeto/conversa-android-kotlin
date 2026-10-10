package com.conversa.app.core.data.anexos

import com.conversa.app.core.model.AnexoDaConversa
import com.conversa.app.core.model.DirecaoAnexos
import com.conversa.app.core.model.FiltroAnexos
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AnexoItemDto
import com.conversa.app.core.network.dto.ConfirmarAnexoDto
import com.conversa.app.core.network.dto.IncluirAnexoRequisicao
import com.conversa.app.core.network.dto.IncluirAnexoResposta
import com.conversa.app.core.network.dto.UrlAnexoDto
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnexosRepositorioTest {
    private val minio = MockWebServer()
    private val api = mockk<ConversaApi>()
    private val bytes = Random(7).nextBytes(300_000)
    private val fonte = object : FonteArquivo {
        override val nome = "Relatório Final.PDF"
        override val tamanho = bytes.size.toLong()
        override val mime = "application/pdf"
        var aberturas = 0

        override fun abrir(): InputStream {
            aberturas++
            return ByteArrayInputStream(bytes)
        }
    }
    private val identificador = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private var agora = Instant.parse("2026-10-07T12:00:00Z")
    private val relogio = object : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = agora
    }

    @Before
    fun subir() = minio.start()

    @After
    fun parar() = minio.close()

    private fun kotlinx.coroutines.test.TestScope.repositorio() =
        AnexosRepositorio(api, OkHttpClient(), relogio, StandardTestDispatcher(testScheduler))

    @Test
    fun `sha256 em fluxo e extensao`() {
        assertThat(calcularSha256(ByteArrayInputStream("abc".toByteArray())))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(extensaoDe("Relatório Final.PDF")).isEqualTo("pdf")
        assertThat(extensaoDe("sem-extensao")).isEmpty()
        assertThat(extensaoDe("x.extensaomuitolonga")).isEqualTo("extensaomu")
    }

    @Test
    fun `novo - sobe os bytes na url assinada sem token, com progresso, e confirma`() = runTest {
        val pedido = slot<IncluirAnexoRequisicao>()
        coEvery { api.incluirAnexo(capture(pedido)) } returns
            IncluirAnexoResposta(
                existe = false,
                id = 9,
                uploadUrl = minio.url("/storage/chat/a?X-Amz-Signature=x").toString(),
                uploadStatus = 0,
            )
        coEvery { api.confirmarAnexo(identificador) } returns ConfirmarAnexoDto(true, 1)
        minio.enqueue(MockResponse.Builder().code(200).build())
        var ultimo = 0L

        val enviado = repositorio().enviar(fonte, TipoConteudo.ARQUIVO) { enviados, _ -> ultimo = enviados }.getOrThrow()

        assertThat(enviado.identificador).isEqualTo(identificador)
        assertThat(pedido.captured).isEqualTo(IncluirAnexoRequisicao(identificador, 3, "Relatório Final.PDF", "pdf", bytes.size.toLong()))
        val recebido = minio.takeRequest()
        assertThat(recebido.method).isEqualTo("PUT")
        assertThat(recebido.body!!.toByteArray()).isEqualTo(bytes)
        assertThat(recebido.headers["Authorization"]).isNull()
        assertThat(ultimo).isEqualTo(bytes.size.toLong())
        coVerify { api.confirmarAnexo(identificador) }
    }

    @Test
    fun `ja existe - nao sobe nada e a url devolvida vira a de leitura`() = runTest {
        coEvery { api.incluirAnexo(any()) } returns IncluirAnexoResposta(existe = true, id = 9, uploadUrl = "https://x/storage/leitura")
        val repo = repositorio()

        repo.enviar(fonte, TipoConteudo.ARQUIVO).getOrThrow()

        assertThat(minio.requestCount).isEqualTo(0)
        coVerify(exactly = 0) { api.confirmarAnexo(any()) }
        assertThat(repo.url(identificador).getOrThrow()).isEqualTo("https://x/storage/leitura")
        // Só o hash leu o arquivo.
        assertThat(fonte.aberturas).isEqualTo(1)
    }

    @Test
    fun `url vencida no meio - pede outra uma vez`() = runTest {
        var vez = 0
        coEvery { api.incluirAnexo(any()) } answers {
            vez++
            IncluirAnexoResposta(existe = false, id = 9, uploadUrl = minio.url("/storage/chat/a?v=$vez").toString())
        }
        coEvery { api.confirmarAnexo(any()) } returns ConfirmarAnexoDto(true, 1)
        minio.enqueue(MockResponse.Builder().code(403).build())
        minio.enqueue(MockResponse.Builder().code(200).build())

        assertThat(repositorio().enviar(fonte, TipoConteudo.ARQUIVO).isSuccess).isTrue()
        assertThat(vez).isEqualTo(2)
    }

    @Test
    fun `maior que 1 GiB nem comeca`() = runTest {
        val grande = object : FonteArquivo {
            override val nome = "x.mp4"
            override val tamanho = AnexosRepositorio.LIMITE_BYTES + 1
            override val mime = null

            override fun abrir(): InputStream = error("não deveria abrir")
        }
        assertThat(
            repositorio().enviar(grande, TipoConteudo.ARQUIVO).exceptionOrNull(),
        ).isInstanceOf(ArquivoGrandeDemaisException::class.java)
    }

    @Test
    fun `url de leitura fica no cache ate perto de vencer e pode ser esquecida`() = runTest {
        var vez = 0
        coEvery { api.urlAnexo("abc") } answers { UrlAnexoDto("https://x/${++vez}") }
        val repo = repositorio()

        assertThat(repo.url("abc").getOrThrow()).isEqualTo("https://x/1")
        agora = agora.plusSeconds(500)
        assertThat(repo.url("abc").getOrThrow()).isEqualTo("https://x/1")
        agora = agora.plusSeconds(60)
        assertThat(repo.url("abc").getOrThrow()).isEqualTo("https://x/2")
        repo.esquecerUrl("abc")
        assertThat(repo.url("abc").getOrThrow()).isEqualTo("https://x/3")
    }

    @Test
    fun `anexos da conversa - filtros na rota, modelo e URL que ja entra no cache`() = runTest {
        coEvery { api.anexos(any(), any(), any(), any(), any(), any()) } returns listOf(
            AnexoItemDto(
                anexoId = 9,
                identificador = "img",
                nome = null,
                tamanho = 2048,
                criadoEm = agora,
                tipo = 2,
                mensagemId = 70,
                conversaId = 3,
                autorNome = "Ana",
                url = "https://s/img",
            ),
        )
        val repo = repositorio()

        val lista = repo.daConversa(3, DirecaoAnexos.RECEBIDOS, FiltroAnexos.IMAGENS, antes = 120).getOrThrow()

        coVerify { api.anexos(conversaId = 3, autorId = 0, direcao = "recebidos", tipos = "2", antes = 120, limite = 60) }
        assertThat(lista).containsExactly(AnexoDaConversa(9, "img", "", "", 2048, agora, TipoConteudo.IMAGEM, 70, 3, "Ana"))
        assertThat(repo.url("img").getOrThrow()).isEqualTo("https://s/img")
        coVerify(exactly = 0) { api.urlAnexo(any()) }
    }
}
