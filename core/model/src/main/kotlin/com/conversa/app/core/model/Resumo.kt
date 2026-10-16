package com.conversa.app.core.model

import java.time.Instant

// Resumo da conversa por IA (CON-14; servidor `b8ea0c2`, contrato §13.5; web `ResumoConversaModal.vue`).

/** Os períodos do web, na ordem das pílulas; [codigo] é o que vai ao servidor. */
enum class PeriodoResumo(val codigo: String) {
    HORAS_24("24h"),
    DIAS_7("7d"),
    DIAS_30("30d"),

    /** "Últimas 400 mensagens": sem limite de data. */
    RECENTES("recentes"),
    ;

    companion object {
        fun de(codigo: String): PeriodoResumo = entries.firstOrNull { it.codigo == codigo } ?: HORAS_24
    }
}

enum class StatusResumo(val codigo: String) {
    PROCESSANDO("processando"),
    CONCLUIDO("concluido"),
    ERRO("erro"),
    ;

    companion object {
        fun de(codigo: String): StatusResumo = entries.firstOrNull { it.codigo == codigo } ?: PROCESSANDO
    }
}

/** Um assunto: o que se disse, o que ficou pendente e as mensagens dele (ids, em ordem crescente). */
data class AssuntoResumo(val titulo: String, val resumo: String, val pendencias: List<String>, val mensagens: List<Long>)

/** [mensagens] = quantas foram mandadas à IA; [erro] preenchido com o status [StatusResumo.ERRO]. */
data class ResumoConversa(
    val id: String,
    val conversaId: Long,
    val periodo: PeriodoResumo,
    val status: StatusResumo,
    val mensagens: Int,
    val assuntos: List<AssuntoResumo>,
    val erro: String,
)

/** O último resumo da conversa guardado no aparelho, com quando foi pedido (como o IndexedDB do web). */
data class ResumoSalvo(val resumo: ResumoConversa, val geradoEm: Instant)
