package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class HistoricoChamadasTest {
    private val eu = 7L
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val hoje = LocalDate.of(2026, 10, 8)

    private fun p(id: Long, nome: String, status: StatusParticipante) = ParticipanteHistorico(id, nome, status, null)

    private fun chamada(
        id: Long,
        criadoPor: Long,
        vararg participantes: ParticipanteHistorico,
        status: StatusChamada = StatusChamada.ENCERRADA,
        criadoEm: String = "2026-10-08T15:00:00Z",
    ) = ChamadaHistorico(id, TipoChamada.AUDIO, status, Instant.parse(criadoEm), criadoPor, 42, null, participantes.toList())

    @Test
    fun `efetuada, outro participante e grupo`() {
        val direta = chamada(1, eu, p(eu, "Ana", StatusParticipante.ENTROU), p(8, "Bruno", StatusParticipante.SAIU))
        val grupo =
            chamada(
                2,
                8,
                p(8, "Bruno", StatusParticipante.ENTROU),
                p(eu, "Ana", StatusParticipante.ENTROU),
                p(9, "Carla", StatusParticipante.SAIU),
            )

        assertThat(direta.efetuada(eu)).isTrue()
        assertThat(direta.outro(eu)?.nome).isEqualTo("Bruno")
        assertThat(direta.grupo).isFalse()
        assertThat(grupo.efetuada(eu)).isFalse()
        assertThat(grupo.grupo).isTrue()
    }

    @Test
    fun `perdida - recebida em que eu fiquei tocando ou fui recusado sozinho`() {
        val tocou =
            chamada(
                1,
                8,
                p(8, "Bruno", StatusParticipante.ENTROU),
                p(eu, "Ana", StatusParticipante.PENDENTE),
                status = StatusChamada.CANCELADA,
            )
        val naoAtendeu =
            chamada(
                2,
                8,
                p(8, "Bruno", StatusParticipante.ENTROU),
                p(eu, "Ana", StatusParticipante.RECUSOU),
                status = StatusChamada.RECUSADA,
            )
        val atendida = chamada(3, 8, p(8, "Bruno", StatusParticipante.SAIU), p(eu, "Ana", StatusParticipante.SAIU))
        val minhaSemResposta =
            chamada(
                4,
                eu,
                p(eu, "Ana", StatusParticipante.ENTROU),
                p(8, "Bruno", StatusParticipante.RECUSOU),
                status = StatusChamada.RECUSADA,
            )
        val desconectada = chamada(5, eu, p(eu, "Ana", StatusParticipante.ENTROU), status = StatusChamada.DESCONECTADA)

        assertThat(tocou.perdida(eu)).isTrue()
        assertThat(naoAtendeu.perdida(eu)).isTrue()
        assertThat(atendida.perdida(eu)).isFalse()
        assertThat(minhaSemResposta.perdida(eu)).isFalse()
        assertThat(desconectada.perdida(eu)).isTrue()
    }

    @Test
    fun `filtro de perdidas e busca pelo nome de qualquer outro participante`() {
        val comBruno = chamada(1, 8, p(8, "Bruno Lima", StatusParticipante.ENTROU), p(eu, "Ana", StatusParticipante.PENDENTE))
        val grupo =
            chamada(
                2,
                eu,
                p(eu, "Ana", StatusParticipante.ENTROU),
                p(9, "Carla", StatusParticipante.SAIU),
                p(10, "Davi", StatusParticipante.SAIU),
            )
        val lista = listOf(comBruno, grupo)

        assertThat(filtrarHistorico(lista, eu, soPerdidas = true, busca = "")).containsExactly(comBruno)
        assertThat(filtrarHistorico(lista, eu, soPerdidas = false, busca = " davi ")).containsExactly(grupo)
        // O meu próprio nome não conta na busca.
        assertThat(filtrarHistorico(lista, eu, soPerdidas = false, busca = "ana")).isEmpty()
        assertThat(filtrarHistorico(lista, eu, soPerdidas = false, busca = "")).containsExactly(comBruno, grupo).inOrder()
    }

    @Test
    fun `agrupa por dia local - Hoje, Ontem e a data, na ordem que veio`() {
        val hojeTarde = chamada(1, eu, criadoEm = "2026-10-08T20:00:00Z")
        // 02:00 UTC do dia 8 é 23:00 do dia 7 em São Paulo: "Ontem".
        val ontemNoite = chamada(2, eu, criadoEm = "2026-10-08T02:00:00Z")
        val ontemCedo = chamada(3, eu, criadoEm = "2026-10-07T12:00:00Z")
        val antes = chamada(4, eu, criadoEm = "2026-10-01T12:00:00Z")

        val grupos = agruparHistorico(listOf(hojeTarde, ontemNoite, ontemCedo, antes), hoje, zona)

        assertThat(grupos.map { it.dia })
            .containsExactly(DiaHistorico.Hoje, DiaHistorico.Ontem, DiaHistorico.Data(LocalDate.of(2026, 10, 1))).inOrder()
        assertThat(grupos[1].chamadas.map { it.id }).containsExactly(2L, 3L).inOrder()
    }
}
