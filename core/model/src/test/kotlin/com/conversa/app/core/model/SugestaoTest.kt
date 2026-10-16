package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Sugestão de texto da IA (ENV-25), com as regras do `useSugestaoIa.ts` do web. */
class SugestaoTest {
    @Test
    fun `so com o cursor no fim, sem selecao e 3 caracteres`() {
        assertThat(podePedirSugestao("Ok, vamos", 9, 9)).isTrue()
        // Depois do cursor, só espaço: ainda é o fim.
        assertThat(podePedirSugestao("Ok, vamos  ", 9, 9)).isTrue()
        assertThat(podePedirSugestao("Ok, vamos", 3, 3)).isFalse()
        assertThat(podePedirSugestao("Ok, vamos", 0, 9)).isFalse()
        assertThat(podePedirSugestao("  oi  ", 6, 6)).isFalse()
        assertThat(podePedirSugestao("", 0, 0)).isFalse()
    }

    @Test
    fun `vai o texto ate o cursor, no limite do servidor`() {
        assertThat(textoParaSugestao("Ok, vamos aguardar", 9)).isEqualTo("Ok, vamos")
        val longo = "a".repeat(5_000) + "fim"
        assertThat(textoParaSugestao(longo, longo.length)).hasLength(MAXIMO_TEXTO_SUGESTAO)
        assertThat(textoParaSugestao(longo, longo.length)).endsWith("fim")
    }
}
