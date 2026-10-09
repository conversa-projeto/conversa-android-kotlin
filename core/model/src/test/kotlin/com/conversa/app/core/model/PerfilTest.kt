package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Regras do perfil (TODO 8.3) e do perfil de outra pessoa (8.4), como o web. */
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

    private fun conversa(tipo: TipoConversa = TipoConversa.DIRETA, destinatario: Long? = 9, foto: String? = "https://s/foto") = Conversa(
        id = 3,
        tipo = tipo,
        descricao = "Bia",
        nome = "Bia",
        destinatarioId = destinatario,
        ultimaMensagemId = 0,
        ultimaMensagemEm = null,
        ultimaMensagemTexto = null,
        naoLidas = 0,
        fixadaOrdem = null,
        arquivadaEm = null,
        avatarUrl = foto,
    )

    @Test
    fun `perfil da direta vem do contato, com a foto da conversa`() {
        val contatos = listOf(
            Contato(8, "Caio", "caio", "caio@x.test", null),
            Contato(9, "Bia Souza", "bia", "bia@x.test", " "),
        )

        assertThat(fichaDaConversa(conversa(), contatos))
            .isEqualTo(FichaUsuario(9, "Bia Souza", email = "bia@x.test", telefone = null, avatarUrl = "https://s/foto"))
    }

    @Test
    fun `sem o contato o perfil sai da conversa, e grupo nao tem perfil`() {
        assertThat(fichaDaConversa(conversa(foto = ""), emptyList()))
            .isEqualTo(FichaUsuario(9, "Bia", email = null, telefone = null, avatarUrl = null))
        assertThat(fichaDaConversa(conversa(tipo = TipoConversa.GRUPO, destinatario = null), emptyList())).isNull()
    }
}
