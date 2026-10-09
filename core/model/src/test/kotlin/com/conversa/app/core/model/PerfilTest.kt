package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Regras do perfil (TODO 8.3), como o web. */
class PerfilTest {
    @Test
    fun `troca de senha confere na ordem do web`() {
        assertThat(validarTrocaDeSenha("", "123456", "123456")).isEqualTo(ErroSenha.CAMPOS_VAZIOS)
        assertThat(validarTrocaDeSenha("velha", "12345", "12345")).isEqualTo(ErroSenha.CURTA)
        assertThat(validarTrocaDeSenha("velha", "123456", "123457")).isEqualTo(ErroSenha.NAO_CONFERE)
        assertThat(validarTrocaDeSenha("velha", "123456", "123456")).isNull()
    }

    @Test
    fun `nome e email sao obrigatorios`() {
        assertThat(dadosDoPerfilValidos("Ana", "ana@exemplo.test")).isTrue()
        assertThat(dadosDoPerfilValidos(" ", "ana@exemplo.test")).isFalse()
        assertThat(dadosDoPerfilValidos("Ana", "")).isFalse()
    }

    @Test
    fun `recorte quadrado do meio`() {
        assertThat(recorteQuadradoCentral(400, 300)).isEqualTo(Recorte(50, 0, 300))
        assertThat(recorteQuadradoCentral(300, 500)).isEqualTo(Recorte(0, 100, 300))
        assertThat(recorteQuadradoCentral(256, 256)).isEqualTo(Recorte(0, 0, 256))
    }
}
