package com.conversa.app.notificacoes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Quando uma mensagem nova vira notificação, som ou nada (NOT-01/NOT-02, como o web). */
class DecidirAvisoTest {
    @Test
    fun `segundo plano notifica, na frente toca som, conversa na tela ou arquivada nao avisa`() {
        assertThat(decidirAviso(arquivada = false, emPrimeiroPlano = false, conversaNaTela = false)).isEqualTo(AvisoMensagem.NOTIFICAR)
        assertThat(decidirAviso(arquivada = false, emPrimeiroPlano = true, conversaNaTela = false)).isEqualTo(AvisoMensagem.SOM)
        assertThat(decidirAviso(arquivada = false, emPrimeiroPlano = true, conversaNaTela = true)).isEqualTo(AvisoMensagem.NADA)
        assertThat(decidirAviso(arquivada = true, emPrimeiroPlano = false, conversaNaTela = false)).isEqualTo(AvisoMensagem.NADA)
    }
}
