package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tema do app (TODO 8.5). */
class AparenciaTest {
    @Test
    fun `sistema segue o aparelho, claro e escuro fixam`() {
        assertThat(PreferenciaTema.SISTEMA.escuro(sistemaEscuro = true)).isTrue()
        assertThat(PreferenciaTema.SISTEMA.escuro(sistemaEscuro = false)).isFalse()
        assertThat(PreferenciaTema.CLARO.escuro(sistemaEscuro = true)).isFalse()
        assertThat(PreferenciaTema.ESCURO.escuro(sistemaEscuro = false)).isTrue()
    }

    @Test
    fun `chave desconhecida ou ausente fica no padrao, claro enquanto o escuro for proposta`() {
        assertThat(PreferenciaTema.de("escuro")).isEqualTo(PreferenciaTema.ESCURO)
        assertThat(PreferenciaTema.de("roxo")).isEqualTo(PreferenciaTema.CLARO)
        assertThat(PreferenciaTema.de(null)).isEqualTo(PreferenciaTema.CLARO)
    }
}
