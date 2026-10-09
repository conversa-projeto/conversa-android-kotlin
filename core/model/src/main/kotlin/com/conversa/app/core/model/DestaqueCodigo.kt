package com.conversa.app.core.model

/*
 * Destaque de sintaxe dos blocos de código (MSG-15, TODO 7.9): um tokenizador leve, sem
 * biblioteca e sem regex (o regex do Android é o do ICU), para as linguagens que o web
 * registra no highlight.js: js/ts, python, sql, json, xml/html, css, bash/sh, csharp/cs e
 * pascal/delphi. Outras (texto, mermaid, markdown...) ficam sem destaque.
 */

enum class TipoToken { PALAVRA_CHAVE, TEXTO, COMENTARIO, NUMERO, TAG, ATRIBUTO }

/** Trecho destacado: [inicio] inclusivo, [fim] exclusivo. */
data class TokenCodigo(val inicio: Int, val fim: Int, val tipo: TipoToken)

private class Linguagem(
    val palavras: Set<String>,
    val linha: List<String> = emptyList(),
    val blocos: List<Pair<String, String>> = emptyList(),
    val aspas: Set<Char> = setOf('"', '\''),
    val semCaixa: Boolean = false,
)

private val PALAVRAS_JS = setOf(
    "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export",
    "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new", "null", "return",
    "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield",
    "async", "await", "of", "static", "get", "set", "from", "as",
)
private val PALAVRAS_TS = PALAVRAS_JS + setOf(
    "interface", "type", "enum", "implements", "private", "public", "protected", "readonly", "declare", "namespace",
    "abstract", "keyof", "any", "unknown", "never", "number", "string", "boolean", "is",
)

private val JS = Linguagem(PALAVRAS_JS, linha = listOf("//"), blocos = listOf("/*" to "*/"), aspas = setOf('"', '\'', '`'))
private val TS = Linguagem(PALAVRAS_TS, linha = listOf("//"), blocos = listOf("/*" to "*/"), aspas = setOf('"', '\'', '`'))
private val PYTHON = Linguagem(
    setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except",
        "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or",
        "pass", "raise", "return", "True", "try", "while", "with", "yield", "self", "print",
    ),
    linha = listOf("#"),
)
private val SQL = Linguagem(
    setOf(
        "select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete", "create", "table",
        "drop", "alter", "add", "join", "inner", "left", "right", "outer", "on", "as", "group", "by", "order", "having",
        "limit", "offset", "distinct", "null", "is", "in", "like", "between", "union", "all", "case", "when", "then", "else",
        "end", "primary", "key", "foreign", "references", "index", "default", "exists", "asc", "desc", "count", "returning",
        "with", "lateral", "coalesce", "true", "false",
    ),
    linha = listOf("--"),
    blocos = listOf("/*" to "*/"),
    aspas = setOf('\''),
    semCaixa = true,
)
private val JSON = Linguagem(setOf("true", "false", "null"), aspas = setOf('"'))
private val CSS = Linguagem(setOf("important", "inherit", "initial", "none", "auto"), blocos = listOf("/*" to "*/"))
private val BASH = Linguagem(
    setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until", "case", "esac", "function", "return",
        "export", "local", "echo", "exit", "cd", "set", "unset", "source", "sudo", "true", "false",
    ),
    linha = listOf("#"),
)
private val CSHARP = Linguagem(
    setOf(
        "abstract", "as", "async", "await", "base", "bool", "break", "case", "catch", "class", "const", "continue", "decimal",
        "default", "do", "double", "else", "enum", "false", "finally", "float", "for", "foreach", "if", "in", "int",
        "interface", "internal", "is", "long", "namespace", "new", "null", "object", "out", "override", "private", "protected",
        "public", "readonly", "ref", "return", "sealed", "static", "string", "struct", "switch", "this", "throw", "true",
        "try", "using", "var", "virtual", "void", "while", "get", "set",
    ),
    linha = listOf("//"),
    blocos = listOf("/*" to "*/"),
)
private val PASCAL = Linguagem(
    setOf(
        "and", "array", "as", "begin", "case", "class", "const", "constructor", "destructor", "div", "do", "downto", "else",
        "end", "except", "false", "finally", "for", "function", "if", "implementation", "in", "inherited", "interface", "is",
        "mod", "nil", "not", "of", "or", "private", "procedure", "program", "property", "protected", "public", "published",
        "raise", "record", "repeat", "result", "self", "set", "string", "then", "to", "true", "try", "type", "unit", "until",
        "uses", "var", "while", "with", "integer", "boolean",
    ),
    linha = listOf("//"),
    blocos = listOf("{" to "}", "(*" to "*)"),
    aspas = setOf('\''),
    semCaixa = true,
)

private fun linguagemDe(nome: String?): Linguagem? = when (nome?.lowercase()) {
    "javascript", "js" -> JS
    "typescript", "ts" -> TS
    "python", "py" -> PYTHON
    "sql" -> SQL
    "json" -> JSON
    "css" -> CSS
    "bash", "sh" -> BASH
    "csharp", "cs" -> CSHARP
    "pascal", "delphi" -> PASCAL
    else -> null
}

/** Linguagem com destaque (inclusive xml/html)? As outras aparecem sem cor. */
fun temDestaque(linguagem: String?): Boolean = linguagemDe(linguagem) != null || linguagem?.lowercase() in setOf("xml", "html")

/** Os trechos destacados do [codigo], em ordem e sem sobreposição. */
fun destacarCodigo(codigo: String, linguagem: String?): List<TokenCodigo> {
    if (linguagem?.lowercase() in setOf("xml", "html")) return destacarXml(codigo)
    val lingua = linguagemDe(linguagem) ?: return emptyList()
    val tokens = mutableListOf<TokenCodigo>()
    var i = 0
    while (i < codigo.length) {
        val c = codigo[i]
        val linha = lingua.linha.firstOrNull { codigo.startsWith(it, i) }
        val bloco = lingua.blocos.firstOrNull { codigo.startsWith(it.first, i) }
        when {
            linha != null -> {
                val fim = codigo.indexOf('\n', i).let { if (it < 0) codigo.length else it }
                tokens += TokenCodigo(i, fim, TipoToken.COMENTARIO)
                i = fim
            }
            bloco != null -> {
                val fecha = codigo.indexOf(bloco.second, i + bloco.first.length)
                val fim = if (fecha < 0) codigo.length else fecha + bloco.second.length
                tokens += TokenCodigo(i, fim, TipoToken.COMENTARIO)
                i = fim
            }
            c in lingua.aspas -> {
                val fim = fimDoTexto(codigo, i, c)
                tokens += TokenCodigo(i, fim, TipoToken.TEXTO)
                i = fim
            }
            c.isDigit() && (i == 0 || !parteDeNome(codigo[i - 1])) -> {
                var fim = i + 1
                while (fim < codigo.length && (codigo[fim].isLetterOrDigit() || codigo[fim] == '.' || codigo[fim] == '_')) fim++
                tokens += TokenCodigo(i, fim, TipoToken.NUMERO)
                i = fim
            }
            inicioDeNome(c) -> {
                var fim = i + 1
                while (fim < codigo.length && parteDeNome(codigo[fim])) fim++
                val palavra = codigo.substring(i, fim)
                val chave = if (lingua.semCaixa) palavra.lowercase() in lingua.palavras else palavra in lingua.palavras
                if (chave) tokens += TokenCodigo(i, fim, TipoToken.PALAVRA_CHAVE)
                i = fim
            }
            else -> i++
        }
    }
    return tokens
}

/** Fim de um texto entre aspas (inclui a aspa final); "\" escapa; sem fechar, vai até o fim da linha. */
private fun fimDoTexto(codigo: String, inicio: Int, aspa: Char): Int {
    var i = inicio + 1
    while (i < codigo.length) {
        val c = codigo[i]
        if (c == '\\') {
            i += 2
            continue
        }
        if (c == aspa) return i + 1
        if (c == '\n' && aspa != '`') return i
        i++
    }
    return codigo.length
}

private fun inicioDeNome(c: Char): Boolean = c.isLetter() || c == '_' || c == '$'

private fun parteDeNome(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

/** XML/HTML: comentários, nome das tags, atributos e os valores entre aspas. */
private fun destacarXml(codigo: String): List<TokenCodigo> {
    val tokens = mutableListOf<TokenCodigo>()
    var i = 0
    while (i < codigo.length) {
        if (codigo.startsWith("<!--", i)) {
            val fecha = codigo.indexOf("-->", i + 4)
            val fim = if (fecha < 0) codigo.length else fecha + 3
            tokens += TokenCodigo(i, fim, TipoToken.COMENTARIO)
            i = fim
            continue
        }
        if (codigo[i] != '<') {
            i++
            continue
        }
        var j = i + 1
        if (j < codigo.length && (codigo[j] == '/' || codigo[j] == '?' || codigo[j] == '!')) j++
        val inicioNome = j
        while (j < codigo.length && (parteDeNome(codigo[j]) || codigo[j] == '-' || codigo[j] == ':' || codigo[j] == '.')) j++
        if (j == inicioNome) {
            i++
            continue
        }
        tokens += TokenCodigo(inicioNome, j, TipoToken.TAG)
        // Dentro da tag, até o ">": atributos e valores.
        while (j < codigo.length && codigo[j] != '>') {
            val c = codigo[j]
            when {
                c == '"' || c == '\'' -> {
                    val fecha = codigo.indexOf(c, j + 1)
                    val fim = if (fecha < 0) codigo.length else fecha + 1
                    tokens += TokenCodigo(j, fim, TipoToken.TEXTO)
                    j = fim
                }
                inicioDeNome(c) -> {
                    var fim = j + 1
                    while (fim < codigo.length && (parteDeNome(codigo[fim]) || codigo[fim] == '-' || codigo[fim] == ':')) fim++
                    tokens += TokenCodigo(j, fim, TipoToken.ATRIBUTO)
                    j = fim
                }
                else -> j++
            }
        }
        i = j + 1
    }
    return tokens
}

/** ```md / ```markdown: o bloco aparece formatado, com "Visualizar"/"Código" (como o `useMarkdown.ts` do web). */
fun ehLinguagemMarkdown(linguagem: String?): Boolean = linguagem?.lowercase().let { it == "md" || it == "markdown" }

/** ```mermaid: o bloco aparece como diagrama, com "Visualizar"/"Código" (como o `useMermaid.ts` do web). */
fun ehLinguagemMermaid(linguagem: String?): Boolean = linguagem?.lowercase() == "mermaid"
