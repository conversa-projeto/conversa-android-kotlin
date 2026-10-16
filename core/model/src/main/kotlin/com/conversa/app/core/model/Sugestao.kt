package com.conversa.app.core.model

// Sugestão de texto da IA ao digitar (ENV-25; servidor `ea02f57`, contrato §13.6; web `useSugestaoIa.ts`).

/** Pausa na digitação antes de pedir (como o web). */
const val ESPERA_SUGESTAO_MS = 600L

/** Caracteres antes do cursor (sem os espaços das pontas) para valer pedir. */
const val MINIMO_SUGESTAO = 3

/** O servidor recusa texto maior (400). */
const val MAXIMO_TEXTO_SUGESTAO = 4_000

/**
 * Pede só sem seleção, com o cursor no fim (depois dele, só espaço) e com pelo menos
 * [MINIMO_SUGESTAO] caracteres antes dele, como o web.
 */
fun podePedirSugestao(texto: String, inicioSelecao: Int, fimSelecao: Int): Boolean =
    inicioSelecao == fimSelecao &&
        fimSelecao in 0..texto.length &&
        texto.substring(fimSelecao).isBlank() &&
        texto.substring(0, fimSelecao).trim().length >= MINIMO_SUGESTAO

/** O que vai ao servidor: o texto até o cursor, cortado no limite (fica o fim, que é o que a IA continua). */
fun textoParaSugestao(
    texto: String,
    cursor: Int,
): String = texto.substring(0, cursor.coerceIn(0, texto.length)).takeLast(MAXIMO_TEXTO_SUGESTAO)
