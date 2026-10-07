package com.conversa.app.feature.chat

import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class AnexosTest {
    private val agora = Instant.parse("2026-10-07T12:00:00Z")

    private fun mensagem(id: Long, vararg conteudos: Conteudo, oculta: Boolean = false) = Mensagem(
        id = id,
        remetenteId = 1,
        remetente = "Ana",
        conversaId = 42,
        inserida = agora.plusSeconds(id),
        visivelEm = null,
        excluidaEm = if (oculta) agora else null,
        referencia = null,
        recebida = true,
        visualizada = true,
        reproduzida = false,
        conteudos = conteudos.toList(),
    )

    private fun imagem(id: String) = Conteudo(null, 2, TipoConteudo.IMAGEM, id, "$id.jpg", "jpg")

    private val texto = Conteudo(null, 1, TipoConteudo.TEXTO, "legenda")

    @Test
    fun `visualizador junta as imagens de todas as mensagens em ordem e pula as ocultas`() {
        val itens = listOf(
            ItemChat.Dia(LocalDate.of(2026, 10, 7)),
            ItemChat.Bolha(mensagem(1, texto, imagem("a"), imagem("b")), mostrarRemetente = false),
            ItemChat.NaoLidas,
            ItemChat.Bolha(mensagem(2, imagem("oculta"), oculta = true), mostrarRemetente = false),
            ItemChat.Bolha(mensagem(3, Conteudo(null, 1, TipoConteudo.ARQUIVO, "pdf", "x.pdf", "pdf")), mostrarRemetente = false),
            ItemChat.Bolha(mensagem(4, imagem("c")), mostrarRemetente = false),
        )

        val imagens = imagensDaConversa(itens)

        assertThat(imagens.map { it.conteudo.conteudo }).containsExactly("a", "b", "c").inOrder()
        assertThat(imagens.first().legenda).isEqualTo("legenda")
        assertThat(imagens.last().legenda).isEmpty()
    }
}
