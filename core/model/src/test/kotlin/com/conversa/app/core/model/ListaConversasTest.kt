package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class ListaConversasTest {
    private fun conversa(
        id: Long,
        tipo: TipoConversa = TipoConversa.DIRETA,
        descricao: String? = null,
        nome: String? = null,
        destinatarioId: Long? = null,
        ultimaMensagemId: Long = 0,
        texto: String? = null,
        fixadaOrdem: Int? = null,
        arquivada: Boolean = false,
        avatarUrl: String? = null,
    ) = Conversa(
        id = id,
        tipo = tipo,
        descricao = descricao,
        nome = nome,
        destinatarioId = destinatarioId,
        ultimaMensagemId = ultimaMensagemId,
        ultimaMensagemEm = null,
        ultimaMensagemTexto = texto,
        naoLidas = 0,
        fixadaOrdem = fixadaOrdem,
        arquivadaEm = if (arquivada) Instant.EPOCH else null,
        avatarUrl = avatarUrl,
    )

    // --- CON-01: título ---

    @Test
    fun `titulo usa descricao, depois nome, depois Conversa id`() {
        assertThat(conversa(1, descricao = "Projeto").titulo).isEqualTo("Projeto")
        assertThat(conversa(2, descricao = "  ", nome = "Bruno").titulo).isEqualTo("Bruno")
        assertThat(conversa(3).titulo).isEqualTo("Conversa #3")
    }

    // --- CON-01: prévia ---

    @Test
    fun `previa resume mencao e codigo`() {
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "oi @[Ana](7)")))
            .isEqualTo(PreviaConversa.Texto("oi @Ana"))
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "```js\nx\n```")))
            .isEqualTo(PreviaConversa.Texto("Código (js)"))
    }

    @Test
    fun `previa de imagem, figurinha, votacao, anexo e vazia`() {
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "imagem"))).isEqualTo(PreviaConversa.Imagem)
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "figurinha"))).isEqualTo(PreviaConversa.Figurinha)
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "enquete"))).isEqualTo(PreviaConversa.Votacao)
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = ""))).isEqualTo(PreviaConversa.SemTexto)
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 0, texto = null))).isEqualTo(PreviaConversa.SemMensagens)
    }

    @Test
    fun `mensagem oculta aparece como o servidor manda`() {
        assertThat(previaDaConversa(conversa(1, ultimaMensagemId = 5, texto = "Mensagem oculta")))
            .isEqualTo(PreviaConversa.Texto("Mensagem oculta"))
    }

    // --- CON-01: ordem ---

    @Test
    fun `fixadas primeiro pela ordem, depois pela ultima mensagem`() {
        val lista = listOf(
            conversa(1, ultimaMensagemId = 50),
            conversa(2, ultimaMensagemId = 90),
            conversa(3, ultimaMensagemId = 10, fixadaOrdem = 2),
            conversa(4, ultimaMensagemId = 0),
            conversa(5, ultimaMensagemId = 5, fixadaOrdem = 1),
        )
        assertThat(ordenarConversas(lista).map { it.id }).containsExactly(5L, 3L, 2L, 1L, 4L).inOrder()
    }

    // --- Hora ---

    @Test
    fun `rotulo de data hoje, ontem e antes`() {
        val zona = ZoneId.of("America/Manaus")
        val agora = Instant.parse("2026-10-07T15:00:00Z") // 11:00 em Manaus
        assertThat(rotuloData(Instant.parse("2026-10-07T13:05:00Z"), agora, zona)).isEqualTo(RotuloData.Hora("09:05"))
        assertThat(rotuloData(Instant.parse("2026-10-07T03:00:00Z"), agora, zona)).isEqualTo(RotuloData.Ontem)
        assertThat(rotuloData(Instant.parse("2026-10-01T12:00:00Z"), agora, zona)).isEqualTo(RotuloData.Data("01/10/26"))
    }

    // --- CON-02: filtro ---

    @Test
    fun `filtro por titulo e previa, sem acento e sem maiuscula`() {
        val c = conversa(1, descricao = "Família", ultimaMensagemId = 3, texto = "Reunião amanhã")
        assertThat(conversaCombina(c, "familia")).isTrue()
        assertThat(conversaCombina(c, "REUNIAO")).isTrue()
        assertThat(conversaCombina(c, "trabalho")).isFalse()
        assertThat(conversaCombina(c, "   ")).isTrue()
    }

    @Test
    fun `filtro de contato por nome, login e email`() {
        val contato = Contato(8, "Bruno Lima", "blima", "bruno@x.com", null)
        assertThat(contatoCombina(contato, "lima")).isTrue()
        assertThat(contatoCombina(contato, "blim")).isTrue()
        assertThat(contatoCombina(contato, "@x.com")).isTrue()
        assertThat(contatoCombina(contato, "ana")).isFalse()
    }

    // --- CON-06 / CON-11 ---

    @Test
    fun `direta com o contato e avatar de reserva`() {
        val conversas = listOf(
            conversa(1, tipo = TipoConversa.GRUPO, destinatarioId = null),
            conversa(2, destinatarioId = 8, avatarUrl = "https://x/a.jpg"),
        )
        assertThat(conversas.diretaCom(8)?.id).isEqualTo(2)
        assertThat(conversas.diretaCom(9)).isNull()
        val contato = Contato(8, "Bruno", "b", null, null)
        assertThat(avatarDoContato(contato, conversas)).isEqualTo("https://x/a.jpg")
        assertThat(avatarDoContato(contato.copy(avatarUrl = "https://x/b.jpg"), conversas)).isEqualTo("https://x/b.jpg")
    }

    // --- CON-03 ---

    @Test
    fun `fixar entra no fim, desafixar tira e mover troca a posicao`() {
        val conversas = listOf(
            conversa(1, fixadaOrdem = 1),
            conversa(2, fixadaOrdem = 2),
            conversa(3),
            conversa(4, fixadaOrdem = 3, arquivada = true),
        )
        assertThat(fixadasAoFixar(conversas, 3)).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(fixadasAoDesafixar(conversas, 1)).containsExactly(2L)
        assertThat(fixadasAoMover(conversas, 2, -1)).containsExactly(2L, 1L).inOrder()
        assertThat(fixadasAoMover(conversas, 1, -1)).isNull()
        assertThat(fixadasAoMover(conversas, 2, +1)).isNull()
    }
}
