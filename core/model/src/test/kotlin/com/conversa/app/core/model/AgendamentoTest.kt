package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** Agendar mensagem e detalhe do status (TODO 7.10), como o web. */
class AgendamentoTest {
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-10-08T15:00:00Z") // 12:00 em São Paulo

    private fun mensagem(visivelEm: Instant? = null, excluidaEm: Instant? = null, tipo: TipoConteudo = TipoConteudo.TEXTO) = Mensagem(
        id = 1, remetenteId = 1, remetente = "Ana", conversaId = 1, inserida = agora, visivelEm = visivelEm,
        excluidaEm = excluidaEm, referencia = null, recebida = false, visualizada = false, reproduzida = false,
        conteudos = listOf(Conteudo(1, 1, tipo, "x")),
    )

    @Test
    fun `sugestao e amanha as 8h`() {
        assertThat(sugestaoAgendamento(agora, zona)).isEqualTo(LocalDateTime.of(2026, 10, 9, 8, 0))
    }

    @Test
    fun `no minimo 5 minutos e no maximo 1 ano`() {
        assertThat(validarAgendamento(LocalDateTime.of(2026, 10, 8, 12, 4), agora, zona)).isEqualTo(ErroAgendamento.MUITO_CEDO)
        assertThat(validarAgendamento(LocalDateTime.of(2026, 10, 8, 12, 5), agora, zona)).isNull()
        assertThat(validarAgendamento(LocalDateTime.of(2027, 10, 8, 12, 0), agora, zona)).isNull()
        assertThat(validarAgendamento(LocalDateTime.of(2027, 10, 8, 12, 1), agora, zona)).isEqualTo(ErroAgendamento.MUITO_LONGE)
    }

    @Test
    fun `prazo hoje, amanha, com a data e com o ano se for outro (formatarPrazo do web)`() {
        assertThat(quandoPrazo(Instant.parse("2026-10-08T21:00:00Z"), agora, zona)).isEqualTo(DiaPrazo.HOJE to "18:00")
        assertThat(quandoPrazo(Instant.parse("2026-10-09T11:30:00Z"), agora, zona)).isEqualTo(DiaPrazo.AMANHA to "08:30")
        assertThat(quandoPrazo(Instant.parse("2026-10-12T21:00:00Z"), agora, zona)).isEqualTo(DiaPrazo.OUTRO to "12/10 18:00")
        assertThat(quandoPrazo(Instant.parse("2027-01-05T11:00:00Z"), agora, zona)).isEqualTo(DiaPrazo.OUTRO to "05/01/2027 08:00")
    }

    @Test
    fun `agendadas saem do chat, em ordem de horario, e voltam na hora exata`() {
        val depois = mensagem(visivelEm = agora.plusSeconds(120)).copy(id = 2)
        val antes = mensagem(visivelEm = agora.plusSeconds(60)).copy(id = 3)
        val normal = mensagem().copy(id = 4)
        val jaSaiu = mensagem(visivelEm = agora).copy(id = 5)

        val separadas = separarAgendadas(listOf(depois, normal, antes, jaSaiu), agora)

        assertThat(separadas.visiveis.map { it.id }).containsExactly(4L, 5L).inOrder()
        assertThat(separadas.agendadas.map { it.id }).containsExactly(3L, 2L).inOrder()
        assertThat(separarAgendadas(listOf(antes), agora.plusSeconds(60)).agendadas).isEmpty()
    }

    @Test
    fun `direta - etapas com ouvida so em audio e oculta so se ocultada`() {
        val recebida = agora.plusSeconds(60)
        val destino = listOf(StatusDestinatario(2, "Bia", recebida, null, null))

        assertThat(etapasDaDireta(mensagem(), destino)).containsExactly(
            EtapaEntrega.ENVIADA to agora,
            EtapaEntrega.RECEBIDA to recebida,
            EtapaEntrega.VISUALIZADA to null,
        ).inOrder()
        val oculta = agora.plusSeconds(120)
        assertThat(etapasDaDireta(mensagem(excluidaEm = oculta, tipo = TipoConteudo.GRAVACAO_AUDIO), emptyList()).map { it.first })
            .containsExactly(
                EtapaEntrega.ENVIADA,
                EtapaEntrega.RECEBIDA,
                EtapaEntrega.VISUALIZADA,
                EtapaEntrega.OUVIDA,
                EtapaEntrega.OCULTA,
            )
            .inOrder()
    }

    @Test
    fun `grupo - visualizada, so recebida e aguardando, sem secoes vazias`() {
        val t = agora.plusSeconds(30)
        val secoes = secoesDoGrupo(
            listOf(
                StatusDestinatario(2, "Bia", t, t, null),
                StatusDestinatario(3, "Caio", null, null, null),
                StatusDestinatario(4, "Davi", null, null, null),
            ),
        )

        assertThat(secoes).containsExactly(
            SecaoStatus.VISUALIZADA_POR to listOf("Bia" to t),
            SecaoStatus.AGUARDANDO to listOf("Caio" to null, "Davi" to null),
        ).inOrder()
    }

    @Test
    fun `hora do status - hoje so a hora, outro dia com a data`() {
        assertThat(horaDoStatus(agora, agora, zona)).isEqualTo("12:00")
        assertThat(horaDoStatus(Instant.parse("2026-10-07T15:00:00Z"), agora, zona)).isEqualTo("07/10 12:00")
    }
}
