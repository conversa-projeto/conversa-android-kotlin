package com.conversa.app.core.model

/*
 * Menções no campo de texto (ENV-05, TODO 7.7), portadas do `utils/mencoesTexto.ts` do web.
 * O campo mostra "@Nome" e guarda à parte o id de cada menção inserida; no envio o texto
 * volta ao formato "@[Nome](id)".
 */

/** Uma menção escolhida na lista: o nome que aparece no campo e o id do usuário. */
data class MencaoInserida(val nome: String, val id: Long)

/** Pedaço do texto do campo: texto comum ou uma menção inserida. */
data class TrechoCampo(val texto: String, val mencao: MencaoInserida? = null)

/** Quantos contatos a lista de menção mostra (como o web). */
const val MAXIMO_SUGESTOES_MENCAO = 6

/**
 * Divide o texto em trechos, marcando cada "@Nome" de uma menção inserida. O nome precisa
 * terminar ali: "@Ana" não marca o começo de "@Anabela" (os nomes maiores são tentados antes).
 */
fun dividirMencoes(texto: String, mencoes: List<MencaoInserida>): List<TrechoCampo> {
    val ordenadas = mencoes.sortedByDescending { it.nome.length }
    val trechos = mutableListOf<TrechoCampo>()
    var inicio = 0
    var i = 0
    while (i < texto.length) {
        val mencao = if (texto[i] == '@') {
            ordenadas.firstOrNull { m ->
                texto.startsWith("@${m.nome}", i) && texto.getOrNull(i + m.nome.length + 1)?.let { !continuaNome(it) } != false
            }
        } else {
            null
        }
        if (mencao == null) {
            i++
            continue
        }
        if (i > inicio) trechos += TrechoCampo(texto.substring(inicio, i))
        trechos += TrechoCampo("@${mencao.nome}", mencao)
        i += mencao.nome.length + 1
        inicio = i
    }
    if (inicio < texto.length) trechos += TrechoCampo(texto.substring(inicio))
    return trechos
}

/** Letra, número ou "_" depois do nome: ainda é parte de outra palavra (como o `[\p{L}\p{N}_]` do web). */
private fun continuaNome(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

/** O texto que vai para o servidor: cada "@Nome" inserido vira "@[Nome](id)". */
fun textoParaEnvio(texto: String, mencoes: List<MencaoInserida>): String {
    if (mencoes.isEmpty()) return texto
    return dividirMencoes(texto, mencoes).joinToString("") { t -> t.mencao?.let { "@[${it.nome}](${it.id})" } ?: t.texto }
}

/** Texto que já vem com "@[Nome](id)" (colado, compartilhado): mostra "@Nome" e devolve as menções. */
fun extrairMencoesCruas(texto: String): Pair<String, List<MencaoInserida>> {
    val mencoes = mutableListOf<MencaoInserida>()
    val saida = StringBuilder()
    var i = 0
    while (i < texto.length) {
        val crua = if (texto[i] == '@') lerMencaoCrua(texto, i) else null
        if (crua == null) {
            saida.append(texto[i])
            i++
        } else {
            mencoes += crua.first
            saida.append('@').append(crua.first.nome)
            i = crua.second
        }
    }
    return saida.toString() to mencoes
}

/** "@[Nome](123)" a partir de [inicio]: a menção e onde ela termina; nulo se não é uma. */
private fun lerMencaoCrua(texto: String, inicio: Int): Pair<MencaoInserida, Int>? {
    if (texto.getOrNull(inicio + 1) != '[') return null
    val fechaNome = texto.indexOf(']', inicio + 2)
    if (fechaNome <= inicio + 2 || texto.getOrNull(fechaNome + 1) != '(') return null
    val fechaId = texto.indexOf(')', fechaNome + 2)
    if (fechaId < 0) return null
    val id = texto.substring(fechaNome + 2, fechaId).takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull() ?: return null
    return MencaoInserida(texto.substring(inicio + 2, fechaNome), id) to fechaId + 1
}

/** O "@termo" sendo digitado antes do cursor: o termo e onde começa o "@". */
data class MencaoDigitada(val termo: String, val inicio: Int)

/**
 * Como o `/@([\w\s]*)$/` do web: volta do cursor enquanto houver letra ASCII, número, "_" ou
 * espaço; se parar num "@", é uma menção sendo digitada.
 */
fun mencaoDigitada(texto: String, cursor: Int): MencaoDigitada? {
    if (cursor <= 0 || cursor > texto.length) return null
    var i = cursor - 1
    while (i >= 0 && parteDoTermo(texto[i])) i--
    if (i < 0 || texto[i] != '@') return null
    return MencaoDigitada(texto.substring(i + 1, cursor), i)
}

private fun parteDoTermo(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c.isWhitespace()

/** Sugestões do web: o termo no nome ou no login, no máximo [MAXIMO_SUGESTOES_MENCAO]. */
fun sugestoesDeMencao(contatos: List<Contato>, termo: String): List<Contato> {
    val busca = termo.trim().lowercase()
    val lista = if (busca.isEmpty()) contatos else contatos.filter { busca in it.nome.lowercase() || busca in it.login.lowercase() }
    return lista.take(MAXIMO_SUGESTOES_MENCAO)
}
