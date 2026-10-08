package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Mesmos casos do web (`resumirTexto`, `parseCodeBlocks`). */
class TextoTest {
    @Test
    fun `mencao vira arroba nome`() {
        assertThat(resumirTexto("oi @[Ana Souza](7), tudo bem?")).isEqualTo("oi @Ana Souza, tudo bem?")
        assertThat(resumirTexto("@[Ana](7) e @[Bruno](8)")).isEqualTo("@Ana e @Bruno")
    }

    @Test
    fun `texto sem marcacao fica igual, com espacos normalizados`() {
        assertThat(resumirTexto("  bom   dia\n\nturma  ")).isEqualTo("bom dia turma")
    }

    @Test
    fun `bloco de codigo vira Codigo com a linguagem`() {
        assertThat(resumirTexto("veja:\n```kotlin\nval x = 1\n```\nfim")).isEqualTo("veja: Código (kotlin) fim")
    }

    @Test
    fun `bloco sem linguagem vira so Codigo`() {
        assertThat(resumirTexto("```\nls -la\n```")).isEqualTo("Código")
    }

    @Test
    fun `cerca maior permite crases triplas dentro (caso do web)`() {
        val bloco = separarBlocosDeCodigo("````md\n```js\nx\n```\n````").single() as SegmentoCodigo.Codigo

        assertThat(bloco.conteudo).isEqualTo("```js\nx\n```")
        assertThat(bloco.linguagem).isEqualTo("md")
    }

    @Test
    fun `crases sem quebra de linha nao abrem bloco`() {
        assertThat(resumirTexto("use ```assim``` mesmo")).isEqualTo("use ```assim``` mesmo")
    }

    @Test
    fun `bloco sem fechamento nao e codigo`() {
        assertThat(temBlocoDeCodigo("```js\nconsole.log(1)")).isFalse()
    }

    @Test
    fun `crases coladas no fim da mensagem fecham o bloco`() {
        val segmentos = separarBlocosDeCodigo("```js\nconsole.log(1)```")
        assertThat(segmentos).containsExactly(SegmentoCodigo.Codigo("console.log(1)", "js"))
    }

    @Test
    fun `crases no meio da linha fazem parte do codigo`() {
        val segmentos = separarBlocosDeCodigo("```js\nx.split('```')\n```")
        assertThat(segmentos).containsExactly(SegmentoCodigo.Codigo("x.split('```')", "js"))
    }

    @Test
    fun `bloco interno com linguagem e fechado pela proxima linha de crases`() {
        val texto = "````md\n# Exemplo\n```kotlin\nval a = 1\n```\n````"
        val segmentos = separarBlocosDeCodigo(texto)
        assertThat(segmentos).containsExactly(SegmentoCodigo.Codigo("# Exemplo\n```kotlin\nval a = 1\n```", "md"))
    }

    @Test
    fun `markdown fecha na ultima linha de crases`() {
        val texto = "```md\ntexto\n```\nmais\n```"
        val segmentos = separarBlocosDeCodigo(texto)
        assertThat(segmentos).containsExactly(SegmentoCodigo.Codigo("texto\n```\nmais", "md"))
    }

    @Test
    fun `dois blocos e texto entre eles`() {
        val texto = "a\n```py\nprint(1)\n```\nb\n```sql\nselect 1\n```"
        assertThat(resumirTexto(texto)).isEqualTo("a Código (py) b Código (sql)")
    }

    @Test
    fun `mencao dentro de texto com codigo`() {
        assertThat(resumirTexto("@[Ana](7) olha\n```\nx\n```")).isEqualTo("@Ana olha Código")
    }
}
