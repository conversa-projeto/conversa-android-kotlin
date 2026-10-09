package com.conversa.app.core.model

// Pesquisa de mensagens (8.2, FC-801, PES-01/02), como o `PesquisaAvancada.vue` e o `ChatHeader.vue`.

/** Onde o [termo] aparece no [texto], sem diferenciar maiúsculas (sem regex: o termo é texto livre). */
fun ocorrencias(texto: String, termo: String): List<IntRange> {
    val alvo = termo.trim()
    if (alvo.isEmpty()) return emptyList()
    val faixas = mutableListOf<IntRange>()
    var inicio = texto.indexOf(alvo, ignoreCase = true)
    while (inicio >= 0) {
        faixas += inicio until inicio + alvo.length
        inicio = texto.indexOf(alvo, inicio + alvo.length, ignoreCase = true)
    }
    return faixas
}

/**
 * Um pedaço do [texto] em volta da primeira ocorrência do [termo] ("…antes termo depois…"), para
 * o termo aparecer mesmo numa mensagem longa. Sem ocorrência, o começo do texto.
 */
fun trechoComTermo(texto: String, termo: String, contexto: Int = 40): String {
    val primeira =
        ocorrencias(texto, termo).firstOrNull() ?: return texto.take(contexto * 2).let { if (it.length < texto.length) "$it…" else it }
    val inicio = (primeira.first - contexto).coerceAtLeast(0)
    val fim = (primeira.last + 1 + contexto).coerceAtMost(texto.length)
    return buildString {
        if (inicio > 0) append("…")
        append(texto, inicio, fim)
        if (fim < texto.length) append("…")
    }
}

/** O texto da mensagem para a lista de resultados (menções como "@Nome"); nulo se não tem texto (a tela diz o tipo). */
fun textoParaPesquisa(mensagem: Mensagem): String? =
    mensagem.conteudos.firstOrNull { it.tipo == TipoConteudo.TEXTO }?.conteudo?.let(::resumirTexto)?.takeIf { it.isNotBlank() }

/** Sem texto: "Imagem", "Áudio" ou "Arquivo" (como o `resumoMensagem` do web). */
fun tipoParaPesquisa(mensagem: Mensagem): TipoConteudo = when {
    mensagem.conteudos.any { it.tipo == TipoConteudo.IMAGEM } -> TipoConteudo.IMAGEM
    mensagem.conteudos.any { it.tipo == TipoConteudo.AUDIO || it.tipo == TipoConteudo.GRAVACAO_AUDIO } -> TipoConteudo.AUDIO
    else -> TipoConteudo.ARQUIVO
}

/** Resultados da pesquisa global agrupados por conversa, na ordem em que cada uma aparece primeiro. */
fun agruparPorConversa(mensagens: List<Mensagem>): List<Pair<Long, List<Mensagem>>> =
    mensagens.groupBy { it.conversaId }.toList()
