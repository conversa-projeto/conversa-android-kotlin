package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** Regras da votação (TODO 7.12), como o web. */
class EnquetesTest {
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-10-08T15:30:00Z") // 12:30 em São Paulo

    private fun enquete(
        multipla: Boolean = false,
        meus: List<Long> = emptyList(),
        votos: List<Int> = listOf(
            0,
            0,
        ),
        encerrada: Boolean = false,
        encerraEm: Instant? = null,
    ) =
        Enquete(
            id = 1, conversaId = 2, mensagemId = 3, pergunta = "Onde?", multipla = multipla, criadoPor = 7,
            opcoes = votos.mapIndexed { i, n -> OpcaoEnquete(i + 1L, "o${i + 1}", (1..n).map { Votante(it.toLong(), "p$it") }) },
            totalVotantes = votos.maxOrNull() ?: 0, meusVotos = meus, encerraEm = encerraEm, encerrada = encerrada,
        )

    @Test
    fun `porcentagem sobre quem votou, arredondada`() {
        assertThat(porcentagemDaOpcao(1, 3)).isEqualTo(33)
        assertThat(porcentagemDaOpcao(2, 3)).isEqualTo(67)
        assertThat(porcentagemDaOpcao(1, 2)).isEqualTo(50)
        assertThat(porcentagemDaOpcao(0, 0)).isEqualTo(0)
    }

    @Test
    fun `unica troca ou tira, multipla marca e desmarca`() {
        assertThat(enquete(meus = listOf(1)).votosAoTocar(2)).containsExactly(2L)
        assertThat(enquete(meus = listOf(1)).votosAoTocar(1)).isEmpty()
        assertThat(enquete(multipla = true, meus = listOf(1)).votosAoTocar(2)).containsExactly(1L, 2L).inOrder()
        assertThat(enquete(multipla = true, meus = listOf(1, 2)).votosAoTocar(1)).containsExactly(2L)
    }

    @Test
    fun `vencedoras so encerrada, com empate e sem ninguem votar`() {
        assertThat(enquete(votos = listOf(2, 1)).vencedoras(agora)).isEmpty()
        assertThat(enquete(votos = listOf(2, 1), encerrada = true).vencedoras(agora)).containsExactly(1L)
        assertThat(enquete(votos = listOf(2, 2), encerrada = true).vencedoras(agora)).containsExactly(1L, 2L)
        assertThat(enquete(votos = listOf(0, 0), encerrada = true).vencedoras(agora)).isEmpty()
        // Prazo vencido com a bolha aberta também encerra.
        assertThat(enquete(votos = listOf(1, 0), encerraEm = agora).vencedoras(agora)).containsExactly(1L)
    }

    @Test
    fun `data final - pelo menos 1 minuto e no maximo 1 ano`() {
        assertThat(validarPrazoEnquete(null, agora, zona)).isEqualTo(ErroPrazo.SEM_DATA)
        assertThat(validarPrazoEnquete(LocalDateTime.of(2026, 10, 8, 12, 30), agora, zona)).isEqualTo(ErroPrazo.NO_PASSADO)
        assertThat(validarPrazoEnquete(LocalDateTime.of(2026, 10, 8, 12, 31), agora, zona)).isNull()
        assertThat(validarPrazoEnquete(LocalDateTime.of(2027, 10, 8, 12, 31), agora, zona)).isEqualTo(ErroPrazo.MUITO_LONGE)
    }

    @Test
    fun `sugestao e amanha na proxima hora cheia`() {
        assertThat(sugestaoPrazoEnquete(agora, zona)).isEqualTo(LocalDateTime.of(2026, 10, 9, 13, 0))
    }

    @Test
    fun `criar pede pergunta, duas opcoes preenchidas e prazo valido`() {
        assertThat(opcoesPreenchidas(listOf(" a ", "", "b"))).containsExactly("a", "b").inOrder()
        assertThat(podeCriarEnquete("Onde?", listOf("a", "b"), null)).isTrue()
        assertThat(podeCriarEnquete(" ", listOf("a", "b"), null)).isFalse()
        assertThat(podeCriarEnquete("Onde?", listOf("a", " "), null)).isFalse()
        assertThat(podeCriarEnquete("Onde?", listOf("a", "b"), ErroPrazo.NO_PASSADO)).isFalse()
    }

    @Test
    fun `id da enquete vem do conteudo tipo 8`() {
        val m = Mensagem(
            id = 1, remetenteId = 1, remetente = "Ana", conversaId = 2, inserida = agora, visivelEm = null, excluidaEm = null,
            referencia = null, recebida = false, visualizada = false, reproduzida = false,
            conteudos = listOf(Conteudo(1, 1, TipoConteudo.ENQUETE, "42")),
        )
        assertThat(m.idDaEnquete()).isEqualTo(42L)
    }
}
