package com.conversa.app.core.data.anexos

import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.network.http.ErroApi
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Baixar anexo para o cache (ANX-09, 4.5): uma vez só, `.part` e URL vencida. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArquivosLocaisTest {
    private val minio = MockWebServer()
    private val anexos = mockk<AnexosRepositorio>()

    @Before
    fun subir() = minio.start()

    @After
    fun parar() = minio.close()

    private fun kotlinx.coroutines.test.TestScope.arquivos() =
        ArquivosLocais(ApplicationProvider.getApplicationContext(), anexos, OkHttpClient(), StandardTestDispatcher(testScheduler))

    @Test
    fun `URL vencida - pede outra e baixa, e da segunda vez usa o cache`() = runTest {
        coEvery { anexos.url("h1") } returnsMany listOf(
            Result.success(minio.url("/velha").toString()),
            Result.success(minio.url("/nova").toString()),
        )
        responder403DepoisOk()
        val arquivos = arquivos()

        val arquivo = arquivos.baixar("h1", "som.wav").getOrThrow()

        assertThat(arquivo.readText()).isEqualTo("conteudo")
        assertThat(arquivo.name).isEqualTo("som.wav")
        assertThat(File(arquivo.parentFile, "som.wav.part").exists()).isFalse()
        verify(exactly = 1) { anexos.esquecerUrl("h1") }
        assertThat(minio.requestCount).isEqualTo(2)

        assertThat(arquivos.baixar("h1", "som.wav").getOrThrow()).isEqualTo(arquivo)
        assertThat(minio.requestCount).isEqualTo(2)
    }

    @Test
    fun `outro erro nao tenta de novo e nao deixa arquivo`() = runTest {
        coEvery { anexos.url("h2") } returns Result.success(minio.url("/x").toString())
        minio.enqueue(MockResponse.Builder().code(404).build())

        val resultado = arquivos().baixar("h2", "a.pdf")

        assertThat((resultado.exceptionOrNull() as ErroApi.Servidor).status).isEqualTo(404)
        assertThat(minio.requestCount).isEqualTo(1)
    }

    @Test
    fun `nome seguro nunca sai da pasta`() {
        assertThat(nomeSeguro("../../segredo.txt")).isEqualTo("segredo.txt")
        assertThat(nomeSeguro("C:\\Windows\\a.exe")).isEqualTo("a.exe")
        assertThat(nomeSeguro("..")).isEqualTo("arquivo")
        assertThat(nomeSeguro(".")).isEqualTo("arquivo")
        assertThat(nomeSeguro("  ")).isEqualTo("arquivo")
        assertThat(nomeSeguro("nota\u0000fiscal.pdf")).isEqualTo("notafiscal.pdf")
        assertThat(nomeSeguro(".bashrc")).isEqualTo(".bashrc")
    }

    private fun responder403DepoisOk() {
        every { anexos.esquecerUrl(any()) } just Runs
        minio.enqueue(MockResponse.Builder().code(403).build())
        minio.enqueue(MockResponse.Builder().body("conteudo").build())
    }
}
