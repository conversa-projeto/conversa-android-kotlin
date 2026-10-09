package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/** Regras da aba Atividades (TODO 8.1), como o `AtividadesPage.vue`. */
class AtividadesTest {
    private val zona = ZoneId.of("America/Sao_Paulo")
    private val agora = Instant.parse("2026-10-08T15:00:00Z") // 12:00 em São Paulo

    private fun atividade(
        id: Long,
        tipo: TipoAtividade = TipoAtividade.REACAO,
        criadoEm: Instant? = agora,
        texto: String? = null,
        conteudoTipo: TipoConteudo? = null,
        conversaTipo: TipoConversa? = TipoConversa.DIRETA,
        conversaDescricao: String? = "Bia",
        emoji: String? = null,
    ) = Atividade(
        id = id, tipo = tipo, criadoEm = criadoEm, nova = false, autorId = 2, autorNome = "Bia", autorAvatarUrl = null,
        conversaId = 9, conversaTipo = conversaTipo, conversaDescricao = conversaDescricao, mensagemId = 5,
        conteudoTipo = conteudoTipo, texto = texto, chamadaId = null, chamadaTipo = null, emoji = emoji,
    )

    @Test
    fun `agrupa em Hoje, Ontem e a data, na ordem`() {
        val grupos = agruparAtividades(
            listOf(
                atividade(4, criadoEm = agora),
                atividade(3, criadoEm = agora.minusSeconds(3600)),
                atividade(2, criadoEm = Instant.parse("2026-10-07T20:00:00Z")),
                atividade(1, criadoEm = Instant.parse("2026-10-01T12:00:00Z")),
            ),
            agora,
            zona,
        )

        assertThat(
            grupos.map {
                it.first
            },
        ).containsExactly(DiaAtividade.Hoje, DiaAtividade.Ontem, DiaAtividade.Data("01/10/2026")).inOrder()
        assertThat(grupos.first().second.map { it.id }).containsExactly(4L, 3L).inOrder()
    }

    @Test
    fun `selo pelo tipo, com o emoji da reacao`() {
        assertThat(atividade(1, emoji = "🔥").textoDoSelo()).isEqualTo("🔥")
        assertThat(atividade(1).textoDoSelo()).isEqualTo("❤️")
        assertThat(atividade(1, tipo = TipoAtividade.RESPOSTA).textoDoSelo()).isEqualTo("↩")
        assertThat(atividade(1, tipo = TipoAtividade.MENCAO).textoDoSelo()).isEqualTo("@")
        assertThat(atividade(1, tipo = TipoAtividade.CHAMADA_PERDIDA).textoDoSelo()).isEqualTo("✆")
    }

    @Test
    fun `em grupo diz onde foi, na direta nao`() {
        assertThat(atividade(1, conversaTipo = TipoConversa.GRUPO, conversaDescricao = "Família").ondeFoi()).isEqualTo("Família")
        assertThat(atividade(1).ondeFoi()).isNull()
    }

    @Test
    fun `previa - texto com mencao resumida, tipo do conteudo, nada na chamada perdida`() {
        assertThat(atividade(1, texto = "oi @[Ana](7)").previa()).isEqualTo(PreviaAtividade.Texto("oi @Ana"))
        assertThat(atividade(1, conteudoTipo = TipoConteudo.IMAGEM).previa()).isEqualTo(PreviaAtividade.Tipo(TipoConteudo.IMAGEM))
        assertThat(atividade(1, conteudoTipo = TipoConteudo.TEXTO).previa()).isNull()
        assertThat(atividade(1, tipo = TipoAtividade.CHAMADA_PERDIDA, texto = "x").previa()).isNull()
    }
}
