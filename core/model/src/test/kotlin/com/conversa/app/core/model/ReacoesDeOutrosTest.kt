package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ReacoesDeOutrosTest {
    private fun mensagem(vararg reacoes: Reacao) = Mensagem(
        id = 1,
        remetenteId = 1,
        remetente = "Eu",
        conversaId = 42,
        inserida = Instant.EPOCH,
        visivelEm = null,
        excluidaEm = null,
        referencia = null,
        recebida = true,
        visualizada = true,
        reproduzida = false,
        conteudos = emptyList(),
        reacoes = reacoes.toList(),
    )

    @Test
    fun `conta as reacoes dos outros e nao a minha`() {
        assertThat(reacoesDeOutros(mensagem())).isEqualTo(0)
        assertThat(reacoesDeOutros(mensagem(Reacao("👍", 1, reagiu = true, usuarios = emptyList())))).isEqualTo(0)
        assertThat(
            reacoesDeOutros(
                mensagem(Reacao("👍", 2, reagiu = true, usuarios = emptyList()), Reacao("😂", 3, reagiu = false, usuarios = emptyList())),
            ),
        ).isEqualTo(4)
    }
}
