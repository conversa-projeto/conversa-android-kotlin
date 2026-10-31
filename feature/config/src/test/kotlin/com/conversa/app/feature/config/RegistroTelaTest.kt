package com.conversa.app.feature.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Os textos curtos da tela do registro: o tamanho do arquivo e o momento de cada entrada. */
class RegistroTelaTest {
    @Test
    fun `tamanho em bytes abaixo de 1 kB`() {
        assertThat(tamanhoLegivel(0)).isEqualTo("0 B")
        assertThat(tamanhoLegivel(121)).isEqualTo("121 B")
        assertThat(tamanhoLegivel(1023)).isEqualTo("1023 B")
        assertThat(tamanhoLegivel(1024)).isEqualTo("1 kB")
        assertThat(tamanhoLegivel(56_320)).isEqualTo("55 kB")
        assertThat(tamanhoLegivel(4_300_000)).isEqualTo("4,1 MB")
    }

    @Test
    fun `momento curto`() {
        assertThat(momentoCurto("2026-10-31 15:46:12.345 -03:00")).isEqualTo("31/10 15:46:12")
        assertThat(momentoCurto("curto")).isEqualTo("curto")
    }
}
