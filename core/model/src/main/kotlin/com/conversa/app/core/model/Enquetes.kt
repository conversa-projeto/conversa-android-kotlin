package com.conversa.app.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// Votação em grupo (7.12, FC-516/517), como o `BolhaEnquete.vue`, o `EnqueteModal.vue` e o `prazoEnquete.ts`.

/** Até 12 opções; pergunta até 300 caracteres, opção até 200 (as regras do servidor). */
const val MAXIMO_OPCOES_ENQUETE = 12
const val MINIMO_OPCOES_ENQUETE = 2
const val LIMITE_PERGUNTA_ENQUETE = 300
const val LIMITE_OPCAO_ENQUETE = 200

/** O id da enquete de uma mensagem tipo 8 (o conteúdo é o id em texto). */
fun Mensagem.idDaEnquete(): Long? = conteudos.firstOrNull { it.tipo == TipoConteudo.ENQUETE }?.conteudo?.toLongOrNull()

/** Sobre quem votou, como no WhatsApp: na múltipla as barras somam mais de 100%. */
fun porcentagemDaOpcao(votos: Int, totalVotantes: Int): Int = if (totalVotantes <= 0) 0 else (votos * 100.0 / totalVotantes).roundToInt()

/** Escolha única: tocar em outra troca, tocar na marcada tira. Múltipla: marca e desmarca. Devolve a lista completa. */
fun Enquete.votosAoTocar(opcao: Long): List<Long> = when {
    opcao in meusVotos -> meusVotos - opcao
    multipla -> meusVotos + opcao
    else -> listOf(opcao)
}

/** Encerrada: as mais votadas (empates entram; ninguém votou = nenhuma) ganham 🏆. */
fun Enquete.vencedoras(agora: Instant): Set<Long> {
    if (!fechada(agora)) return emptySet()
    val maximo = opcoes.maxOfOrNull { it.votantes.size } ?: 0
    if (maximo == 0) return emptySet()
    return opcoes.filter { it.votantes.size == maximo }.mapTo(mutableSetOf()) { it.id }
}

/** Quando encerrou: à mão (`encerrada_em`) ou o prazo (`encerra_em`). */
val Enquete.momentoDoEncerramento: Instant? get() = encerradaEm ?: encerraEm

enum class ErroPrazo { SEM_DATA, NO_PASSADO, MUITO_LONGE }

/** As mesmas regras do servidor: pelo menos 1 minuto no futuro e no máximo 1 ano. */
fun validarPrazoEnquete(quando: LocalDateTime?, agora: Instant, zona: ZoneId = ZoneId.systemDefault()): ErroPrazo? {
    if (quando == null) return ErroPrazo.SEM_DATA
    val instante = quando.atZone(zona).toInstant()
    return when {
        instante < agora + Duration.ofMinutes(1) -> ErroPrazo.NO_PASSADO
        instante > agora.atZone(zona).plusYears(1).toInstant() -> ErroPrazo.MUITO_LONGE
        else -> null
    }
}

/** Sugestão de data final: amanhã, na próxima hora cheia. */
fun sugestaoPrazoEnquete(agora: Instant, zona: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(agora, zona).plusDays(1).plusHours(1).truncatedTo(ChronoUnit.HOURS)

/** As opções preenchidas, sem espaços nas pontas (só elas vão ao servidor). */
fun opcoesPreenchidas(opcoes: List<String>): List<String> = opcoes.map { it.trim() }.filter { it.isNotEmpty() }

/** "Criar votação" habilitado: pergunta, pelo menos 2 opções preenchidas e data final válida (se pedida). */
fun podeCriarEnquete(pergunta: String, opcoes: List<String>, erroPrazo: ErroPrazo?): Boolean =
    pergunta.isNotBlank() && opcoesPreenchidas(opcoes).size >= MINIMO_OPCOES_ENQUETE && erroPrazo == null
