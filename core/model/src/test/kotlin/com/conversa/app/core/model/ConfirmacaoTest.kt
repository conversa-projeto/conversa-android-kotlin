package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** Regras da confirmação de leitura (FC-521, FC-525), as mesmas do web. */
class ConfirmacaoTest {
    private val agora = Instant.parse("2026-10-15T12:00:00Z")

    private fun confirmacao(total: Int, confirmaram: List<Long>, destinatarios: List<Long> = emptyList()) = ConfirmacaoLeitura(
        total = total,
        confirmou = false,
        podeConfirmar = false,
        destinatarios = destinatarios.map { DestinatarioConfirmacao(it, "u$it") },
        usuarios = confirmaram.map { ConfirmacaoUsuario(it, "u$it", null, agora) },
    )

    private fun mensagem(
        id: Long = 10,
        remetente: Long = 7,
        inserida: Instant = agora.minusSeconds(60),
        visivelEm: Instant? = null,
        oculta: Boolean = false,
        pede: ConfirmacaoLeitura? = null,
    ) = Mensagem(
        id = id,
        remetenteId = remetente,
        remetente = "Ana",
        conversaId = 42,
        inserida = inserida,
        visivelEm = visivelEm,
        excluidaEm = if (oculta) agora else null,
        referencia = null,
        recebida = true,
        visualizada = true,
        reproduzida = false,
        conteudos = emptyList(),
        confirmacao = pede,
    )

    @Test
    fun `resumo de quem enviou - aguardando, N de T e so N quando ainda nao ha o total`() {
        assertThat(resumoConfirmacao(confirmacao(3, emptyList()))).isEqualTo(ResumoConfirmacao.Aguardando)
        assertThat(resumoConfirmacao(confirmacao(3, listOf(8, 9)))).isEqualTo(ResumoConfirmacao.PorDe(2, 3))
        assertThat(resumoConfirmacao(confirmacao(0, listOf(8)))).isEqualTo(ResumoConfirmacao.Por(1))
    }

    @Test
    fun `falta confirmar sao os da lista que ainda nao confirmaram`() {
        val falta = faltaConfirmar(confirmacao(3, confirmaram = listOf(9), destinatarios = listOf(8, 9, 35)))

        assertThat(falta.map { it.usuarioId }).containsExactly(8L, 35L).inOrder()
    }

    @Test
    fun `pedir confirmacao depois - minha, salva, visivel, nao oculta, sem pedido e ate 24 h`() {
        assertThat(podePedirConfirmacao(mensagem(), eu = 7, agora = agora)).isTrue()
        // De outra pessoa, ainda saindo, oculta ou que já pede: não.
        assertThat(podePedirConfirmacao(mensagem(remetente = 8), eu = 7, agora = agora)).isFalse()
        assertThat(podePedirConfirmacao(mensagem(id = -1), eu = 7, agora = agora)).isFalse()
        assertThat(podePedirConfirmacao(mensagem(oculta = true), eu = 7, agora = agora)).isFalse()
        assertThat(podePedirConfirmacao(mensagem(pede = confirmacao(1, emptyList())), eu = 7, agora = agora)).isFalse()
        // O prazo conta de coalesce(visivel_em, inserida): agendada que não saiu não; 24 h depois não.
        assertThat(podePedirConfirmacao(mensagem(visivelEm = agora.plusSeconds(600)), eu = 7, agora = agora)).isFalse()
        assertThat(podePedirConfirmacao(mensagem(inserida = agora.minusSeconds(24 * 3600)), eu = 7, agora = agora)).isFalse()
        assertThat(podePedirConfirmacao(mensagem(inserida = agora.minusSeconds(24 * 3600 - 1)), eu = 7, agora = agora)).isTrue()
    }

    @Test
    fun `filtro de quem precisa confirmar ignora acento e maiusculas`() {
        val membros = listOf(MembroConversa(1, 8, "Bruno", null), MembroConversa(2, 9, "Élcio", null), MembroConversa(3, 35, "Ana", null))

        assertThat(filtrarMembros(membros, "elc").map { it.usuarioId }).containsExactly(9L)
        assertThat(filtrarMembros(membros, "  ").map { it.usuarioId }).containsExactly(8L, 9L, 35L).inOrder()
        assertThat(filtrarMembros(membros, "zé")).isEmpty()
    }
}
