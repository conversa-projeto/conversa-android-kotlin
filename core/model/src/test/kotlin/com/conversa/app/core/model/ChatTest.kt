package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** Casos copiados dos testes do web (`classificarMensagem.test.ts`, `formatters.test.ts`) e os do app. */
class ChatTest {
    private val agora = Instant.parse("2026-10-07T15:00:00Z")

    private fun conteudo(tipo: TipoConteudo, texto: String = "", ordem: Int = 1) = Conteudo(
        id = ordem.toLong(),
        ordem = ordem,
        tipo = tipo,
        conteudo = texto,
    )

    private fun texto(t: String) = conteudo(TipoConteudo.TEXTO, t)

    private fun mensagem(
        vararg conteudos: Conteudo,
        id: Long = 1,
        remetente: Long = 8,
        inserida: Instant = agora,
        oculta: Boolean = false,
        referencia: ReferenciaMensagem? = null,
        recebida: Boolean = false,
        visualizada: Boolean = false,
    ) = Mensagem(
        id = id,
        remetenteId = remetente,
        remetente = "Bruno",
        conversaId = 42,
        inserida = inserida,
        visivelEm = null,
        excluidaEm = if (oculta) agora else null,
        referencia = referencia,
        recebida = recebida,
        visualizada = visualizada,
        reproduzida = false,
        conteudos = conteudos.toList(),
    )

    private fun citando(vararg conteudos: Conteudo, oculta: Boolean = false) = ReferenciaMensagem(
        TipoReferencia.RESPOSTA,
        MensagemResumida(9, 42, "Ana", agora, if (oculta) agora else null, conteudos.toList(), null),
    )

    // --- Citação: conteúdos da citada e os próprios (como o BolhaReferencia.vue) ---

    private fun encaminhando(vararg conteudos: Conteudo) = ReferenciaMensagem(
        TipoReferencia.ENCAMINHAMENTO,
        MensagemResumida(9, 7, "Ana", agora, null, conteudos.toList(), null),
    )

    @Test
    fun `resposta - a citacao mostra a citada e embaixo vai tudo o que e meu`() {
        val m = mensagem(texto("sim"), referencia = citando(texto("vamos?")))

        val separados = separarConteudosDaCitacao(m)

        assertThat(separados.daCitacao.map { it.conteudo }).containsExactly("vamos?")
        assertThat(separados.proprios.map { it.conteudo }).containsExactly("sim")
    }

    @Test
    fun `encaminhada nao repete embaixo o que ja esta na citacao, so o acrescentado`() {
        val foto = conteudo(TipoConteudo.IMAGEM, "img-1", ordem = 1)
        val legenda = conteudo(TipoConteudo.TEXTO, "olha", ordem = 2)
        val comentario = conteudo(TipoConteudo.TEXTO, "que tal?", ordem = 3)
        val m = mensagem(foto, legenda, comentario, referencia = encaminhando(foto, legenda))

        val separados = separarConteudosDaCitacao(m)

        assertThat(separados.daCitacao.map { it.conteudo }).containsExactly("img-1", "olha").inOrder()
        assertThat(separados.proprios.map { it.conteudo }).containsExactly("que tal?")
    }

    @Test
    fun `encaminhada de encaminhada - na citacao, a copia da citacao de baixo nao se repete (web 7322e83)`() {
        val foto = conteudo(TipoConteudo.IMAGEM, "img-1", ordem = 1)
        val nota = conteudo(TipoConteudo.TEXTO, "veja", ordem = 2)
        // A de baixo (original) tem a foto; a do meio a encaminhou e acrescentou "veja".
        val original = ReferenciaMensagem(TipoReferencia.ENCAMINHAMENTO, MensagemResumida(1, 7, "Bia", agora, null, listOf(foto), null))

        assertThat(semCopiasDaReferencia(listOf(foto, nota), original).map { it.conteudo }).containsExactly("veja")
        // Duas cópias iguais: só uma sai por conteúdo da citação.
        assertThat(semCopiasDaReferencia(listOf(foto, foto), original).map { it.conteudo }).containsExactly("img-1")
        // Resposta não copia nada: tudo fica.
        assertThat(semCopiasDaReferencia(listOf(foto), original.copy(tipo = TipoReferencia.RESPOSTA))).hasSize(1)
    }

    @Test
    fun `encaminhada com a citada sem conteudo usa os proprios na citacao`() {
        val m = mensagem(texto("repasse"), referencia = encaminhando())

        val separados = separarConteudosDaCitacao(m)

        assertThat(separados.daCitacao.map { it.conteudo }).containsExactly("repasse")
        assertThat(separados.proprios).isEmpty()
    }

    @Test
    fun `sem citacao tudo e proprio`() {
        val separados = separarConteudosDaCitacao(mensagem(texto("oi")))

        assertThat(separados.daCitacao).isEmpty()
        assertThat(separados.proprios.map { it.conteudo }).containsExactly("oi")
    }

    // --- classificarMensagem (mesmos casos do web) ---

    @Test
    fun `oculta vem antes de qualquer outro tipo`() {
        listOf(conteudo(TipoConteudo.CHAMADA), conteudo(TipoConteudo.IMAGEM), texto("```ts\nx\n```"), texto("oi")).forEach {
            assertThat(classificarMensagem(mensagem(it, oculta = true))).isEqualTo(TipoExibicao.OCULTA)
        }
        assertThat(classificarMensagem(mensagem(texto("oi")))).isEqualTo(TipoExibicao.TEXTO_CURTO)
    }

    @Test
    fun `chamada, imagem, figurinha e enquete`() {
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.CHAMADA)))).isEqualTo(TipoExibicao.CHAMADA)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.IMAGEM)))).isEqualTo(TipoExibicao.IMAGEM)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.FIGURINHA, "basico/coracao")))).isEqualTo(TipoExibicao.FIGURINHA)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.ENQUETE, "5")))).isEqualTo(TipoExibicao.ENQUETE)
    }

    @Test
    fun `com referencia, figurinha e imagem viram bolha de referencia, mas enquete nao`() {
        val ref = citando(texto("oi"))
        assertThat(
            classificarMensagem(mensagem(conteudo(TipoConteudo.FIGURINHA, "a/b"), referencia = ref)),
        ).isEqualTo(TipoExibicao.COM_REFERENCIA)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.IMAGEM), referencia = ref))).isEqualTo(TipoExibicao.COM_REFERENCIA)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.ENQUETE, "5"), referencia = ref))).isEqualTo(TipoExibicao.ENQUETE)
        assertThat(classificarMensagem(mensagem(texto("concordo"), referencia = ref))).isEqualTo(TipoExibicao.COM_REFERENCIA)
    }

    @Test
    fun `codigo sozinho e codigo com texto em volta`() {
        assertThat(classificarMensagem(mensagem(texto("```js\nconst a = 1\n```")))).isEqualTo(TipoExibicao.CODIGO)
        assertThat(classificarMensagem(mensagem(texto("veja:\n```js\nconst a = 1\n```")))).isEqualTo(TipoExibicao.PADRAO)
    }

    @Test
    fun `so emojis`() {
        assertThat(classificarMensagem(mensagem(texto("😀 👍")))).isEqualTo(TipoExibicao.EMOJI)
        assertThat(ehSoEmoji("👍🏽")).isTrue()
        assertThat(ehSoEmoji("👨‍👩‍👧")).isTrue()
        assertThat(ehSoEmoji("❤️")).isTrue()
        assertThat(ehSoEmoji("🇧🇷")).isTrue()
        assertThat(ehSoEmoji("oi 😀")).isFalse()
        assertThat(ehSoEmoji("123")).isFalse()
        assertThat(ehSoEmoji("   ")).isFalse()
    }

    @Test
    fun `texto curto ate 60, 61 ou quebra de linha e padrao, imagem com texto e padrao`() {
        assertThat(classificarMensagem(mensagem(texto("a".repeat(60))))).isEqualTo(TipoExibicao.TEXTO_CURTO)
        assertThat(classificarMensagem(mensagem(texto("a".repeat(61))))).isEqualTo(TipoExibicao.PADRAO)
        assertThat(classificarMensagem(mensagem(texto("oi\ntudo bem?")))).isEqualTo(TipoExibicao.PADRAO)
        assertThat(classificarMensagem(mensagem(conteudo(TipoConteudo.IMAGEM), conteudo(TipoConteudo.TEXTO, "legenda", 2))))
            .isEqualTo(TipoExibicao.PADRAO)
    }

    // --- links e menções ---

    @Test
    fun `links com pontuacao no fim e parenteses do proprio link`() {
        assertThat(separarLinks("veja https://x.com/a, depois")).containsExactly(
            SegmentoTexto.Texto("veja "),
            SegmentoTexto.Link("https://x.com/a"),
            SegmentoTexto.Texto(", depois"),
        ).inOrder()
        assertThat(separarLinks("(https://pt.wikipedia.org/wiki/Java_(linguagem))")).containsExactly(
            SegmentoTexto.Texto("("),
            SegmentoTexto.Link("https://pt.wikipedia.org/wiki/Java_(linguagem)"),
            SegmentoTexto.Texto(")"),
        ).inOrder()
        val www = separarLinks("site www.exemplo.com.").filterIsInstance<SegmentoTexto.Link>().single()
        assertThat(www.url).isEqualTo("www.exemplo.com")
        assertThat(www.destino).isEqualTo("https://www.exemplo.com")
    }

    @Test
    fun `mencao e link no mesmo texto`() {
        assertThat(separarTexto("oi @[Ana Souza](7), olha http://a.b")).containsExactly(
            SegmentoTexto.Texto("oi "),
            SegmentoTexto.Mencao("Ana Souza", 7),
            SegmentoTexto.Texto(", olha "),
            SegmentoTexto.Link("http://a.b"),
        ).inOrder()
    }

    // --- status ---

    @Test
    fun `status de entrega`() {
        assertThat(statusEntrega(mensagem(texto("a"), id = -3))).isEqualTo(StatusEntrega.ENVIANDO)
        assertThat(statusEntrega(mensagem(texto("a"), id = -3).copy(falhou = true))).isEqualTo(StatusEntrega.FALHOU)
        assertThat(statusEntrega(mensagem(texto("a")))).isEqualTo(StatusEntrega.ENVIADA)
        assertThat(statusEntrega(mensagem(texto("a"), recebida = true))).isEqualTo(StatusEntrega.ENTREGUE)
        assertThat(statusEntrega(mensagem(texto("a"), recebida = true, visualizada = true))).isEqualTo(StatusEntrega.LIDA)
    }

    // --- digitando ---

    @Test
    fun `quem esta digitando ou gravando`() {
        val nomes = mapOf(8L to "Ana", 9L to "Beto", 10L to "Caio", 11L to "Davi")
        assertThat(atividadeDaConversa(false, listOf(8L), emptyList(), nomes, eu = 7)).isEqualTo(AtividadeConversa.Direta(false))
        assertThat(atividadeDaConversa(true, listOf(7L), emptyList(), nomes, eu = 7)).isEqualTo(AtividadeConversa.Nenhuma)
        assertThat(atividadeDaConversa(true, listOf(8L, 9L), listOf(10L), nomes, eu = 7))
            .isEqualTo(AtividadeConversa.Grupo(gravando = true, nomes = listOf("Caio"), outros = 0))
        assertThat(atividadeDaConversa(true, listOf(8L, 9L, 10L, 11L, 12L), emptyList(), nomes, eu = 7))
            .isEqualTo(AtividadeConversa.Grupo(false, listOf("Ana", "Beto", "Caio"), outros = 2))
        assertThat((atividadeDaConversa(true, listOf(99L), emptyList(), nomes, eu = 7) as AtividadeConversa.Grupo).nomes)
            .containsExactly("Usuário #99")
    }

    // --- itens do chat ---

    @Test
    fun `ordem, separador de dia, Ultimas e nome do remetente no grupo`() {
        val zona = ZoneId.of("UTC")
        val ontem = Instant.parse("2026-10-06T10:00:00Z")
        val lista = listOf(
            mensagem(texto("c"), id = 12, remetente = 8, inserida = agora),
            mensagem(texto("a"), id = 10, remetente = 8, inserida = ontem),
            mensagem(texto("enviando"), id = -1, remetente = 7, inserida = Instant.parse("2026-10-07T14:00:00Z")),
            mensagem(texto("b"), id = 11, remetente = 9, inserida = agora),
            mensagem(texto("d"), id = 13, remetente = 9, inserida = agora),
        )
        val itens = montarItensChat(lista, primeiraNaoLida = 11, grupo = true, eu = 7, zona = zona)
        val resumo = itens.map {
            when (it) {
                is ItemChat.Dia -> "dia ${it.data}"
                ItemChat.NaoLidas -> "ultimas"
                is ItemChat.Bolha -> "${it.mensagem.id}${if (it.mostrarRemetente) "*" else ""}"
            }
        }
        // 11 e 12 têm a mesma data: desempata pelo id. A otimista (-1) vai para o fim.
        assertThat(resumo).containsExactly("dia 2026-10-06", "10*", "dia 2026-10-07", "ultimas", "11*", "12*", "13*", "-1").inOrder()
    }

    @Test
    fun `rotulo do dia`() {
        val zona = ZoneId.of("UTC")
        assertThat(rotuloDia(LocalDate.parse("2026-10-07"), agora, zona)).isEqualTo(RotuloDia.Hoje)
        assertThat(rotuloDia(LocalDate.parse("2026-10-06"), agora, zona)).isEqualTo(RotuloDia.Ontem)
        assertThat(rotuloDia(LocalDate.parse("2026-10-01"), agora, zona)).isEqualTo(RotuloDia.Data(LocalDate.parse("2026-10-01")))
    }

    // --- citação e duração ---

    @Test
    fun `resumo da citacao`() {
        fun resumo(vararg c: Conteudo, oculta: Boolean = false) = resumoCitacao(citando(*c, oculta = oculta).mensagem!!)
        assertThat(resumo(texto("oi @[Ana](7)"))).isEqualTo(ResumoCitacao.Texto("oi @Ana"))
        assertThat(resumo(texto("oi"), oculta = true)).isEqualTo(ResumoCitacao.Oculta)
        assertThat(resumo(conteudo(TipoConteudo.IMAGEM))).isEqualTo(ResumoCitacao.Tipo(TipoConteudo.IMAGEM))
        assertThat(resumo(conteudo(TipoConteudo.GRAVACAO_AUDIO))).isEqualTo(ResumoCitacao.Tipo(TipoConteudo.GRAVACAO_AUDIO))
        assertThat(resumo(conteudo(TipoConteudo.ARQUIVO))).isEqualTo(ResumoCitacao.Tipo(TipoConteudo.ARQUIVO))
    }

    @Test
    fun `duracao mm ss`() {
        assertThat(formatarDuracao(null)).isEqualTo("--:--")
        assertThat(formatarDuracao(5)).isEqualTo("00:05")
        assertThat(formatarDuracao(3_725)).isEqualTo("62:05")
        assertThat(formatarDuracao(-3)).isEqualTo("00:00")
    }

    // --- anexos ---

    @Test
    fun `tipo pelo mime, video e tamanho`() {
        assertThat(tipoPorMime("image/jpeg")).isEqualTo(TipoConteudo.IMAGEM)
        assertThat(tipoPorMime("audio/ogg")).isEqualTo(TipoConteudo.AUDIO)
        assertThat(tipoPorMime("video/mp4")).isEqualTo(TipoConteudo.ARQUIVO)
        assertThat(tipoPorMime("application/pdf")).isEqualTo(TipoConteudo.ARQUIVO)
        assertThat(tipoPorMime(null)).isEqualTo(TipoConteudo.ARQUIVO)
        assertThat(ehVideo(Conteudo(1, 1, TipoConteudo.ARQUIVO, "x", "filme.MP4", ""))).isTrue()
        assertThat(ehVideo(Conteudo(1, 1, TipoConteudo.ARQUIVO, "x", "a", ".mov"))).isTrue()
        assertThat(ehVideo(Conteudo(1, 1, TipoConteudo.ARQUIVO, "x", "a.pdf", "pdf"))).isFalse()
        assertThat(formatarTamanho(900)).isEqualTo("900 B")
        assertThat(formatarTamanho(1536)).isEqualTo("1.5 KB")
        assertThat(formatarTamanho(200L * 1024 * 1024)).isEqualTo("200.0 MB")
        assertThat(Conteudo(null, 1, TipoConteudo.IMAGEM, "local:content://x/1").local).isTrue()
    }
}
