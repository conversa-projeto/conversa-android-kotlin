package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** Pesquisa de mensagens (TODO 8.2). */
class PesquisaTest {
    private fun mensagem(id: Long, conversa: Long, vararg conteudos: Conteudo) = Mensagem(
        id = id, remetenteId = 1, remetente = "Ana", conversaId = conversa, inserida = Instant.EPOCH, visivelEm = null,
        excluidaEm = null, referencia = null, recebida = true, visualizada = true, reproduzida = false, conteudos = conteudos.toList(),
    )

    private fun texto(t: String) = Conteudo(1, 1, TipoConteudo.TEXTO, t)

    @Test
    fun `ocorrencias sem diferenciar maiusculas e sem regex`() {
        assertThat(ocorrencias("Bolo de bolo", "BOLO")).containsExactly(0..3, 8..11).inOrder()
        assertThat(ocorrencias("a.b a+b", "a+b")).containsExactly(4..6)
        assertThat(ocorrencias("nada", " ")).isEmpty()
    }

    @Test
    fun `trecho em volta do termo numa mensagem longa`() {
        val longo = "x".repeat(100) + " reunião amanhã " + "y".repeat(100)
        val trecho = trechoComTermo(longo, "reunião", contexto = 10)
        assertThat(trecho).startsWith("…")
        assertThat(trecho).endsWith("…")
        assertThat(trecho).contains("reunião")
        assertThat(trechoComTermo("curto", "curto")).isEqualTo("curto")
    }

    @Test
    fun `texto com mencao resumida, ou o tipo quando nao ha texto`() {
        assertThat(textoParaPesquisa(mensagem(1, 9, texto("oi @[Bia](2)")))).isEqualTo("oi @Bia")
        val foto = mensagem(2, 9, Conteudo(1, 1, TipoConteudo.IMAGEM, "img"))
        assertThat(textoParaPesquisa(foto)).isNull()
        assertThat(tipoParaPesquisa(foto)).isEqualTo(TipoConteudo.IMAGEM)
        assertThat(tipoParaPesquisa(mensagem(3, 9, Conteudo(1, 1, TipoConteudo.GRAVACAO_AUDIO, "a")))).isEqualTo(TipoConteudo.AUDIO)
    }

    @Test
    fun `agrupa por conversa na ordem da primeira aparicao`() {
        val grupos = agruparPorConversa(listOf(mensagem(5, 2, texto("a")), mensagem(4, 1, texto("b")), mensagem(3, 2, texto("c"))))
        assertThat(grupos.map { it.first }).containsExactly(2L, 1L).inOrder()
        assertThat(grupos.first().second.map { it.id }).containsExactly(5L, 3L).inOrder()
    }
}
