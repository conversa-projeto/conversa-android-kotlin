package com.conversa.app.core.model

// Seletor de emojis (ENV-03, ENV-17, FC-418), como o do web (`useEmojis.ts`): o mesmo catálogo
// (`emojis/pt.json`, sem tons de pele nem bandeiras), a mesma pesquisa e os mesmos recentes.

/** Um emoji do catálogo; [busca] é o nome e as palavras-chave sem acento, cada palavra depois de um espaço. */
data class EmojiDoCatalogo(val emoji: String, val nome: String, val busca: String)

data class CategoriaEmojis(val id: String, val nome: String, val icone: String, val emojis: List<EmojiDoCatalogo>)

/** Quantos ficam em "Recentes" (como o web). */
const val MAXIMO_EMOJIS_RECENTES = 32

/** Quantos resultados a pesquisa mostra (como o web). */
const val MAXIMO_RESULTADOS_EMOJI = 200

/** Sem nada usado ainda, os recentes começam pelos mais comuns (a lista do web). */
val EMOJIS_POPULARES = listOf("😂", "❤️", "😍", "🤣", "😊", "🙏", "😭", "😘", "👍", "😅", "🔥", "🥰", "😎", "💕", "🎉", "✨")

/** As palavras de um texto, sem acento nem maiúsculas (sem regex: o ICU do Android não é o da JVM). */
fun palavrasDaBusca(texto: String): List<String> {
    val limpo = normalizarBusca(texto)
    val palavras = mutableListOf<String>()
    val atual = StringBuilder()
    for (c in limpo) {
        if (Character.isLetterOrDigit(c)) {
            atual.append(c)
        } else if (atual.isNotEmpty()) {
            palavras += atual.toString()
            atual.clear()
        }
    }
    if (atual.isNotEmpty()) palavras += atual.toString()
    return palavras
}

/** O texto de busca de um emoji: " palavra1 palavra2 …" (cada palavra começa depois de um espaço). */
fun textoDeBuscaDoEmoji(nome: String, palavrasChave: String): String =
    palavrasDaBusca("$nome $palavrasChave").joinToString(separator = " ", prefix = " ")

/** Cada palavra digitada precisa começar alguma palavra do nome ou das palavras-chave. */
fun pesquisarEmojis(categorias: List<CategoriaEmojis>, termo: String, maximo: Int = MAXIMO_RESULTADOS_EMOJI): List<EmojiDoCatalogo> {
    val palavras = palavrasDaBusca(termo)
    if (palavras.isEmpty()) return emptyList()
    val achados = mutableListOf<EmojiDoCatalogo>()
    for (categoria in categorias) {
        for (item in categoria.emojis) {
            if (palavras.all { item.busca.contains(" $it") }) {
                achados += item
                if (achados.size >= maximo) return achados
            }
        }
    }
    return achados
}

/** Usou [emoji]: vai para o começo dos recentes, sem repetir, até [MAXIMO_EMOJIS_RECENTES]. */
fun usarEmojiRecente(recentes: List<String>, emoji: String): List<String> =
    (listOf(emoji) + recentes.filter { it != emoji }).take(MAXIMO_EMOJIS_RECENTES)
