package com.conversa.app.core.data.anexos

import com.conversa.app.core.network.http.ErroApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Baixar" (4.7): cache → Downloads, aviso e resultado; falhas não deixam arquivo pela metade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadsRepositorioTest {
    private val arquivos = mockk<ArquivosLocais>()
    private val avisados = mutableListOf<ResultadoDownload.Salvo>()
    private val pasta = PastaFalsa()

    private fun TestScope.repositorio() =
        DownloadsRepositorio(arquivos, pasta, { avisados += it }, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun arquivoNoCache(texto: String): File = Files.createTempFile("anexo", ".pdf").toFile().apply { writeText(texto) }

    @Test
    fun `salva em Downloads com o tipo pela extensao, conclui e avisa`() = runTest {
        coEvery { arquivos.baixar("h1", "Relatório.pdf") } returns Result.success(arquivoNoCache("conteudo do pdf"))
        val repo = repositorio()
        val resultado = backgroundScope.launch { repo.resultados.first() }

        repo.salvarEmDownloads("h1", "Relatório.pdf", null)
        runCurrent()

        val destino = pasta.criados.single()
        assertThat(destino.nome).isEqualTo("Relatório.pdf")
        assertThat(destino.mime).isEqualTo("application/pdf")
        assertThat(destino.bytes.toString(Charsets.UTF_8)).isEqualTo("conteudo do pdf")
        assertThat(destino.concluido).isTrue()
        assertThat(avisados.single()).isEqualTo(ResultadoDownload.Salvo("Relatório.pdf", "content://downloads/1", "application/pdf"))
        assertThat(resultado.isCompleted).isTrue()
    }

    @Test
    fun `sem baixar nao cria nada e avisa a falha`() = runTest {
        coEvery { arquivos.baixar(any(), any()) } returns Result.failure(ErroApi.SemConexao(IOException("caiu")))
        val repo = repositorio()
        var recebido: ResultadoDownload? = null
        backgroundScope.launch { recebido = repo.resultados.first() }

        repo.salvarEmDownloads("h2", "a.zip", null)
        runCurrent()

        assertThat(pasta.criados).isEmpty()
        assertThat(recebido).isEqualTo(ResultadoDownload.Falhou("a.zip"))
        assertThat(avisados).isEmpty()
    }

    @Test
    fun `erro ao gravar descarta o arquivo pela metade`() = runTest {
        coEvery { arquivos.baixar(any(), any()) } returns Result.success(arquivoNoCache("x"))
        pasta.falharAoGravar = true
        val repo = repositorio()
        var recebido: ResultadoDownload? = null
        backgroundScope.launch { recebido = repo.resultados.first() }

        repo.salvarEmDownloads("h3", "b.txt", "text/plain")
        runCurrent()

        assertThat(pasta.criados.single().descartado).isTrue()
        assertThat(recebido).isEqualTo(ResultadoDownload.Falhou("b.txt"))
        assertThat(avisados).isEmpty()
    }

    private class PastaFalsa : PastaDownloads {
        var falharAoGravar = false
        val criados = mutableListOf<DestinoFalso>()

        override fun criar(nome: String, mime: String) = DestinoFalso(
            nome,
            mime,
            "content://downloads/${criados.size + 1}",
            falharAoGravar,
        ).also {
            criados +=
                it
        }

        override fun escolhido(uri: String) = DestinoFalso("", "", uri, falharAoGravar).also { criados += it }
    }

    private class DestinoFalso(val nome: String, val mime: String, override val uri: String, private val falhar: Boolean) :
        DestinoDownload {
        val bytes = ByteArrayOutputStream()
        var concluido = false
        var descartado = false

        override fun abrir(): OutputStream = if (falhar) throw IOException("disco cheio") else bytes

        override fun concluir() {
            concluido = true
        }

        override fun descartar() {
            descartado = true
        }
    }
}
