package com.conversa.app.core.model

import java.time.Duration
import java.time.Instant

// Confirmação de leitura (MSG-21, ENV-24, ENV-27; web `MessageBubble.vue` e `MensagemAcoes.vue`).

/** O que quem enviou (ou quem não está na lista) vê na bolha. */
sealed interface ResumoConfirmacao {
    /** "Aguardando confirmação". */
    data object Aguardando : ResumoConfirmacao

    /** "Confirmada por N de T". */
    data class PorDe(val quantos: Int, val total: Int) : ResumoConfirmacao

    /** "Confirmada por N" (a recém-enviada ainda não tem o total do servidor). */
    data class Por(val quantos: Int) : ResumoConfirmacao
}

fun resumoConfirmacao(confirmacao: ConfirmacaoLeitura): ResumoConfirmacao {
    val quantos = confirmacao.usuarios.size
    return when {
        quantos == 0 -> ResumoConfirmacao.Aguardando
        confirmacao.total > 0 -> ResumoConfirmacao.PorDe(quantos, confirmacao.total)
        else -> ResumoConfirmacao.Por(quantos)
    }
}

/** Quem está na lista e ainda não confirmou ("Falta confirmar"). */
fun faltaConfirmar(confirmacao: ConfirmacaoLeitura): List<DestinatarioConfirmacao> =
    confirmacao.destinatarios.filter { destinatario -> confirmacao.usuarios.none { it.usuarioId == destinatario.usuarioId } }

/** Até quando quem enviou pode pedir a confirmação depois do envio (servidor `PRAZO_PEDIR_CONFIRMACAO_HORAS`). */
val PRAZO_PEDIR_CONFIRMACAO: Duration = Duration.ofHours(24)

/**
 * "Pedir confirmação de leitura" no menu (FC-525): minha, já salva e visível, não oculta, que ainda
 * não pede confirmação, até 24 h de `coalesce(visivel_em, inserida)`.
 */
fun podePedirConfirmacao(mensagem: Mensagem, eu: Long, agora: Instant): Boolean {
    if (mensagem.remetenteId != eu || mensagem.confirmacao != null || mensagem.oculta || mensagem.id <= 0) return false
    val enviada = mensagem.dataEfetiva
    return !enviada.isAfter(agora) && Duration.between(enviada, agora) < PRAZO_PEDIR_CONFIRMACAO
}

/**
 * O `confirmacao_usuarios` que vai ao servidor (CON-16). Sem a lista, o servidor pede a todos os
 * outros membros, inclusive aos bots, que nunca confirmam: o "N de T" não fecharia (S27). Com bot
 * no grupo, o "todos" ([escolhidos] nulo) vira a lista dos [outros] sem os [bots].
 */
fun listaDeConfirmacao(escolhidos: List<Long>?, outros: List<Long>, bots: Set<Long>): List<Long>? = when {
    escolhidos != null -> escolhidos.filterNot { it in bots }
    outros.any { it in bots } -> outros.filterNot { it in bots }
    else -> null
}

/** Membros do "Quem precisa confirmar" que batem com o filtro (sem acento nem maiúsculas). */
fun filtrarMembros(membros: List<MembroConversa>, termo: String): List<MembroConversa> {
    val busca = normalizarBusca(termo)
    return if (busca.isEmpty()) membros else membros.filter { normalizarBusca(it.nome).contains(busca) }
}
