package com.conversa.app.core.model

// --- Atalhos de emoji (7.11, FC-515): porte do `emojiAtalhos.ts`, sem regex ---

/** Mesma tabela do web. Só valem como palavra solta e fora de código. */
private val ATALHOS = mapOf(
    ":)" to "🙂",
    ":-)" to "🙂",
    ":D" to "😃",
    ":-D" to "😃",
    "xD" to "😆",
    "XD" to "😆",
    ";)" to "😉",
    ";-)" to "😉",
    ":(" to "🙁",
    ":-(" to "🙁",
    ":'(" to "😢",
    ":P" to "😛",
    ":p" to "😛",
    ":O" to "😮",
    ":o" to "😮",
    ":|" to "😐",
    ":/" to "😕",
    ":*" to "😘",
    "<3" to "❤️",
    "\\o/" to "🙌",
)

/** O mais longo primeiro: ":-)" vence ":)". */
private val ATALHOS_POR_TAMANHO = ATALHOS.keys.sortedByDescending { it.length }

data class TrocaAtalho(val texto: String, val cursor: Int)

/** Atalho que termina em [fim] (exclusivo), precedido do início ou de espaço; nulo se não há. */
private fun atalhoAntes(texto: String, fim: Int): String? = ATALHOS_POR_TAMANHO.firstOrNull { atalho ->
    val inicio = fim - atalho.length
    inicio >= 0 && texto.startsWith(atalho, inicio) && (inicio == 0 || texto[inicio - 1].isWhitespace())
}

/** Dentro de bloco (```) ou trecho (`) de código o texto fica como está. */
private fun dentroDeCodigo(antes: String): Boolean {
    val partes = antes.split("```")
    if ((partes.size - 1) % 2 == 1) return true
    val semBlocos = partes.filterIndexed { i, _ -> i % 2 == 0 }.joinToString("")
    return semBlocos.count { it == '`' } % 2 == 1
}

/** Troca o atalho fechado por um espaço (ou quebra) logo antes do [cursor]; nulo se não há o que trocar. */
fun substituirAtalhoAntesDoCursor(texto: String, cursor: Int): TrocaAtalho? {
    val antes = texto.substring(0, cursor)
    if (antes.isEmpty() || !antes.last().isWhitespace()) return null
    val atalho = atalhoAntes(antes, antes.length - 1) ?: return null
    if (dentroDeCodigo(antes)) return null
    val novoAntes = antes.substring(0, antes.length - 1 - atalho.length) + ATALHOS.getValue(atalho) + antes.last()
    return TrocaAtalho(novoAntes + texto.substring(cursor), novoAntes.length)
}

/** No envio: troca o atalho que ficou no fim, sem espaço depois. */
fun substituirAtalhoNoFim(texto: String): String {
    val atalho = atalhoAntes(texto, texto.length) ?: return texto
    if (dentroDeCodigo(texto)) return texto
    return texto.substring(0, texto.length - atalho.length) + ATALHOS.getValue(atalho)
}

// --- Código colado e "Inserir código" (7.11) ---

/** Linguagens do "Inserir código" (as do `CodigoModal.vue`). */
val LINGUAGENS_CODIGO = listOf(
    "texto", "javascript", "typescript", "python", "sql", "json", "html", "css", "bash", "csharp", "pascal", "markdown", "mermaid",
)

/** Mais de 10 linhas: colar sugere "Inserir código" (`textoLongo` do web). */
fun textoLongo(texto: String): Boolean = texto.trim().split('\n').size > 10

/** Cerca maior que qualquer sequência de crases do código (um Markdown com exemplos não fecha antes da hora). */
fun cercaCodigo(codigo: String): String {
    var maior = 0
    var atual = 0
    for (c in codigo) {
        atual = if (c == '`') atual + 1 else 0
        maior = maxOf(maior, atual)
    }
    return "`".repeat(maxOf(3, maior + 1))
}

/** O bloco que vai na mensagem: cerca + linguagem, o código e a cerca. */
fun blocoDeCodigo(codigo: String, linguagem: String): String {
    val cerca = cercaCodigo(codigo)
    return "$cerca$linguagem\n$codigo\n$cerca"
}
