package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Menções no campo (portadas do `mencoesTexto.ts` do web). */
class MencoesTest {
    private val ana = MencaoInserida("Ana", 7)
    private val anaPaula = MencaoInserida("Ana Paula", 9)

    @Test
    fun `envio troca cada arroba Nome inserido pelo formato do servidor`() {
        assertThat(textoParaEnvio("oi @Ana, tudo bem?", listOf(ana))).isEqualTo("oi @[Ana](7), tudo bem?")
        assertThat(textoParaEnvio("sem menção", emptyList())).isEqualTo("sem menção")
    }

    @Test
    fun `arroba Ana nao casa com arroba Anabela nem com Ana_1`() {
        assertThat(textoParaEnvio("@Anabela e @Ana_1 e @Ana", listOf(ana))).isEqualTo("@Anabela e @Ana_1 e @[Ana](7)")
        assertThat(textoParaEnvio("@Anaé", listOf(ana))).isEqualTo("@Anaé")
    }

    @Test
    fun `o nome maior e tentado antes`() {
        assertThat(textoParaEnvio("@Ana Paula e @Ana", listOf(ana, anaPaula))).isEqualTo("@[Ana Paula](9) e @[Ana](7)")
    }

    @Test
    fun `dividir marca os trechos das mencoes`() {
        val trechos = dividirMencoes("a @Ana b", listOf(ana))

        assertThat(trechos).containsExactly(TrechoCampo("a "), TrechoCampo("@Ana", ana), TrechoCampo(" b")).inOrder()
    }

    @Test
    fun `texto cru volta a mostrar arroba Nome e devolve as mencoes`() {
        val (texto, mencoes) = extrairMencoesCruas("oi @[Ana Paula](9) e @[Bruno](8)! @[sem id]() @x")

        assertThat(texto).isEqualTo("oi @Ana Paula e @Bruno! @[sem id]() @x")
        assertThat(mencoes).containsExactly(anaPaula, MencaoInserida("Bruno", 8)).inOrder()
    }

    @Test
    fun `mencao digitada antes do cursor, como o regex do web`() {
        assertThat(mencaoDigitada("oi @an", 6)).isEqualTo(MencaoDigitada("an", 3))
        assertThat(mencaoDigitada("oi @", 4)).isEqualTo(MencaoDigitada("", 3))
        assertThat(mencaoDigitada("@ana paula", 10)).isEqualTo(MencaoDigitada("ana paula", 0))
        assertThat(mencaoDigitada("a@b @c", 6)).isEqualTo(MencaoDigitada("c", 4))
        assertThat(mencaoDigitada("e-mail: x@y.com", 15)).isNull()
        assertThat(mencaoDigitada("@joã", 4)).isNull()
        assertThat(mencaoDigitada("sem", 3)).isNull()
    }

    @Test
    fun `sugestoes por nome ou login, no maximo 6`() {
        val contatos = (1L..8L).map { Contato(it, "Pessoa $it", "login$it", null, null) } + Contato(20, "Bruno", "bmartins", null, null)

        assertThat(sugestoesDeMencao(contatos, "")).hasSize(6)
        assertThat(sugestoesDeMencao(contatos, "MART").map { it.id }).containsExactly(20L)
        assertThat(sugestoesDeMencao(contatos, "bru").map { it.id }).containsExactly(20L)
    }
}
