package com.conversa.app.feature.chat

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagramaMermaidTest {
    @Test
    fun `cor translucida vira a cor que aparece sobre o fundo`() {
        // O divisorLista do tema claro (preto a 0x32) sobre branco: cinza claro, não preto.
        assertThat(corHex(Color(0x32000000), Color.White)).isEqualTo("#CDCDCD")
        // O do escuro (branco a 0x33) sobre preto: cinza escuro, não branco.
        assertThat(corHex(Color(0x33FFFFFF), Color.Black)).isEqualTo("#333333")
        // Opaca fica como está.
        assertThat(corHex(Color(0xFF007DFF), Color.White)).isEqualTo("#007DFF")
    }
}
