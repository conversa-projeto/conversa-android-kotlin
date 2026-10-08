package com.conversa.app.core.model

import java.time.Instant

/** Reações rápidas do menu da mensagem (as do web, em duas linhas: 4 + 3). */
val REACOES_RAPIDAS = listOf("👍", "❤️", "😂", "😮", "😢", "👏", "🔥")

/** O servidor recusa emoji com mais de 10 code points (contrato §10.12). */
const val MAXIMO_CODE_POINTS_EMOJI = 10

fun emojiAceito(emoji: String): Boolean = emoji.isNotBlank() && emoji.codePointCount(0, emoji.length) <= MAXIMO_CODE_POINTS_EMOJI

/** Emojis diferentes que cada pessoa pode deixar na mesma mensagem (servidor `d4435db`). */
const val LIMITE_REACOES_POR_PESSOA = 5

/** Chips à mostra embaixo da bolha; os demais ficam no "+N" (web `eaa8bac`). */
const val REACOES_A_MOSTRA = 5

/** Tirar é sempre possível; pôr um emoji novo, só abaixo do limite (o web confere antes da otimista). */
fun podeReagir(reacoes: List<Reacao>, emoji: String): Boolean {
    val minhas = reacoes.filter { it.reagiu }
    return minhas.any { it.emoji == emoji } || minhas.size < LIMITE_REACOES_POR_PESSOA
}

/**
 * Alterna a minha reação [emoji] (como o `PUT /mensagem/reacao`): se já reagi, tiro;
 * senão, ponho. Usado para mostrar na hora, antes da resposta do servidor (ENV-17).
 * Um mesmo usuário pode ter vários emojis na mesma mensagem.
 */
fun alternarReacao(reacoes: List<Reacao>, emoji: String, eu: Long, meuNome: String, agora: Instant): List<Reacao> {
    val existente = reacoes.firstOrNull { it.emoji == emoji }
    return when {
        existente == null -> reacoes + Reacao(emoji, 1, reagiu = true, usuarios = listOf(UsuarioReacao(eu, meuNome, agora, null)))
        existente.reagiu -> {
            val restante = existente.quantidade - 1
            if (restante <= 0) {
                reacoes - existente
            } else {
                reacoes.map {
                    if (it ===
                        existente
                    ) {
                        it.copy(quantidade = restante, reagiu = false, usuarios = it.usuarios.filter { u -> u.usuarioId != eu })
                    } else {
                        it
                    }
                }
            }
        }
        else -> reacoes.map {
            if (it === existente) {
                it.copy(quantidade = it.quantidade + 1, reagiu = true, usuarios = it.usuarios + UsuarioReacao(eu, meuNome, agora, null))
            } else {
                it
            }
        }
    }
}
