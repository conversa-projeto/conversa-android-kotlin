package com.conversa.app.core.network.json

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ConversaJsonTest {
    @Test
    fun `datas iso com Z, offset, sem fuso e com espaco`() {
        assertThat(lerInstant("2026-10-06T12:00:00.123Z")).isEqualTo(Instant.parse("2026-10-06T12:00:00.123Z"))
        assertThat(lerInstant("2026-10-06T09:00:00-03:00")).isEqualTo(Instant.parse("2026-10-06T12:00:00Z"))
        // JSON de resumo da chamada: sem fuso = UTC (contrato §9.8)
        assertThat(lerInstant("2026-10-06T12:00:05")).isEqualTo(Instant.parse("2026-10-06T12:00:05Z"))
        assertThat(lerInstant("2026-10-06 12:00:05")).isEqualTo(Instant.parse("2026-10-06T12:00:05Z"))
        assertThat(lerInstant("ontem")).isNull()
        assertThat(lerInstant("")).isNull()
    }
}
