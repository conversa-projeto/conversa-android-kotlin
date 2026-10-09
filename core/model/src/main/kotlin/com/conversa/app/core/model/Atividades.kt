package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Aba Atividades (8.1, FC-800, ATV-01/02), como o `AtividadesPage.vue`. Os textos ficam no strings.xml.

/** Itens por página de `GET /atividades` (menos que isso = chegou ao fim). */
const val ATIVIDADES_POR_PAGINA = 30

/** Cabeçalho de um grupo: Hoje, Ontem ou `dd/MM/aaaa`. */
sealed interface DiaAtividade {
    data object Hoje : DiaAtividade

    data object Ontem : DiaAtividade

    data class Data(val texto: String) : DiaAtividade
}

private val FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Agrupa em ordem (a lista já vem da mais nova para a mais antiga): um grupo por dia seguido. */
fun agruparAtividades(
    lista: List<Atividade>,
    agora: Instant,
    zona: ZoneId = ZoneId.systemDefault(),
): List<Pair<DiaAtividade, List<Atividade>>> {
    val hoje = LocalDate.ofInstant(agora, zona)
    val grupos = mutableListOf<Pair<DiaAtividade, MutableList<Atividade>>>()
    for (atividade in lista) {
        val dia = atividade.criadoEm?.let { LocalDate.ofInstant(it, zona) }
        val rotulo = when (dia) {
            hoje -> DiaAtividade.Hoje
            hoje.minusDays(1) -> DiaAtividade.Ontem
            null -> DiaAtividade.Hoje
            else -> DiaAtividade.Data(FORMATO_DIA.format(dia))
        }
        val ultimo = grupos.lastOrNull()
        if (ultimo?.first == rotulo) ultimo.second.add(atividade) else grupos.add(rotulo to mutableListOf(atividade))
    }
    return grupos
}

/** O selo no canto do avatar: o emoji da reação, "↩" (resposta), "@" (menção) ou "✆" (chamada perdida). */
fun Atividade.textoDoSelo(): String = when (tipo) {
    TipoAtividade.REACAO -> emoji?.takeIf { it.isNotBlank() } ?: "❤️"
    TipoAtividade.RESPOSTA -> "↩"
    TipoAtividade.MENCAO -> "@"
    TipoAtividade.CHAMADA_PERDIDA -> "✆"
    TipoAtividade.DESCONHECIDO -> "•"
}

/** "em <grupo>": só em grupo (na direta, o nome de quem fez já diz onde foi). */
fun Atividade.ondeFoi(): String? = conversaDescricao?.takeIf { conversaTipo == TipoConversa.GRUPO && it.isNotBlank() }

/** Prévia entre aspas: o texto (menções como "@Nome") ou o tipo do conteúdo; chamada perdida não tem. */
sealed interface PreviaAtividade {
    data class Texto(val texto: String) : PreviaAtividade

    data class Tipo(val tipo: TipoConteudo) : PreviaAtividade
}

private val TIPOS_COM_NOME = setOf(
    TipoConteudo.IMAGEM,
    TipoConteudo.ARQUIVO,
    TipoConteudo.AUDIO,
    TipoConteudo.GRAVACAO_AUDIO,
    TipoConteudo.FIGURINHA,
    TipoConteudo.ENQUETE,
)

fun Atividade.previa(): PreviaAtividade? {
    if (tipo == TipoAtividade.CHAMADA_PERDIDA) return null
    texto?.let(::resumirTexto)?.takeIf { it.isNotBlank() }?.let { return PreviaAtividade.Texto(it) }
    return conteudoTipo?.takeIf { it in TIPOS_COM_NOME }?.let { PreviaAtividade.Tipo(it) }
}
