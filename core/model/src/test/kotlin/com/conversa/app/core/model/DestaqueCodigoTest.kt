package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Destaque de sintaxe dos blocos de código (TODO 7.9). */
class DestaqueCodigoTest {
    private fun trechos(codigo: String, linguagem: String?) =
        destacarCodigo(codigo, linguagem).map { codigo.substring(it.inicio, it.fim) to it.tipo }

    @Test
    fun `javascript - palavras, texto, numero e comentarios`() {
        val codigo = "const x = 'a // b' + 42 // fim\n/* bloco */ return `t`"

        assertThat(trechos(codigo, "js")).containsExactly(
            "const" to TipoToken.PALAVRA_CHAVE,
            "'a // b'" to TipoToken.TEXTO,
            "42" to TipoToken.NUMERO,
            "// fim" to TipoToken.COMENTARIO,
            "/* bloco */" to TipoToken.COMENTARIO,
            "return" to TipoToken.PALAVRA_CHAVE,
            "`t`" to TipoToken.TEXTO,
        ).inOrder()
    }

    @Test
    fun `numero colado num nome nao e numero, e aspas escapadas continuam o texto`() {
        assertThat(trechos("let v2 = \"a\\\"b\"", "ts")).containsExactly(
            "let" to TipoToken.PALAVRA_CHAVE,
            "\"a\\\"b\"" to TipoToken.TEXTO,
        ).inOrder()
    }

    @Test
    fun `sql e pascal sem diferenciar maiusculas`() {
        assertThat(trechos("SELECT id FROM t -- x", "sql").map { it.first }).containsExactly("SELECT", "FROM", "-- x").inOrder()
        assertThat(trechos("Begin { c } (* d *) End;", "delphi").map { it.second })
            .containsExactly(TipoToken.PALAVRA_CHAVE, TipoToken.COMENTARIO, TipoToken.COMENTARIO, TipoToken.PALAVRA_CHAVE).inOrder()
    }

    @Test
    fun `python e bash com comentario de cerquilha`() {
        assertThat(trechos("def f(): # oi", "python").map { it.first }).containsExactly("def", "# oi").inOrder()
        assertThat(trechos("echo \"x\" # y", "sh").map { it.second })
            .containsExactly(TipoToken.PALAVRA_CHAVE, TipoToken.TEXTO, TipoToken.COMENTARIO).inOrder()
    }

    @Test
    fun `xml - tag, atributo, valor e comentario`() {
        assertThat(trechos("<!-- c --><a href=\"x\">t</a>", "html")).containsExactly(
            "<!-- c -->" to TipoToken.COMENTARIO,
            "a" to TipoToken.TAG,
            "href" to TipoToken.ATRIBUTO,
            "\"x\"" to TipoToken.TEXTO,
            "a" to TipoToken.TAG,
        ).inOrder()
    }

    @Test
    fun `linguagem sem destaque nao marca nada`() {
        assertThat(destacarCodigo("const x = 1", "mermaid")).isEmpty()
        assertThat(destacarCodigo("const x = 1", null)).isEmpty()
        assertThat(temDestaque("JSON")).isTrue()
        assertThat(temDestaque("markdown")).isFalse()
        assertThat(ehLinguagemMarkdown("MD")).isTrue()
        assertThat(ehLinguagemMarkdown("markdown")).isTrue()
        assertThat(ehLinguagemMarkdown("mermaid")).isFalse()
        assertThat(ehLinguagemMarkdown(null)).isFalse()
        assertThat(ehLinguagemMermaid("Mermaid")).isTrue()
        assertThat(ehLinguagemMermaid("md")).isFalse()
    }

    @Test
    fun `texto sem fechar para no fim da linha`() {
        assertThat(trechos("x = 'aberto\ny = 1", "py")).containsExactly("'aberto" to TipoToken.TEXTO, "1" to TipoToken.NUMERO).inOrder()
    }
}
