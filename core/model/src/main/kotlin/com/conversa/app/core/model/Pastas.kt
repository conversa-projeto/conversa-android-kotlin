package com.conversa.app.core.model

import java.text.Collator
import java.util.Locale

// Pastas de mensagens da conversa direta (contrato §10.17; web `stores/pastas.ts`): a pasta só
// marca, a mensagem continua no chat e pode estar em várias. Cada pessoa tem as suas; a
// compartilhada aparece também para a outra pessoa, que põe e tira mensagens mas não a muda.

/** Uma pasta: [donoId] é quem criou (só ele renomeia, muda a cor, compartilha e exclui). */
data class Pasta(
    val id: Long,
    val conversaId: Long,
    val donoId: Long,
    val nome: String,
    /** "#rrggbb" ou "" (a cor principal do tema). */
    val cor: String,
    val compartilhada: Boolean,
    /** Quantas mensagens estão nela (conta também as ocultas). */
    val mensagens: Int,
)

/** O servidor aceita nomes de até 60 caracteres (depois do `trim`). */
const val LIMITE_NOME_PASTA = 60

/** Cada pessoa cria até 50 pastas por conversa. */
const val LIMITE_PASTAS_POR_CONVERSA = 50

/** `GET /pasta/mensagens` devolve 50 por página. */
const val MENSAGENS_POR_PAGINA_PASTA = 50

/** Pôr ou tirar: até 500 mensagens por chamada. */
const val LIMITE_MENSAGENS_POR_VEZ = 500

/** Na bolha, no máximo três marcas de pasta (como o web). */
const val MARCAS_DE_PASTA_NA_BOLHA = 3

/**
 * As cores para escolher, as mesmas do web (`CORES_PASTA`), para a pasta aparecer igual nos dois
 * clientes: "" é a cor principal do tema. Dado do servidor, não do tema (docs/design/cores.md §6.5).
 */
val CORES_PASTA = listOf("", "#ef4444", "#f59e0b", "#22c55e", "#06b6d4", "#3b82f6", "#a855f7", "#ec4899")

/** A cor da pasta em ARGB; nula sem cor ou fora de "#rrggbb" (aí vale a cor principal do tema). */
fun argbDaPasta(cor: String): Long? {
    if (cor.length != 7 || cor[0] != '#') return null
    val rgb = cor.substring(1).toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}

/** A ordem do servidor e do web: pelo nome, sem diferenciar maiúsculas nem acentos, e pelo id. */
fun ordenarPastas(pastas: List<Pasta>): List<Pasta> {
    val comparador = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }
    return pastas.sortedWith { a, b -> comparador.compare(a.nome, b.nome).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
}

/** As pastas de uma mensagem que a pessoa enxerga, na ordem dos ids, até [limite] (as marcas da bolha). */
fun pastasDaMensagem(ids: List<Long>, pastas: List<Pasta>, limite: Int = MARCAS_DE_PASTA_NA_BOLHA): List<Pasta> {
    val porId = pastas.associateBy { it.id }
    return ids.sorted().mapNotNull { porId[it] }.take(limite)
}

/** As pastas no Room: ids em ordem, separados por vírgula. */
fun pastasParaTexto(ids: List<Long>): String = ids.distinct().sorted().joinToString(",")

fun pastasDoTexto(texto: String): List<Long> = if (texto.isEmpty()) {
    emptyList()
} else {
    texto.split(',').mapNotNull {
        it.trim().toLongOrNull()
    }
}

/** As pastas de uma mensagem depois de pôr ([incluir]) ou tirar da pasta [pastaId], em ordem. */
fun pastasDepois(atuais: List<Long>, pastaId: Long, incluir: Boolean): List<Long> =
    if (incluir) (atuais + pastaId).distinct().sorted() else atuais.filter { it != pastaId }
