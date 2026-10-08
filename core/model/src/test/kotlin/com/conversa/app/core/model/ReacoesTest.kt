package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ReacoesTest {
    private val agora = Instant.parse("2026-10-08T12:00:00Z")
    private val bruno = UsuarioReacao(8, "Bruno", agora, null)

    @Test
    fun `limite de 5 emojis meus - tirar sempre pode, o sexto nao`() {
        val cinco = listOf("👍", "❤️", "😂", "😮", "😢").map { Reacao(it, 1, true, emptyList()) }
        val deOutro = Reacao("🔥", 1, false, listOf(bruno))

        assertThat(podeReagir(cinco + deOutro, "🔥")).isFalse()
        assertThat(podeReagir(cinco, "👍")).isTrue()
        assertThat(podeReagir(cinco.drop(1) + deOutro, "🔥")).isTrue()
    }

    @Test
    fun `reagir com emoji novo cria a reacao com 1 e marcada`() {
        val lista = alternarReacao(emptyList(), "👍", 7, "Ana", agora)

        assertThat(lista).containsExactly(Reacao("👍", 1, true, listOf(UsuarioReacao(7, "Ana", agora, null))))
    }

    @Test
    fun `reagir num emoji de outro soma e marca`() {
        val lista = alternarReacao(listOf(Reacao("❤️", 1, false, listOf(bruno))), "❤️", 7, "Ana", agora)

        assertThat(lista.single().quantidade).isEqualTo(2)
        assertThat(lista.single().reagiu).isTrue()
        assertThat(lista.single().usuarios.map { it.usuarioId }).containsExactly(8L, 7L)
    }

    @Test
    fun `tirar a minha reacao subtrai e some quando chega a zero`() {
        val minhaSo = Reacao("😂", 1, true, listOf(UsuarioReacao(7, "Ana", agora, null)))
        val comBruno = Reacao("👍", 2, true, listOf(bruno, UsuarioReacao(7, "Ana", agora, null)))

        val lista = alternarReacao(listOf(minhaSo, comBruno), "😂", 7, "Ana", agora)
        assertThat(lista).containsExactly(comBruno)

        val depois = alternarReacao(lista, "👍", 7, "Ana", agora).single()
        assertThat(depois.quantidade).isEqualTo(1)
        assertThat(depois.reagiu).isFalse()
        assertThat(depois.usuarios).containsExactly(bruno)
    }

    @Test
    fun `emoji aceito ate 10 code points`() {
        assertThat(emojiAceito("👍")).isTrue()
        // Família com modificadores: 7 code points.
        assertThat(emojiAceito("👨‍👩‍👧‍👦")).isTrue()
        assertThat(emojiAceito("")).isFalse()
        assertThat(emojiAceito("👍".repeat(11))).isFalse()
    }
}
