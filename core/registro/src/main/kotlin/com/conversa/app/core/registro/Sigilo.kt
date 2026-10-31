package com.conversa.app.core.registro

import java.util.concurrent.ConcurrentHashMap

/**
 * O que nunca entra no registro. Quem registra já não passa token, senha nem texto de mensagem;
 * isto é a segunda barreira, aplicada a toda linha:
 * - os segredos em uso ([guardar]: o token da sessão, as credenciais do TURN) viram `***` onde aparecerem;
 * - `Bearer …` e valores de chaves sensíveis (`token=`, `"senha":`, `X-Amz-Signature=`…) também.
 */
object Sigilo {
    private val segredos = ConcurrentHashMap<String, String>()

    /** Segredos curtos demais ficam de fora: trocariam pedaços comuns do texto. */
    private const val TAMANHO_MINIMO = 6

    private val BEARER = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)

    private val CHAVE_SENSIVEL = Regex(
        "((?:token|senha|password|passwd|secret|segredo|signature|credential|authorization|apikey|api_key)" +
            "[A-Za-z_-]*[\"']?\\s*[=:]\\s*[\"']?)[^\\s&\"',;}]+",
        RegexOption.IGNORE_CASE,
    )

    /** Guarda (ou troca) o segredo [chave]; `null` esquece. */
    fun guardar(chave: String, valor: String?) {
        if (valor == null || valor.length < TAMANHO_MINIMO) segredos.remove(chave) else segredos[chave] = valor
    }

    fun esconder(texto: String): String {
        var resultado = texto
        for (segredo in segredos.values) resultado = resultado.replace(segredo, "***")
        resultado = BEARER.replace(resultado, "$1***")
        return CHAVE_SENSIVEL.replace(resultado, "$1***")
    }
}
