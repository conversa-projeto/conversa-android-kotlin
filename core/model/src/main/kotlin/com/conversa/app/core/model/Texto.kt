package com.conversa.app.core.model

// Regras de texto das mensagens, portadas do web para dar o mesmo resultado:
// - menção: `@[Nome](id)` (conversa-web `src/utils/formatters.ts`);
// - bloco de código: cerca de 3+ crases com linguagem opcional
//   (conversa-web `src/utils/codeBlocks.ts`).

/** Pedaço de uma mensagem: texto comum ou bloco de código. */
sealed interface SegmentoCodigo {
    data class Texto(val conteudo: String) : SegmentoCodigo

    data class Codigo(val conteudo: String, val linguagem: String?) : SegmentoCodigo
}

private val REGEX_MENCAO = Regex("""@\[([^\]]+)\]\((\d+)\)""")

// Abertura: 3 ou mais crases e a linguagem, seguidas de quebra de linha.
private val ABERTURA = Regex("(`{3,})(\\w*)\\n")
private val LINHA_SO_CRASES = Regex("(`{3,})\\s*")
private val LINHA_CRASES_LINGUAGEM = Regex("(`{3,})(\\w*)\\s*")
private val MARKDOWN = Regex("md|markdown", RegexOption.IGNORE_CASE)
private val ESPACOS = Regex("(?U)\\s+")

private data class Fechamento(val conteudoFim: Int, val fim: Int)

private data class Bloco(val inicio: Int, val fim: Int, val linguagem: String, val conteudo: String)

/**
 * Procura o fechamento do bloco aberto pela [cerca], com o código começando em [posicao].
 * Fecha numa linha só com crases (pelo menos tantas quanto a abertura) ou em crases
 * no fim da mensagem. Uma linha como ```` ```kotlin ```` dentro do bloco abre um bloco
 * interno, e a próxima linha de crases fecha esse interno. Em Markdown (`md`), o bloco
 * fecha na **última** linha de crases. Mesma lógica de `fecharBloco` no web.
 */
private fun fecharBloco(texto: String, posicao: Int, cerca: String, linguagem: String): Fechamento? {
    if (MARKDOWN.matches(linguagem)) {
        var ultimo: Fechamento? = null
        var inicioLinha = posicao
        while (inicioLinha <= texto.length) {
            val quebra = texto.indexOf('\n', inicioLinha)
            val fimLinha = if (quebra < 0) texto.length else quebra
            val crases = LINHA_SO_CRASES.matchEntire(texto.substring(inicioLinha, fimLinha))?.groupValues?.get(1)
            if (crases != null && crases.length >= cerca.length) {
                ultimo = Fechamento(maxOf(posicao, inicioLinha - 1), fimLinha)
            }
            if (quebra < 0) break
            inicioLinha = quebra + 1
        }
        if (ultimo != null) return ultimo
    }

    var profundidade = 0
    var inicioLinha = posicao
    while (inicioLinha <= texto.length) {
        val quebra = texto.indexOf('\n', inicioLinha)
        val fimLinha = if (quebra < 0) texto.length else quebra
        val achado = LINHA_CRASES_LINGUAGEM.matchEntire(texto.substring(inicioLinha, fimLinha))
        if (achado != null) {
            val (crases, linguagemInterna) = achado.destructured
            if (crases.length >= cerca.length) {
                when {
                    linguagemInterna.isNotEmpty() -> profundidade++
                    profundidade > 0 -> profundidade--
                    else -> return Fechamento(maxOf(posicao, inicioLinha - 1), fimLinha)
                }
            }
        }
        if (quebra < 0) break
        inicioLinha = quebra + 1
    }
    // Sem linha de fechamento: aceita as crases coladas no fim da mensagem.
    val final = Regex("$cerca\\s*\\z").find(texto.substring(posicao))
    if (final != null && final.range.first > 0) return Fechamento(posicao + final.range.first, texto.length)
    return null
}

private fun encontrarBlocos(texto: String): List<Bloco> {
    val blocos = mutableListOf<Bloco>()
    var busca = 0
    while (busca <= texto.length) {
        val abertura = ABERTURA.find(texto, busca) ?: break
        val (cerca, linguagem) = abertura.destructured
        val inicioCodigo = abertura.range.last + 1
        val fechamento = fecharBloco(texto, inicioCodigo, cerca, linguagem)
        if (fechamento == null) {
            busca = abertura.range.last + 1
            continue
        }
        blocos += Bloco(abertura.range.first, fechamento.fim, linguagem, texto.substring(inicioCodigo, fechamento.conteudoFim))
        busca = fechamento.fim
    }
    return blocos
}

/** Separa o texto em pedaços de texto comum e blocos de código. */
fun separarBlocosDeCodigo(texto: String): List<SegmentoCodigo> {
    val segmentos = mutableListOf<SegmentoCodigo>()
    var ultimo = 0
    for (bloco in encontrarBlocos(texto)) {
        if (bloco.inicio > ultimo) segmentos += SegmentoCodigo.Texto(texto.substring(ultimo, bloco.inicio))
        segmentos += SegmentoCodigo.Codigo(bloco.conteudo, bloco.linguagem.ifEmpty { null })
        ultimo = bloco.fim
    }
    if (ultimo < texto.length) segmentos += SegmentoCodigo.Texto(texto.substring(ultimo))
    return segmentos
}

/** O texto tem pelo menos um bloco de código formatado. */
fun temBlocoDeCodigo(texto: String): Boolean = encontrarBlocos(texto).isNotEmpty()

/** Uma linha para listas: cada bloco de código vira "Código (linguagem)". */
fun resumirCodigo(texto: String): String = separarBlocosDeCodigo(texto)
    .joinToString("") { segmento ->
        when (segmento) {
            is SegmentoCodigo.Texto -> segmento.conteudo
            is SegmentoCodigo.Codigo -> " Código${segmento.linguagem?.let { " ($it)" }.orEmpty()} "
        }
    }
    .replace(ESPACOS, " ")
    .trim()

/**
 * Texto de uma linha para prévias (lista de conversas, resposta, notificação):
 * menção vira `@Nome` e cada bloco de código vira "Código (linguagem)".
 */
fun resumirTexto(texto: String): String = resumirCodigo(texto.replace(REGEX_MENCAO, "@$1"))
