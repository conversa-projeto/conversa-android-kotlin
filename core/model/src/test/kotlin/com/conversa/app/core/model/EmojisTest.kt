package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pesquisa e recentes do seletor de emojis (FC-418), as mesmas regras do `useEmojis.ts` do web. */
class EmojisTest {
    private fun emoji(emoji: String, nome: String, palavras: String) = EmojiDoCatalogo(emoji, nome, textoDeBuscaDoEmoji(nome, palavras))

    private val categorias = listOf(
        CategoriaEmojis(
            "carinhas",
            "Carinhas e emoções",
            "😀",
            listOf(
                emoji("😀", "Rosto risonho", "engraçado feliz lol rindo"),
                emoji("😢", "Rosto chorando", "lágrima triste"),
            ),
        ),
        CategoriaEmojis("simbolos", "Símbolos", "❤️", listOf(emoji("❤️", "Coração vermelho", "amor"))),
    )

    @Test
    fun `palavras da busca sem acento, maiusculas nem pontuacao`() {
        assertThat(palavrasDaBusca("  Coração, VERMELHO!  ")).containsExactly("coracao", "vermelho").inOrder()
        assertThat(palavrasDaBusca("—")).isEmpty()
        assertThat(textoDeBuscaDoEmoji("Rosto risonho", "lol")).isEqualTo(" rosto risonho lol")
    }

    @Test
    fun `cada palavra digitada precisa comecar uma palavra do nome ou das palavras-chave`() {
        assertThat(pesquisarEmojis(categorias, "ros").map { it.emoji }).containsExactly("😀", "😢").inOrder()
        assertThat(pesquisarEmojis(categorias, "rosto tri").map { it.emoji }).containsExactly("😢")
        // Sem acento e no meio da palavra não vale.
        assertThat(pesquisarEmojis(categorias, "lagrima").map { it.emoji }).containsExactly("😢")
        assertThat(pesquisarEmojis(categorias, "isonho")).isEmpty()
        assertThat(pesquisarEmojis(categorias, "coracao").map { it.emoji }).containsExactly("❤️")
        assertThat(pesquisarEmojis(categorias, "  ")).isEmpty()
        assertThat(pesquisarEmojis(categorias, "ros", maximo = 1)).hasSize(1)
    }

    @Test
    fun `recentes - o usado vai para o comeco, sem repetir, ate 32`() {
        assertThat(usarEmojiRecente(listOf("😀", "👍", "🎉"), "🎉")).containsExactly("🎉", "😀", "👍").inOrder()
        val cheios = (1..MAXIMO_EMOJIS_RECENTES).map { "e$it" }
        val depois = usarEmojiRecente(cheios, "novo")
        assertThat(depois).hasSize(MAXIMO_EMOJIS_RECENTES)
        assertThat(depois.first()).isEqualTo("novo")
        assertThat(depois).doesNotContain("e$MAXIMO_EMOJIS_RECENTES")
    }
}
