package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Regras puras da tela de chat (etapa 3), portadas do web para dar o mesmo resultado.

// --- Classificação da bolha (MSG-07, conversa-web src/utils/classificarMensagem.ts) ---

/** Que bolha desenhar. A ordem de [classificarMensagem] é a prioridade. */
enum class TipoExibicao {
    OCULTA,
    CHAMADA,
    IMAGEM,
    ENQUETE,
    FIGURINHA,
    CODIGO,
    EMOJI,
    COM_REFERENCIA,
    TEXTO_CURTO,
    PADRAO,
}

/** Limite da bolha curta (uma linha, hora ao lado): igual ao web. */
const val LIMITE_TEXTO_CURTO = 60

fun classificarMensagem(mensagem: Mensagem): TipoExibicao {
    val unico = mensagem.conteudos.singleOrNull()
    val temReferencia = mensagem.referencia != null
    val textoUnico = unico?.takeIf { it.tipo == TipoConteudo.TEXTO && !temReferencia }?.conteudo
    return when {
        // Oculta vem antes de tudo: o conteúdo só aparece se a pessoa pedir.
        mensagem.oculta -> TipoExibicao.OCULTA
        unico?.tipo == TipoConteudo.CHAMADA -> TipoExibicao.CHAMADA
        unico?.tipo == TipoConteudo.IMAGEM && !temReferencia -> TipoExibicao.IMAGEM
        // Votação mesmo com referência (só o servidor cria e não se encaminha).
        unico?.tipo == TipoConteudo.ENQUETE -> TipoExibicao.ENQUETE
        unico?.tipo == TipoConteudo.FIGURINHA && !temReferencia -> TipoExibicao.FIGURINHA
        textoUnico != null && soCodigo(textoUnico) -> TipoExibicao.CODIGO
        textoUnico != null && ehSoEmoji(textoUnico) -> TipoExibicao.EMOJI
        mensagem.referencia?.mensagem != null -> TipoExibicao.COM_REFERENCIA
        textoUnico != null && '\n' !in textoUnico && textoUnico.length <= LIMITE_TEXTO_CURTO -> TipoExibicao.TEXTO_CURTO
        else -> TipoExibicao.PADRAO
    }
}

private fun soCodigo(texto: String): Boolean = temBlocoDeCodigo(texto) &&
    separarBlocosDeCodigo(texto).filterIsInstance<SegmentoCodigo.Texto>().joinToString("") { it.conteudo }.isBlank()

/**
 * Mensagem só de emojis (MSG-14): fonte grande, sem fundo. O web usa as propriedades
 * Unicode `Extended_Pictographic`/`Emoji_Presentation`; aqui usamos as faixas de
 * código equivalentes, porque o regex do Android (ICU) e o da JVM dos testes não
 * aceitam a mesma sintaxe para essas propriedades.
 */
fun ehSoEmoji(texto: String): Boolean {
    val valor = texto.trim()
    if (valor.isEmpty()) return false
    var temEmoji = false
    var i = 0
    while (i < valor.length) {
        val cp = valor.codePointAt(i)
        when {
            ehPictografico(cp) -> temEmoji = true
            cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF || Character.isWhitespace(cp) -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return temEmoji
}

private fun ehPictografico(cp: Int): Boolean = cp in 0x1F000..0x1FAFF ||
    cp in 0x2600..0x27BF ||
    cp in 0x2300..0x23FF ||
    cp in 0x2B00..0x2BFF ||
    cp in 0x2190..0x21FF ||
    cp in 0x25A0..0x25FF ||
    cp in 0x2900..0x297F ||
    cp in 0x1FC00..0x1FFFD ||
    cp == 0x00A9 ||
    cp == 0x00AE ||
    cp == 0x203C ||
    cp == 0x2049 ||
    cp == 0x2122 ||
    cp == 0x2139 ||
    cp == 0x24C2 ||
    cp == 0x3030 ||
    cp == 0x303D ||
    cp == 0x3297 ||
    cp == 0x3299

// --- Texto da bolha: menções e links (conversa-web src/utils/formatters.ts) ---

sealed interface SegmentoTexto {
    data class Texto(val conteudo: String) : SegmentoTexto

    /** [url] é o texto como aparece; [destino] já com `https://` quando começa por `www.`. */
    data class Link(val url: String) : SegmentoTexto {
        val destino: String get() = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    data class Mencao(val nome: String, val usuarioId: Long) : SegmentoTexto
}

private val REGEX_MENCAO_SEGMENTO = Regex("""@\[([^\]]+)\]\((\d+)\)""")

// Vírgula, ponto e vírgula, aspas simples e parênteses fazem parte do link; só saem
// quando ficam no fim dele (veja removerPontuacaoFinal).
private val REGEX_LINKS = Regex("""(https?://[^\s<>"]+|www\.[^\s<>"]+)""")

/** Pontuação do texto logo depois do link. O ")" só sai se não fechar um "(" do próprio link. */
private fun removerPontuacaoFinal(original: String): String {
    var url = original
    while (url.length > 1) {
        val ultimo = url.last()
        url = when {
            ultimo in ".,;:!?'" -> url.dropLast(1)
            ultimo == ')' && url.count { it == ')' } > url.count { it == '(' } -> url.dropLast(1)
            else -> return url
        }
    }
    return url
}

fun separarLinks(texto: String): List<SegmentoTexto> {
    val segmentos = mutableListOf<SegmentoTexto>()
    var ultimo = 0
    var busca = 0
    while (true) {
        val achado = REGEX_LINKS.find(texto, busca) ?: break
        if (achado.range.first > ultimo) segmentos += SegmentoTexto.Texto(texto.substring(ultimo, achado.range.first))
        val url = removerPontuacaoFinal(achado.value)
        segmentos += SegmentoTexto.Link(url)
        ultimo = achado.range.first + url.length
        busca = ultimo
    }
    if (ultimo < texto.length) segmentos += SegmentoTexto.Texto(texto.substring(ultimo))
    return segmentos
}

/** Texto da bolha: menções `@[Nome](id)` e links, na ordem (`parseTextSegments` do web). */
fun separarTexto(texto: String): List<SegmentoTexto> {
    val resultado = mutableListOf<SegmentoTexto>()
    var ultimo = 0
    for (mencao in REGEX_MENCAO_SEGMENTO.findAll(texto)) {
        if (mencao.range.first > ultimo) resultado += separarLinks(texto.substring(ultimo, mencao.range.first))
        resultado += SegmentoTexto.Mencao(mencao.groupValues[1], mencao.groupValues[2].toLong())
        ultimo = mencao.range.last + 1
    }
    if (ultimo < texto.length) resultado += separarLinks(texto.substring(ultimo))
    return resultado
}

// --- Status de entrega (MSG-08) ---

enum class StatusEntrega { ENVIANDO, FALHOU, ENVIADA, ENTREGUE, LIDA }

fun statusEntrega(mensagem: Mensagem): StatusEntrega = when {
    mensagem.falhou -> StatusEntrega.FALHOU
    mensagem.enviando || mensagem.id < 0 -> StatusEntrega.ENVIANDO
    mensagem.visualizada -> StatusEntrega.LIDA
    mensagem.recebida -> StatusEntrega.ENTREGUE
    else -> StatusEntrega.ENVIADA
}

// --- Quem está digitando/gravando (ENV-15) ---

/** O que mostrar no cabeçalho/campo. Os textos ficam no strings.xml. */
sealed interface AtividadeConversa {
    data object Nenhuma : AtividadeConversa

    /** Conversa direta: só "Digitando…"/"Gravando áudio…". */
    data class Direta(val gravando: Boolean) : AtividadeConversa

    /** Grupo: até três nomes e quantos sobram. */
    data class Grupo(val gravando: Boolean, val nomes: List<String>, val outros: Int) : AtividadeConversa
}

/** Gravando tem prioridade sobre digitando (como no web). Quem não é contato vira "Usuário #id". */
fun atividadeDaConversa(
    grupo: Boolean,
    digitando: Collection<Long>,
    gravando: Collection<Long>,
    nomes: Map<Long, String>,
    eu: Long,
): AtividadeConversa {
    val quemGrava = gravando.filter { it != eu }
    val quem = quemGrava.ifEmpty { digitando.filter { it != eu } }
    if (quem.isEmpty()) return AtividadeConversa.Nenhuma
    val ehGravacao = quemGrava.isNotEmpty()
    if (!grupo) return AtividadeConversa.Direta(ehGravacao)
    val nomesOrdenados = quem.map { nomes[it] ?: "Usuário #$it" }
    return if (nomesOrdenados.size <= 3) {
        AtividadeConversa.Grupo(ehGravacao, nomesOrdenados, 0)
    } else {
        AtividadeConversa.Grupo(ehGravacao, nomesOrdenados.take(3), nomesOrdenados.size - 3)
    }
}

// --- Itens da lista do chat: separadores de dia e "Últimas" (MSG-02, MSG-03) ---

sealed interface ItemChat {
    val chave: String

    data class Dia(val data: LocalDate) : ItemChat {
        override val chave = "dia-$data"
    }

    /** Linha "Últimas" antes da primeira não lida. */
    data object NaoLidas : ItemChat {
        override val chave = "nao-lidas"
    }

    /** [mostrarRemetente]: em grupo, mensagem de outra pessoa quando muda o remetente. */
    data class Bolha(val mensagem: Mensagem, val mostrarRemetente: Boolean) : ItemChat {
        override val chave = "m-${mensagem.id}"
    }
}

/** Ordem do chat: data efetiva, depois id; as que ainda estão saindo (id negativo) no fim. */
fun ordenarMensagens(mensagens: List<Mensagem>): List<Mensagem> =
    mensagens.sortedWith(compareBy<Mensagem> { it.id < 0 }.thenBy { it.dataEfetiva }.thenBy { if (it.id < 0) -it.id else it.id })

/**
 * Monta a lista na ordem cronológica, com o separador de dia (pela data efetiva) e a
 * linha "Últimas" antes de [primeiraNaoLida]. Diferente do web, o dia usa a data
 * efetiva (`visivel_em`), a mesma da ordenação.
 */
fun montarItensChat(mensagens: List<Mensagem>, primeiraNaoLida: Long?, grupo: Boolean, eu: Long, zona: ZoneId): List<ItemChat> {
    val itens = mutableListOf<ItemChat>()
    var diaAtual: LocalDate? = null
    var ultimoRemetente: Long? = null
    for (mensagem in ordenarMensagens(mensagens)) {
        val dia = mensagem.dataEfetiva.atZone(zona).toLocalDate()
        if (dia != diaAtual) {
            diaAtual = dia
            ultimoRemetente = null
            itens += ItemChat.Dia(dia)
        }
        if (mensagem.id == primeiraNaoLida) itens += ItemChat.NaoLidas
        val mostrar = grupo && mensagem.remetenteId != eu && mensagem.remetenteId != ultimoRemetente
        ultimoRemetente = mensagem.remetenteId
        itens += ItemChat.Bolha(mensagem, mostrar)
    }
    return itens
}

/** Rótulo do separador: "Hoje", "Ontem" ou a data (a tela formata). */
sealed interface RotuloDia {
    data object Hoje : RotuloDia

    data object Ontem : RotuloDia

    data class Data(val data: LocalDate) : RotuloDia
}

fun rotuloDia(dia: LocalDate, agora: Instant, zona: ZoneId): RotuloDia {
    val hoje = LocalDate.ofInstant(agora, zona)
    return when (dia) {
        hoje -> RotuloDia.Hoje
        hoje.minusDays(1) -> RotuloDia.Ontem
        else -> RotuloDia.Data(dia)
    }
}

// --- Citação (resposta/encaminhada) e chamada ---

/** O que a citação mostra (MSG-06). Os textos fixos ficam no strings.xml. */
sealed interface ResumoCitacao {
    data object Oculta : ResumoCitacao

    data class Texto(val texto: String) : ResumoCitacao

    data class Tipo(val tipo: TipoConteudo) : ResumoCitacao
}

/** Mesma escolha do web (`resumoConteudos`): texto, senão imagem, gravação, áudio, figurinha, votação, arquivo. */
fun resumoCitacao(citada: MensagemResumida): ResumoCitacao {
    if (citada.oculta) return ResumoCitacao.Oculta
    citada.conteudos.firstOrNull { it.tipo == TipoConteudo.TEXTO && it.conteudo.isNotEmpty() }
        ?.let { return ResumoCitacao.Texto(resumirTexto(it.conteudo)) }
    val ordem = listOf(TipoConteudo.IMAGEM, TipoConteudo.GRAVACAO_AUDIO, TipoConteudo.AUDIO, TipoConteudo.FIGURINHA, TipoConteudo.ENQUETE)
    val tipo = ordem.firstOrNull { t -> citada.conteudos.any { it.tipo == t } } ?: TipoConteudo.ARQUIVO
    return ResumoCitacao.Tipo(tipo)
}

/** Duração `mm:ss` (minutos podem passar de 59, como no web). Nulo → `--:--`. */
fun formatarDuracao(segundos: Long?): String {
    if (segundos == null) return "--:--"
    val total = maxOf(0, segundos)
    return "%02d:%02d".format(total / 60, total % 60)
}

/** Conteúdo tipo 6 já lido (contrato §9.8): a bolha de chamada (MSG-13). */
data class ChamadaNaMensagem(
    val chamadaId: Long,
    val tipo: TipoChamada,
    /** 2 recusada, 4 encerrada, 5 perdida, 6 cancelada… (contrato §9.1). */
    val status: Int,
    val duracaoSegundos: Long?,
    val participantes: List<ParticipanteNaMensagem>,
) {
    /** Mais de 2 participantes = "em grupo". */
    val emGrupo: Boolean get() = participantes.size > 2
    val falhou: Boolean get() = status == 2 || status == 5
    val encerrada: Boolean get() = status == 4
}

data class ParticipanteNaMensagem(val usuarioId: Long, val nome: String, val status: Int, val duracaoSegundos: Long?)

// --- Anexos (ANX-01, ANX-03) ---

/**
 * Tipo do conteúdo pelo MIME (como o web): `image/` → imagem (2), `audio/` → áudio (4),
 * o resto → arquivo (3), inclusive vídeo (o servidor não tem tipo de vídeo, §8.1).
 * A gravação do microfone é sempre 5 e não passa por aqui.
 */
fun tipoPorMime(mime: String?): TipoConteudo = when {
    mime == null -> TipoConteudo.ARQUIVO
    mime.startsWith("image/") -> TipoConteudo.IMAGEM
    mime.startsWith("audio/") -> TipoConteudo.AUDIO
    else -> TipoConteudo.ARQUIVO
}

private val EXTENSOES_VIDEO = setOf("mp4", "webm", "ogg", "mov", "m4v", "mkv")

/** Vídeo é enviado como arquivo (3); reconhecido pela extensão (como o `isVideoConteudo` do web). */
fun ehVideo(conteudo: Conteudo): Boolean {
    val extensao = conteudo.extensao.ifBlank { conteudo.nome.substringAfterLast('.', "") }.trim().lowercase().removePrefix(".")
    return conteudo.tipo == TipoConteudo.ARQUIVO && extensao in EXTENSOES_VIDEO
}

/** Tamanho legível ("1.5 MB"), como o `formatarTamanho` do web. */
fun formatarTamanho(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var valor = bytes / 1024.0
    for (unidade in listOf("KB", "MB", "GB")) {
        if (valor < 1024 || unidade == "GB") return "%.1f %s".format(java.util.Locale.ROOT, valor, unidade)
        valor /= 1024
    }
    return "$bytes B"
}

/** Conteúdo de anexo ainda no aparelho (mensagem otimista): `conteudo = "local:<uri>"`. */
const val PREFIXO_LOCAL = "local:"

val Conteudo.local: Boolean get() = conteudo.startsWith(PREFIXO_LOCAL)
