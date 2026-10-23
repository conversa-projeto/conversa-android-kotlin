package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** Áudios em sequência (ANX-19), como o `tocarProximo` do web. */
class AudiosEmSequenciaTest {
    private fun conteudo(ordem: Int, tipo: TipoConteudo) = Conteudo(null, ordem, tipo, "id$ordem")

    private fun mensagem(id: Long, vararg conteudos: Conteudo, oculta: Boolean = false) = Mensagem(
        id = id,
        remetenteId = if (id % 2 == 0L) 7 else 8,
        remetente = "Ana",
        conversaId = 5,
        inserida = Instant.EPOCH.plusSeconds(id),
        visivelEm = null,
        excluidaEm = if (oculta) Instant.EPOCH else null,
        referencia = null,
        recebida = true,
        visualizada = true,
        reproduzida = false,
        conteudos = conteudos.toList(),
    )

    @Test
    fun `o seguinte da mesma mensagem vem antes da mensagem de baixo`() {
        val dois = mensagem(1, conteudo(1, TipoConteudo.AUDIO), conteudo(2, TipoConteudo.TEXTO), conteudo(3, TipoConteudo.GRAVACAO_AUDIO))
        val abaixo = mensagem(2, conteudo(1, TipoConteudo.AUDIO))
        val lista = listOf(dois, abaixo)
        assertThat(proximoAudio(lista, 1, 1)).isEqualTo(dois to dois.conteudos[2])
        // Acabou a mensagem: o primeiro da de baixo, mesmo sendo minha.
        assertThat(proximoAudio(lista, 1, 3)).isEqualTo(abaixo to abaixo.conteudos[0])
        // Era o último áudio da conversa.
        assertThat(proximoAudio(lista, 2, 1)).isNull()
    }

    @Test
    fun `para quando a mensagem de baixo nao tem audio ou esta oculta`() {
        val audio = mensagem(1, conteudo(1, TipoConteudo.AUDIO))
        val texto = mensagem(2, conteudo(1, TipoConteudo.TEXTO))
        val depois = mensagem(3, conteudo(1, TipoConteudo.AUDIO))
        assertThat(proximoAudio(listOf(audio, texto, depois), 1, 1)).isNull()
        val oculta = mensagem(2, conteudo(1, TipoConteudo.AUDIO), oculta = true)
        assertThat(proximoAudio(listOf(audio, oculta), 1, 1)).isNull()
        // Mensagem que não está na lista (rolou para fora ou foi apagada).
        assertThat(proximoAudio(listOf(audio), 99, 1)).isNull()
    }
}
