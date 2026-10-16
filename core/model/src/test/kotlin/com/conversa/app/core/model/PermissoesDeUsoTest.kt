package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissoesDeUsoTest {
    @Test
    fun `antes de carregar tudo pode e carregadas so as da lista`() {
        assertThat(PermissoesDeUso().pode(CodigoPermissao.REAGIR)).isTrue()
        val algumas = PermissoesDeUso(setOf(CodigoPermissao.ARQUIVO))
        assertThat(algumas.pode(CodigoPermissao.ARQUIVO)).isTrue()
        assertThat(algumas.pode(CodigoPermissao.REAGIR)).isFalse()
        assertThat(algumas.algumAnexo).isTrue()
        assertThat(PermissoesDeUso(setOf(CodigoPermissao.REAGIR)).algumAnexo).isFalse()
    }

    @Test
    fun `codigo de cada anexo como o servidor confere`() {
        assertThat(codigoDoAnexo(TipoConteudo.IMAGEM, "a.png", "image/png")).isEqualTo(CodigoPermissao.IMAGEM)
        assertThat(codigoDoAnexo(TipoConteudo.AUDIO, "a.m4a", "audio/mp4")).isEqualTo(CodigoPermissao.AUDIO)
        assertThat(codigoDoAnexo(TipoConteudo.ARQUIVO, "filme.MP4", null)).isEqualTo(CodigoPermissao.VIDEO)
        assertThat(codigoDoAnexo(TipoConteudo.ARQUIVO, "sem-extensao", "video/webm")).isEqualTo(CodigoPermissao.VIDEO)
        assertThat(codigoDoAnexo(TipoConteudo.ARQUIVO, "nota.pdf", "application/pdf")).isEqualTo(CodigoPermissao.ARQUIVO)
    }

    @Test
    fun `anexos barrados sem repetir, na ordem do web`() {
        val so = PermissoesDeUso(setOf(CodigoPermissao.IMAGEM))
        val anexos = listOf(
            Triple(TipoConteudo.ARQUIVO, "b.pdf", "application/pdf"),
            Triple(TipoConteudo.IMAGEM, "a.png", "image/png"),
            Triple(TipoConteudo.ARQUIVO, "c.mp4", "video/mp4"),
            Triple(TipoConteudo.ARQUIVO, "d.txt", "text/plain"),
        )
        assertThat(anexosBarrados(so, anexos)).containsExactly(CodigoPermissao.VIDEO, CodigoPermissao.ARQUIVO).inOrder()
        assertThat(anexosBarrados(PermissoesDeUso(), anexos)).isEmpty()
    }
}
