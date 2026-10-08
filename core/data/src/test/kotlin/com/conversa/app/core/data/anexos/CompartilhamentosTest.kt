package com.conversa.app.core.data.anexos

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.model.TipoConteudo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Compartilhamento recebido (4.9): só `content://` de outro app, cópia para o cache, limite de tamanho. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompartilhamentosTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val fontes = mockk<FontesArquivo>()
    private val arquivos = mockk<ArquivosLocais> {
        every { uriCompartilhado(any()) } answers { "content://${contexto.packageName}.arquivos/compartilhados/" + firstArg<File>().name }
    }

    private fun TestScope.compartilhamentos() = Compartilhamentos(contexto, fontes, arquivos, StandardTestDispatcher(testScheduler))

    private fun oferecer(uri: String, nome: String, conteudo: String, tamanho: Long = conteudo.length.toLong()) {
        shadowOf(contexto.contentResolver).registerInputStream(Uri.parse(uri), conteudo.byteInputStream())
        every { fontes.descrever(uri) } returns AnexoLocal(uri, nome, tamanho, "application/pdf", TipoConteudo.ARQUIVO)
    }

    @Test
    fun `copia o arquivo de outro app e entrega uma vez so`() = runTest {
        oferecer("content://outro.app/doc/1", "relatório.pdf", "conteudo")
        val c = compartilhamentos()

        assertThat(c.receber("veja", listOf("content://outro.app/doc/1"))).isTrue()
        val itens = c.retirar()!!

        assertThat(itens.texto).isEqualTo("veja")
        val anexo = itens.anexos.single()
        assertThat(anexo.nome).isEqualTo("relatório.pdf")
        assertThat(anexo.uri).isEqualTo("content://${contexto.packageName}.arquivos/compartilhados/relatório.pdf")
        val copia = File(contexto.cacheDir, "compartilhados").walkTopDown().single { it.isFile }
        assertThat(copia.readText()).isEqualTo("conteudo")
        assertThat(c.retirar()).isNull()
    }

    @Test
    fun `recusa file e o proprio app, e o que passa de 1 GiB`() = runTest {
        val c = compartilhamentos()
        oferecer("content://outro.app/grande", "filme.mp4", "x", tamanho = AnexosRepositorio.LIMITE_BYTES + 1)

        val aceitou = c.receber(
            "texto",
            listOf(
                "file:///data/data/${contexto.packageName}/files/datastore/sessao.bin",
                "content://${contexto.packageName}.arquivos/anexos/x/segredo.pdf",
                "content://outro.app/grande",
            ),
        )

        assertThat(aceitou).isTrue()
        val itens = c.retirar()!!
        assertThat(itens.anexos).isEmpty()
        assertThat(itens.ignorados).isEqualTo(3)
    }

    @Test
    fun `sem texto e sem arquivo aceito nao ha o que enviar`() = runTest {
        val c = compartilhamentos()
        assertThat(c.receber(null, listOf("file:///sdcard/a.txt"))).isFalse()
        assertThat(c.pendente.value).isNull()
    }

    @Test
    fun `desistir apaga a copia, e um novo compartilhamento substitui o pendente`() = runTest {
        oferecer("content://outro.app/1", "a.pdf", "a")
        oferecer("content://outro.app/2", "b.pdf", "b")
        val c = compartilhamentos()

        c.receber(null, listOf("content://outro.app/1"))
        c.receber(null, listOf("content://outro.app/2"))
        assertThat(
            File(contexto.cacheDir, "compartilhados").walkTopDown().filter {
                it.isFile
            }.map { it.name }.toList(),
        ).containsExactly("b.pdf")

        c.descartar()
        assertThat(File(contexto.cacheDir, "compartilhados").walkTopDown().filter { it.isFile }.toList()).isEmpty()
    }
}
