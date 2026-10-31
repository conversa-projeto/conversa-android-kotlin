package com.conversa.app.core.registro

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Os arquivos do registro: um por dia, partes, sete dias e o teto total. */
class ArquivosDoRegistroTest {
    @get:Rule val temporaria = TemporaryFolder()

    private class Relogio(var agora: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = agora
    }

    private val relogio = Relogio(Instant.parse("2026-10-31T12:00:00Z"))

    private fun arquivos(limiteArquivo: Long = 1024, limiteTotal: Long = 1024 * 1024) =
        ArquivosDoRegistro(temporaria.root, relogio, limiteArquivo, limiteTotal)

    private fun nomes() = temporaria.root.list().orEmpty().sorted()

    @Test
    fun `escreve no arquivo do dia e acrescenta no mesmo arquivo`() {
        val registro = arquivos()
        registro.escrever(listOf("a", "b"))
        registro.fechar()
        arquivos().escrever(listOf("c"))

        assertThat(nomes()).containsExactly("2026-10-31.log")
        assertThat(File(temporaria.root, "2026-10-31.log").readLines()).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `virou o dia, arquivo novo`() {
        val registro = arquivos()
        registro.escrever(listOf("sexta"))
        relogio.agora = Instant.parse("2026-11-01T00:00:01Z")
        registro.escrever(listOf("sabado"))

        assertThat(nomes()).containsExactly("2026-10-31.log", "2026-11-01.log").inOrder()
        assertThat(File(temporaria.root, "2026-11-01.log").readLines()).containsExactly("sabado")
    }

    @Test
    fun `dia grande continua na parte seguinte`() {
        val registro = arquivos(limiteArquivo = 100)
        registro.escrever(List(30) { "linha %02d".format(it) })

        assertThat(nomes()).containsAtLeast("2026-10-31.log", "2026-10-31.2.log")
        assertThat(registro.lista().map { it.parte }).isInOrder()
        // Nada se perde e a ordem fica: lendo as partes em sequência.
        assertThat(registro.ultimasLinhas(100)).isEqualTo(List(30) { "linha %02d".format(it) })
        registro.lista().forEach { assertThat(it.arquivo.length()).isAtMost(100L + 8) }
    }

    @Test
    fun `guarda sete dias contando hoje`() {
        for (dia in 0..9) {
            File(temporaria.root, "${LocalDate.parse("2026-10-22").plusDays(dia.toLong())}.log").writeText("x\n")
        }
        File(temporaria.root, "2026-10-24.2.log").writeText("x\n")
        File(temporaria.root, "outro.txt").writeText("não é do registro")

        arquivos().limparAntigos()

        assertThat(nomes()).containsExactly(
            "2026-10-25.log",
            "2026-10-26.log",
            "2026-10-27.log",
            "2026-10-28.log",
            "2026-10-29.log",
            "2026-10-30.log",
            "2026-10-31.log",
            "outro.txt",
        )
    }

    @Test
    fun `passou do teto total, sai o mais antigo e nunca o aberto`() {
        File(temporaria.root, "2026-10-29.log").writeText("x".repeat(400))
        File(temporaria.root, "2026-10-30.log").writeText("x".repeat(400))
        val registro = arquivos(limiteArquivo = 10_000, limiteTotal = 700)

        registro.escrever(listOf("y".repeat(300)))

        assertThat(nomes()).containsExactly("2026-10-30.log", "2026-10-31.log").inOrder()
    }

    @Test
    fun `ultimas linhas atravessam os arquivos`() {
        File(temporaria.root, "2026-10-30.log").writeText("1\n2\n3\n")
        val registro = arquivos()
        registro.escrever(listOf("4", "5"))

        assertThat(registro.ultimasLinhas(3)).containsExactly("3", "4", "5").inOrder()
        assertThat(registro.ultimasLinhas(50)).containsExactly("1", "2", "3", "4", "5").inOrder()
    }

    @Test
    fun `compacta todos os arquivos e apaga tudo`() {
        File(temporaria.root, "2026-10-30.log").writeText("ontem\n")
        val registro = arquivos()
        registro.escrever(listOf("hoje"))

        val zip = registro.compactar(File(temporaria.newFolder("saida"), "registro.zip"))
        ZipFile(zip).use { arquivo ->
            assertThat(arquivo.entries().toList().map { it.name }).containsExactly("2026-10-30.log", "2026-10-31.log").inOrder()
        }

        registro.apagarTudo()
        assertThat(registro.lista()).isEmpty()
        registro.escrever(listOf("depois de apagar"))
        assertThat(registro.ultimasLinhas(10)).containsExactly("depois de apagar")
    }

    @Test
    fun `so le nomes do registro`() {
        val pasta = temporaria.root
        fun ler(nome: String) = ArquivosDoRegistro.lerNome(File(pasta, nome).apply { writeText("") })

        assertThat(ler("2026-10-31.log")?.parte).isEqualTo(1)
        assertThat(ler("2026-10-31.3.log")?.parte).isEqualTo(3)
        assertThat(ler("2026-10-31.1.log")).isNull()
        assertThat(ler("2026-13-31.log")).isNull()
        assertThat(ler("2026-10-31.a.log")).isNull()
        assertThat(ler("registro.zip")).isNull()
    }
}
