package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pastas de mensagens (contrato §10.17): cor, ordem e marcas, como o web. */
class PastasTest {
    private fun pasta(id: Long, nome: String, cor: String = "") = Pasta(id, 5, 1, nome, cor, false, 0)

    @Test
    fun `cor so em rrggbb - o resto usa a cor do tema`() {
        assertThat(argbDaPasta("#f59e0b")).isEqualTo(0xFFF59E0BL)
        assertThat(argbDaPasta("#3B82F6")).isEqualTo(0xFF3B82F6L)
        assertThat(argbDaPasta("")).isNull()
        assertThat(argbDaPasta("red")).isNull()
        assertThat(argbDaPasta("#fff")).isNull()
        assertThat(argbDaPasta("#gggggg")).isNull()
        assertThat(CORES_PASTA.drop(1).all { argbDaPasta(it) != null }).isTrue()
    }

    @Test
    fun `ordena pelo nome sem acento nem maiusculas e depois pelo id`() {
        val ordem = ordenarPastas(listOf(pasta(3, "contratos"), pasta(1, "Ácaros"), pasta(2, "Contratos"), pasta(4, "banco")))
        assertThat(ordem.map { it.id }).containsExactly(1L, 4L, 2L, 3L).inOrder()
    }

    @Test
    fun `marcas da bolha - so as pastas visiveis, em ordem, ate tres`() {
        val pastas = listOf(pasta(1, "a"), pasta(2, "b"), pasta(3, "c"), pasta(4, "d"))
        assertThat(pastasDaMensagem(listOf(4, 9, 2, 1, 3), pastas).map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(pastasDaMensagem(listOf(9), pastas)).isEmpty()
    }

    @Test
    fun `por e tirar da pasta`() {
        assertThat(pastasDepois(listOf(3), 1, incluir = true)).containsExactly(1L, 3L).inOrder()
        assertThat(pastasDepois(listOf(1, 3), 1, incluir = true)).containsExactly(1L, 3L).inOrder()
        assertThat(pastasDepois(listOf(1, 3), 1, incluir = false)).containsExactly(3L)
        assertThat(pastasDepois(emptyList(), 1, incluir = false)).isEmpty()
    }
}
