package com.conversa.app.feature.chat

import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class AcoesMensagemTest {
    private val agora = Instant.parse("2026-10-08T15:00:00Z")

    private fun mensagem(id: Long = 5, tipo: TipoConteudo = TipoConteudo.TEXTO, excluidaEm: Instant? = null) = Mensagem(
        id = id,
        remetenteId = 7,
        remetente = "Ana",
        conversaId = 42,
        inserida = agora,
        visivelEm = null,
        excluidaEm = excluidaEm,
        referencia = null,
        recebida = true,
        visualizada = true,
        reproduzida = false,
        conteudos = listOf(Conteudo(id, 1, tipo, if (tipo == TipoConteudo.TEXTO) "oi" else "{}")),
    )

    @Test
    fun `menu so abre em mensagem do servidor que nao e chamada nem oculta`() {
        assertThat(podeAbrirMenu(mensagem())).isTrue()
        assertThat(podeAbrirMenu(mensagem(id = -1))).isFalse()
        assertThat(podeAbrirMenu(mensagem(excluidaEm = agora))).isFalse()
        assertThat(podeAbrirMenu(mensagem(tipo = TipoConteudo.CHAMADA))).isFalse()
    }

    @Test
    fun `hora da reacao - so a hora se foi hoje, senao dia e hora`() {
        val zona = ZoneId.of("America/Sao_Paulo")
        val hoje = LocalDate.of(2026, 10, 8)

        assertThat(horaDaReacao(agora, zona, hoje)).isEqualTo("12:00")
        assertThat(horaDaReacao(agora.minusSeconds(86_400), zona, hoje)).isEqualTo("07/10 12:00")
    }
}
