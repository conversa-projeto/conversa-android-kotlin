package com.conversa.app.core.model

import kotlin.math.roundToInt

/**
 * Duração e onda de um áudio, medidas no envio e gravadas no servidor (ANX-10, servidor
 * `ef13c22`; web `utils/dadosAudio.ts`): o player mostra a duração e a onda sem baixar o
 * arquivo. [duracao] em segundos (2 casas); [intensidade] com picos de 0 a 100.
 */
data class DadosAudio(val duracao: Double, val intensidade: List<Int>)

/** Barras da onda (o web usa 48; o servidor aceita até 200). */
const val BARRAS_ONDA = 48

/** Duração máxima que o servidor aceita (24 h). */
const val DURACAO_MAXIMA_AUDIO = 86_400.0

/** Segundos com 2 casas, como o web. */
fun arredondarDuracao(segundos: Double): Double = (segundos * 100).roundToInt() / 100.0

/**
 * O maior valor de cada trecho, de 0 a 100 relativo ao trecho mais alto (o `picos` do web).
 * [amostras] são valores absolutos ao longo do áudio (no app, o máximo de cada janela curta).
 */
fun picos(amostras: FloatArray, barras: Int = BARRAS_ONDA): List<Int> {
    val tamanho = maxOf(1, amostras.size / barras)
    val maximos = FloatArray(barras) { barra ->
        var maximo = 0f
        val fim = minOf(amostras.size, (barra + 1) * tamanho)
        for (i in barra * tamanho until fim) if (amostras[i] > maximo) maximo = amostras[i]
        maximo
    }
    val topo = maximos.maxOrNull() ?: 0f
    return maximos.map { if (topo > 0f) (it / topo * 100).roundToInt() else 0 }
}

/** A intensidade no Room, como texto ("12,40,…"). */
fun intensidadeParaTexto(intensidade: List<Int>): String = intensidade.joinToString(",")

/** O contrário de [intensidadeParaTexto]; vazio ou ilegível vira nulo. */
fun intensidadeDoTexto(texto: String?): List<Int>? =
    texto?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }

/** O texto do tempo no player: "atual / total" tocando ou parado no meio, senão o total (como o web). */
fun textoDoTempoDoAudio(posicaoSegundos: Long?, totalSegundos: Long?): String = when {
    posicaoSegundos == null -> formatarDuracao(totalSegundos)
    totalSegundos == null -> formatarDuracao(posicaoSegundos)
    else -> "${formatarDuracao(posicaoSegundos)} / ${formatarDuracao(totalSegundos)}"
}
