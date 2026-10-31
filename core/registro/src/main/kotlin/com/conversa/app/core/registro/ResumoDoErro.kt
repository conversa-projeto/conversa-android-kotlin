package com.conversa.app.core.registro

/** Uma exceção que se descreve melhor no registro do que o nome e a mensagem (ex.: o status HTTP do `ErroApi`). */
interface DescricaoParaRegistro {
    fun paraRegistro(): String
}

private const val MAXIMO_CAUSAS = 4
private const val MAXIMO_MENSAGEM = 300

/**
 * O erro numa linha: `Classe: mensagem ← Causa: mensagem`, até [MAXIMO_CAUSAS] níveis. Para quem
 * quer a pilha inteira, `Timber.w(erro, …)` (o Timber junta a pilha na entrada).
 */
fun Throwable.resumoDoErro(): String {
    val partes = mutableListOf<String>()
    var atual: Throwable? = this
    while (atual != null && partes.size < MAXIMO_CAUSAS) {
        partes += (atual as? DescricaoParaRegistro)?.paraRegistro() ?: atual.nomeEMensagem()
        atual = atual.cause?.takeIf { it !== atual }
    }
    return partes.joinToString(" ← ")
}

private fun Throwable.nomeEMensagem(): String {
    val nome = javaClass.simpleName.ifEmpty { javaClass.name }
    val mensagem = message?.takeIf { it.isNotBlank() }?.replace('\n', ' ')?.take(MAXIMO_MENSAGEM)
    return if (mensagem == null) nome else "$nome: $mensagem"
}
