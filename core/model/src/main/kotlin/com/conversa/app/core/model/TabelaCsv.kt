package com.conversa.app.core.model

// Tabelas CSV e TSV nas mensagens (MSG-26; web `utils/tabelaCsv.ts`): o bloco ```csv / ```tsv
// guarda os dados originais (é o que o "Copiar" copia) e aparece como tabela. O app desenha a
// grade direto, em vez de passar por Markdown como o web.

fun ehLinguagemTabela(linguagem: String?): Boolean = linguagem?.lowercase().let { it == "csv" || it == "tsv" }

/**
 * Linhas e células, respeitando as aspas do CSV: `"a;b"`, `"com ""aspas"""` e quebra de linha
 * dentro de aspas (o Excel copia assim a célula com Enter). Linhas vazias ficam de fora.
 */
fun lerTabela(texto: String, separador: Char): List<List<String>> {
    val linhas = mutableListOf<List<String>>()
    var linha = mutableListOf<String>()
    val celula = StringBuilder()
    var aspas = false
    var inicioCelula = true
    fun fecharLinha() {
        linha += celula.toString()
        if (linha.any { it.isNotBlank() }) linhas += linha
        linha = mutableListOf()
        celula.clear()
        inicioCelula = true
    }
    var i = 0
    while (i < texto.length) {
        val c = texto[i]
        if (aspas) {
            when {
                c == '"' && texto.getOrNull(i + 1) == '"' -> {
                    celula.append('"')
                    i++
                }
                c == '"' -> aspas = false
                else -> celula.append(c)
            }
        } else if (c == '"' && inicioCelula) {
            aspas = true
            inicioCelula = false
        } else if (c == separador) {
            linha += celula.toString()
            celula.clear()
            inicioCelula = true
        } else if (c == '\n' || c == '\r') {
            if (c == '\r' && texto.getOrNull(i + 1) == '\n') i++
            fecharLinha()
        } else {
            celula.append(c)
            inicioCelula = false
        }
        i++
    }
    if (celula.isNotEmpty() || linha.isNotEmpty()) fecharLinha()
    return linhas
}

/** Todas as linhas com o mesmo número de colunas, no mínimo duas. */
private fun colunasIguais(linhas: List<List<String>>): Boolean {
    val colunas = linhas.firstOrNull()?.size ?: 0
    return colunas >= 2 && linhas.all { it.size == colunas }
}

/**
 * O separador do CSV: o `;` (Excel em português) ou a vírgula, o que der as mesmas colunas em
 * todas as linhas; empate fica com o que der mais colunas na primeira linha (e, aí, o `;`).
 */
fun separadorCsv(texto: String): Char {
    val pontoEVirgula = lerTabela(texto, ';')
    val virgula = lerTabela(texto, ',')
    val okPv = colunasIguais(pontoEVirgula)
    val okV = colunasIguais(virgula)
    if (okPv != okV) return if (okPv) ';' else ','
    return if ((pontoEVirgula.firstOrNull()?.size ?: 0) >= (virgula.firstOrNull()?.size ?: 0)) ';' else ','
}

private fun separadorDa(linguagem: String, texto: String): Char = if (linguagem.equals(
        "tsv",
        ignoreCase = true,
    )
) {
    '\t'
} else {
    separadorCsv(texto)
}

/** Número, valor em reais ou porcentagem: a coluna só com eles fica à direita (o espaço não separável do Excel também vale). */
private val NUMERO = Regex("""[-+]?(R\$[ \t\u00A0\u202F]*)?[0-9][0-9., \t\u00A0\u202F]*%?""")

fun ehNumeroDeTabela(valor: String): Boolean = NUMERO.matches(valor)

/** A tabela pronta para desenhar: o cabeçalho, o corpo (linhas curtas completadas) e as colunas à direita. */
data class TabelaDeDados(val cabecalho: List<String>, val corpo: List<List<String>>, val aDireita: List<Boolean>)

/**
 * A primeira linha é o cabeçalho; as células perdem os espaços das pontas; coluna em que todo
 * valor não vazio do corpo é número fica à direita (o cabeçalho, não). Sem nada, nula.
 */
fun montarTabela(texto: String, linguagem: String): TabelaDeDados? {
    val linhas = lerTabela(texto, separadorDa(linguagem, texto))
    val colunas = linhas.maxOfOrNull { it.size } ?: 0
    if (colunas == 0) return null
    val completas = linhas.map { linha -> (linha + List(colunas - linha.size) { "" }).map { it.trim() } }
    val corpo = completas.drop(1)
    val aDireita = (0 until colunas).map { i ->
        val valores = corpo.map { it[i] }.filter { it.isNotEmpty() }
        valores.isNotEmpty() && valores.all(::ehNumeroDeTabela)
    }
    return TabelaDeDados(completas.first(), corpo, aDireita)
}

/** Texto colado que é uma tabela: a linguagem (`csv` ou `tsv`) e o texto sem as linhas vazias das pontas. */
data class TabelaColada(val linguagem: String, val tabela: String)

/**
 * Duas linhas ou mais, todas com o mesmo número de colunas (no mínimo duas). Tab é o que vem do
 * Excel, do Sheets e de tabelas de páginas. Com `;` ou vírgula, nenhuma célula pode começar com
 * espaço (para "Oi, tudo bem?" não virar tabela), nem pode parecer código.
 */
fun pareceTabela(texto: String): TabelaColada? {
    val tabela = texto.lines().dropWhile { it.isBlank() }.joinToString("\n").trimEnd()
    if ('\n' !in tabela) return null
    if ('\t' in tabela) {
        val linhas = lerTabela(tabela, '\t')
        return if (linhas.size >= 2 && colunasIguais(linhas)) TabelaColada("tsv", tabela) else null
    }
    if (tabela.lines().any { it.startsWith("```") } || pareceCodigo(tabela)) return null
    val linhas = lerTabela(tabela, separadorCsv(tabela))
    if (linhas.size < 2 || !colunasIguais(linhas)) return null
    if (linhas.any { linha -> linha.any { it.firstOrNull()?.isWhitespace() == true } }) return null
    return TabelaColada("csv", tabela)
}
