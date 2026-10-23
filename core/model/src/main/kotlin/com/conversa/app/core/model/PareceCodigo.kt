package com.conversa.app.core.model

// Texto colado que parece código (web `utils/codeBlocks.ts`, `pareceCodigo`). Os padrões evitam
// `\w` e flags embutidas: o Android usa o regex do ICU (CLAUDE.md).

/** Desenho ASCII: caixas e árvores (├──) ou linhas cercadas por + e |. */
private val DESENHO_ASCII = Regex("""[─│┌┐└┘├┤┬┴┼═║╔╗╚╝]|^\s*[+|].*[+|]\s*$""")

/** Linha com cara de código: termina em ; { } ), tem =>, começa com palavra reservada comum, tag HTML ou indentação. */
private val LINHA_CODIGO = Regex(
    """[;{}]\s*$|\)\s*$|=>|^\s*[}\])]|^\s*(import|export|from|const|let|var|function|return|class|def|public|private|protected|static|if|else|elif|for|foreach|while|switch|case|try|catch|using|namespace|package|#include|select|insert|update|delete|create|alter|begin|end|procedure|with)\b|^\s*</?[a-zA-Z][A-Za-z0-9_-]*|^( {2,}|\t)""",
    RegexOption.IGNORE_CASE,
)

/** Sinal que texto comum quase não tem, para uma lista indentada não virar código. */
private val SINAL_FORTE = Regex("""[{};]|=>|[A-Za-z0-9_]\([^)]*\)|</[a-zA-Z][A-Za-z0-9_-]*>""")

/** O mesmo sinal no começo de uma linha (o web usa o modo de várias linhas). */
private val SINAL_FORTE_NA_LINHA = Regex("""^\s*(def|function|class|import|select|from)\b""", RegexOption.IGNORE_CASE)

/** Comando de terminal no começo da linha (cd, npm, docker, git...). */
private val COMANDO_TERMINAL = Regex("""^\s*(\$\s+)?(cd|ls|npm|npx|node|docker|git|sudo|apt|curl|chmod|mkdir|rm|cp|mv|psql)\s""")

/**
 * Duas ou mais linhas, metade delas com cara de código e algum sinal forte; ou metade desenho
 * ASCII; ou metade comandos de terminal. Já com ``` não conta (o texto já está formatado).
 */
fun pareceCodigo(texto: String): Boolean {
    if (texto.lines().any { it.startsWith("```") }) return false
    val linhas = texto.split('\n').filter { it.isNotBlank() }
    if (linhas.size < 2) return false
    val metade = linhas.size / 2.0
    if (linhas.count { DESENHO_ASCII.containsMatchIn(it) } >= metade) return true
    if (linhas.count { COMANDO_TERMINAL.containsMatchIn(it) } >= metade) return true
    val comCara = linhas.count { LINHA_CODIGO.containsMatchIn(it) || COMANDO_TERMINAL.containsMatchIn(it) }
    val sinalForte = SINAL_FORTE.containsMatchIn(texto) || linhas.any { SINAL_FORTE_NA_LINHA.containsMatchIn(it) }
    return comCara >= 2 && comCara >= metade && sinalForte
}
