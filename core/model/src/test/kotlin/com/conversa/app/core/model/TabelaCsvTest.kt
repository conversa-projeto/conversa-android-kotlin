package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tabelas CSV e TSV (MSG-26), com os casos de `tabelaCsv.test.ts` do web. */
class TabelaCsvTest {
    private val exemplo = listOf(
        "Teste\tResultado",
        "9998 liga para o 9996 registrado com o sip.js 0.21 (o do app)\t486 Busy Here depois de ~7 s",
        "9996 liga para o *43\tatende normalmente",
    ).joinToString("\n")

    @Test
    fun `separa linhas e celulas e ignora linhas vazias`() {
        assertThat(lerTabela("a;b\r\n\nc;d\n", ';')).containsExactly(listOf("a", "b"), listOf("c", "d")).inOrder()
    }

    @Test
    fun `respeita aspas, aspas duplas e quebra de linha dentro delas`() {
        assertThat(lerTabela("\"x;y\";\"com \"\"aspas\"\"\"\n\"duas\nlinhas\";z", ';'))
            .containsExactly(listOf("x;y", "com \"aspas\""), listOf("duas\nlinhas", "z")).inOrder()
    }

    @Test
    fun `separador do csv - ponto e virgula do Excel ou virgula`() {
        assertThat(separadorCsv("nome;valor\nA;1,5\nB;2,25")).isEqualTo(';')
        assertThat(separadorCsv("nome,valor\nA,1\nB,2")).isEqualTo(',')
    }

    @Test
    fun `cabecalho, linhas curtas completadas e numeros a direita`() {
        val tabela = montarTabela("item,qtd\nA,1\nB", "csv")!!
        assertThat(tabela.cabecalho).containsExactly("item", "qtd").inOrder()
        assertThat(tabela.corpo).containsExactly(listOf("A", "1"), listOf("B", "")).inOrder()
        assertThat(tabela.aDireita).containsExactly(false, true).inOrder()
        // TSV com aspas, barra vertical e quebra de linha na célula: o texto fica como é.
        val comAspas = montarTabela("a\tb\n\"x|y\nz\"\t<b>", "tsv")!!
        assertThat(comAspas.corpo.single()).containsExactly("x|y\nz", "<b>").inOrder()
        assertThat(montarTabela(exemplo, "TSV")!!.corpo).hasSize(2)
        assertThat(montarTabela("", "csv")).isNull()
    }

    @Test
    fun `numeros, reais e porcentagem ficam a direita`() {
        listOf("10", "-3,5", "+1.234,56", "R$ 10,00", "R$10", "45%", "1 234").forEach { assertThat(ehNumeroDeTabela(it)).isTrue() }
        listOf("abc", "10 reais", "R$", "", "1a").forEach { assertThat(ehNumeroDeTabela(it)).isFalse() }
    }

    @Test
    fun `texto com tabs copiado do Excel vira tsv, e csv com ponto e virgula ou virgula`() {
        assertThat(pareceTabela(exemplo + "\n")).isEqualTo(TabelaColada("tsv", exemplo))
        assertThat(pareceTabela("nome;idade\nAna;30")?.linguagem).isEqualTo("csv")
        assertThat(pareceTabela("nome,idade\nAna,30")?.linguagem).isEqualTo("csv")
        assertThat(pareceTabela("\n\nnome,idade\nAna,30\n  ")?.tabela).isEqualTo("nome,idade\nAna,30")
    }

    @Test
    fun `nao pega frases, uma linha so, colunas diferentes nem codigo`() {
        assertThat(pareceTabela("Oi, tudo bem?\nSim, e você?")).isNull()
        assertThat(pareceTabela("a\tb")).isNull()
        assertThat(pareceTabela("a\tb\nc")).isNull()
        assertThat(pareceTabela("const a = f(x,y);\nconst b = g(z,w);")).isNull()
        assertThat(pareceTabela("linha um\nlinha dois")).isNull()
    }

    @Test
    fun `linguagem de tabela`() {
        assertThat(ehLinguagemTabela("CSV")).isTrue()
        assertThat(ehLinguagemTabela("tsv")).isTrue()
        assertThat(ehLinguagemTabela("md")).isFalse()
        assertThat(ehLinguagemTabela(null)).isFalse()
    }

    @Test
    fun `parece codigo - codigo, terminal e arvore, mas nao texto comum`() {
        assertThat(pareceCodigo("function soma(a, b) {\n  return a + b;\n}")).isTrue()
        assertThat(pareceCodigo("cd projeto\nnpm install")).isTrue()
        assertThat(pareceCodigo("src\n├── main.ts\n└── App.vue")).isTrue()
        assertThat(pareceCodigo("Oi, tudo bem?\nAmanhã a gente conversa.")).isFalse()
        assertThat(pareceCodigo("const a = 1;")).isFalse()
        assertThat(pareceCodigo("```\nconst a = 1;\n```")).isFalse()
    }
}
