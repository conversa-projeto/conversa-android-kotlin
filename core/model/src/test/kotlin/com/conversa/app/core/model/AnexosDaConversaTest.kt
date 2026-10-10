package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

/** Anexos da conversa (TODO 8.6). */
class AnexosDaConversaTest {
    @Test
    fun `filtros viram o csv de tipos da rota`() {
        assertThat(FiltroAnexos.TODOS.parametro).isEqualTo("2,3,4,5")
        assertThat(FiltroAnexos.IMAGENS.parametro).isEqualTo("2")
        assertThat(FiltroAnexos.GRAVACOES.parametro).isEqualTo("5")
        assertThat(DirecaoAnexos.TODOS.chave).isEmpty()
        assertThat(DirecaoAnexos.RECEBIDOS.chave).isEqualTo("recebidos")
    }

    @Test
    fun `data - hoje so a hora, outro dia a data curta`() {
        val zona = ZoneOffset.UTC
        val hoje = LocalDate.parse("2026-10-09")

        assertThat(dataDoAnexo(Instant.parse("2026-10-09T14:05:00Z"), zona, hoje)).isEqualTo("14:05")
        assertThat(dataDoAnexo(Instant.parse("2026-10-08T23:59:00Z"), zona, hoje)).isEqualTo("08/10/26")
    }

    @Test
    fun `vira conteudo de mensagem e a pagina acaba com menos de 60`() {
        val anexo = AnexoDaConversa(5, "abc", "festa.mp4", "mp4", 10, null, TipoConteudo.ARQUIVO, 70, 3, "Ana")

        assertThat(anexo.paraConteudo()).isEqualTo(Conteudo(null, 0, TipoConteudo.ARQUIVO, "abc", "festa.mp4", "mp4"))
        assertThat(acabouAPagina(59)).isTrue()
        assertThat(acabouAPagina(60)).isFalse()
    }
}
