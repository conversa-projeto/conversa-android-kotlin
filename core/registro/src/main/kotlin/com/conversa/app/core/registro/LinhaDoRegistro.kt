package com.conversa.app.core.registro

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `2026-10-31 15:46:12.345 -03:00`: o fuso fica em cada linha (o aparelho pode trocar de fuso). */
private val FORMATO_MOMENTO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xxx", Locale.ROOT)

/** Onde fica a letra do nível: logo depois do momento e de um espaço. */
private const val POSICAO_NIVEL = 31

/** Letra do nível como no logcat: V, D, I, W, E, A (prioridades do `android.util.Log`, 2 a 7). */
fun letraDoNivel(prioridade: Int): Char = when (prioridade) {
    2 -> 'V'
    3 -> 'D'
    4 -> 'I'
    5 -> 'W'
    6 -> 'E'
    7 -> 'A'
    else -> '?'
}

/**
 * Uma entrada do registro: `momento N/Tag [thread] mensagem`. A mensagem passa pelo [Sigilo];
 * uma mensagem com várias linhas (pilha de um erro) continua nas linhas seguintes, como veio.
 */
fun linhaDoRegistro(momento: ZonedDateTime, prioridade: Int, tag: String?, thread: String, mensagem: String): String =
    "${FORMATO_MOMENTO.format(momento)} ${letraDoNivel(prioridade)}/${tag ?: "-"} [${nomeCurtoDaThread(thread)}] " +
        Sigilo.esconder(mensagem)

/** As threads do OkHttp levam o endereço no nome ("OkHttp https://…"); fica só o começo. */
internal fun nomeCurtoDaThread(nome: String): String = nome.substringBefore(' ').take(32)

/** Uma entrada lida do arquivo, para a tela do registro. [texto] é o que vem depois do nível. */
data class EntradaDoRegistro(val momento: String, val nivel: Char, val texto: String)

/** A linha começa uma entrada (e não é a continuação da pilha de um erro)? */
fun comecaEntrada(linha: String): Boolean = linha.length > POSICAO_NIVEL + 1 &&
    linha[4] == '-' &&
    linha[7] == '-' &&
    linha[10] == ' ' &&
    linha[13] == ':' &&
    linha[POSICAO_NIVEL + 1] == '/' &&
    linha.take(4).all(Char::isDigit)

/**
 * Agrupa as linhas em entradas: as de continuação vão para a entrada de cima. As que sobram no
 * começo (o corte pegou o meio de uma pilha) viram uma entrada sem momento.
 */
fun agruparEntradas(linhas: List<String>): List<EntradaDoRegistro> {
    val entradas = mutableListOf<EntradaDoRegistro>()
    for (linha in linhas) {
        if (comecaEntrada(linha)) {
            entradas += EntradaDoRegistro(
                momento = linha.substring(0, POSICAO_NIVEL - 1),
                nivel = linha[POSICAO_NIVEL],
                texto = linha.substring(POSICAO_NIVEL + 2),
            )
        } else {
            val ultima = entradas.removeLastOrNull()
            entradas += ultima?.copy(texto = ultima.texto + "\n" + linha) ?: EntradaDoRegistro("", '?', linha)
        }
    }
    return entradas
}
