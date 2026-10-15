package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/** Subtítulo da direta (PRE-03), como o `ChatHeader.vue` do web. */
class PresencaTest {
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-10-15T15:00:00Z") // 12:00 em São Paulo

    @Test
    fun `nesta conversa vem antes do estado, e depois ativo e ausente`() {
        val ativo = PresencaContato(EstadoPresenca.ATIVO)
        assertThat(subtituloPresenca(ativo, nestaConversa = true, agora, zona)).isEqualTo(SubtituloPresenca.NestaConversa)
        assertThat(subtituloPresenca(ativo, nestaConversa = false, agora, zona)).isEqualTo(SubtituloPresenca.AtivoAgora)
        assertThat(subtituloPresenca(PresencaContato(EstadoPresenca.AUSENTE), false, agora, zona)).isEqualTo(SubtituloPresenca.Ausente)
    }

    @Test
    fun `offline mostra o visto por ultimo de hoje, de ontem e com a data, no fuso do aparelho`() {
        fun visto(iso: String) = subtituloPresenca(PresencaContato(EstadoPresenca.OFFLINE, Instant.parse(iso)), false, agora, zona)

        assertThat(visto("2026-10-15T13:05:00Z")).isEqualTo(SubtituloPresenca.VistoHoje("10:05"))
        // 01:30 UTC do dia 15 ainda é dia 14 em São Paulo: ontem.
        assertThat(visto("2026-10-15T01:30:00Z")).isEqualTo(SubtituloPresenca.VistoOntem("22:30"))
        assertThat(visto("2026-10-10T12:00:00Z")).isEqualTo(SubtituloPresenca.VistoEm("10/10", "09:00"))
        assertThat(visto("2025-12-31T12:00:00Z")).isEqualTo(SubtituloPresenca.VistoEm("31/12/2025", "09:00"))
    }

    @Test
    fun `offline sem visto por ultimo ou sem presenca nao mostra nada`() {
        assertThat(subtituloPresenca(PresencaContato(EstadoPresenca.OFFLINE), false, agora, zona)).isEqualTo(SubtituloPresenca.Nenhum)
        assertThat(subtituloPresenca(null, false, agora, zona)).isEqualTo(SubtituloPresenca.Nenhum)
    }

    @Test
    fun `estado do servidor e quem nao esta no mapa fica offline`() {
        assertThat(EstadoPresenca.de("ausente")).isEqualTo(EstadoPresenca.AUSENTE)
        assertThat(EstadoPresenca.de("outro")).isNull()
        val mapa = mapOf(8L to PresencaContato(EstadoPresenca.ATIVO))
        assertThat(mapa.estadoDe(8)).isEqualTo(EstadoPresenca.ATIVO)
        assertThat(mapa.estadoDe(9)).isEqualTo(EstadoPresenca.OFFLINE)
        assertThat(mapa.estadoDe(null)).isEqualTo(EstadoPresenca.OFFLINE)
    }
}
