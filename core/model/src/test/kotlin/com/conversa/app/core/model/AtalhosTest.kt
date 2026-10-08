package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Mesmos casos do web (`utilsTexto.test.ts`, "atalhos de emoji") e do `codeBlocks.ts`. */
class AtalhosTest {
    @Test
    fun `troca o atalho logo antes do espaco e ajusta o cursor`() {
        assertThat(substituirAtalhoAntesDoCursor("oi :) ", 6)).isEqualTo(TrocaAtalho("oi 🙂 ", 6))
        assertThat(substituirAtalhoAntesDoCursor(":D\nlinha", 3)).isEqualTo(TrocaAtalho("😃\nlinha", 3))
    }

    @Test
    fun `o atalho mais longo vence`() {
        assertThat(substituirAtalhoAntesDoCursor(":-) ", 4)!!.texto).isEqualTo("🙂 ")
    }

    @Test
    fun `nao troca dentro de palavra, URL ou codigo`() {
        assertThat(substituirAtalhoAntesDoCursor("http:/ ", 7)).isNull()
        assertThat(substituirAtalhoAntesDoCursor("`:) ", 4)).isNull()
        assertThat(substituirAtalhoAntesDoCursor("```\n:) ", 7)).isNull()
        assertThat(substituirAtalhoAntesDoCursor("sem atalho ", 11)).isNull()
    }

    @Test
    fun `no envio, troca o atalho que ficou no fim`() {
        assertThat(substituirAtalhoNoFim("valeu <3")).isEqualTo("valeu ❤️")
        assertThat(substituirAtalhoNoFim("\\o/")).isEqualTo("🙌")
        assertThat(substituirAtalhoNoFim("código `x :)")).isEqualTo("código `x :)")
        assertThat(substituirAtalhoNoFim("nada aqui")).isEqualTo("nada aqui")
    }

    @Test
    fun `texto longo e mais de 10 linhas`() {
        assertThat(textoLongo((1..10).joinToString("\n"))).isFalse()
        assertThat(textoLongo((1..11).joinToString("\n"))).isTrue()
        assertThat(textoLongo((1..10).joinToString("\n") + "\n\n\n")).isFalse()
    }

    @Test
    fun `cerca maior que as crases do codigo`() {
        assertThat(blocoDeCodigo("x = 1", "python")).isEqualTo("```python\nx = 1\n```")
        assertThat(cercaCodigo("```js\nx\n```")).isEqualTo("````")
    }
}
