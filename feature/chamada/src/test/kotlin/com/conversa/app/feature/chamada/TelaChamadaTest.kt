package com.conversa.app.feature.chamada

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TelaChamadaTest {
    @Test
    fun `duracao como no web - mm ss e hh mm ss depois de uma hora`() {
        assertThat(formatarDuracao(0)).isEqualTo("00:00")
        assertThat(formatarDuracao(65)).isEqualTo("01:05")
        assertThat(formatarDuracao(3_599)).isEqualTo("59:59")
        assertThat(formatarDuracao(3_725)).isEqualTo("01:02:05")
    }

    @Test
    fun `avisos com o texto do web`() {
        val textos = mapOf(
            R.string.ja_existe_chamada to "Já existe uma chamada em andamento",
            R.string.sem_microfone to "Não foi possível acessar o microfone",
            R.string.falha_chamada to "Não foi possível completar a chamada",
            R.string.falha_chamada_detalhe to "Não foi possível completar a chamada: %1\$s",
        )
        val texto = { id: Int -> textos.getValue(id) }

        assertThat(textoDoAviso(AvisoChamada.JaEmChamada, texto)).isEqualTo("Já existe uma chamada em andamento")
        assertThat(textoDoAviso(AvisoChamada.Falhou("Chamada não encontrada!"), texto))
            .isEqualTo("Não foi possível completar a chamada: Chamada não encontrada!")
        assertThat(textoDoAviso(AvisoChamada.Falhou(null), texto)).isEqualTo("Não foi possível completar a chamada")
    }
}
