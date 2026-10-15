package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Presença (PRE-01…03; servidor `769bd7d`, web `IndicadorPresenca.vue` e `ChatHeader.vue`).

/** Ativo (verde), ausente (amarelo) ou offline (sem bolinha). */
enum class EstadoPresenca {
    ATIVO,
    AUSENTE,
    OFFLINE,
    ;

    companion object {
        /** O texto do servidor (`ativo`, `ausente`, `offline`); outro valor = nulo. */
        fun de(texto: String?): EstadoPresenca? = when (texto) {
            "ativo" -> ATIVO
            "ausente" -> AUSENTE
            "offline" -> OFFLINE
            else -> null
        }
    }
}

/** Como um contato aparece: o estado e o "visto por último" (só se a pessoa mostra). */
data class PresencaContato(val estado: EstadoPresenca, val vistoEm: Instant? = null)

/** Privacidade da presença (CFG-09): o padrão do servidor é mostrar tudo e não aparecer offline. */
data class Privacidade(val mostrarVistoEm: Boolean = true, val mostrarNaConversa: Boolean = true, val aparecerOffline: Boolean = false)

/** O estado de [usuarioId] (offline se não está no mapa). */
fun Map<Long, PresencaContato>.estadoDe(usuarioId: Long?): EstadoPresenca =
    usuarioId?.let { this[it]?.estado } ?: EstadoPresenca.OFFLINE

/** O subtítulo da conversa direta, na ordem do web. */
sealed interface SubtituloPresenca {
    /** A outra pessoa está com esta conversa aberta. */
    data object NestaConversa : SubtituloPresenca

    data object AtivoAgora : SubtituloPresenca

    data object Ausente : SubtituloPresenca

    /** "visto por último hoje às HH:mm". */
    data class VistoHoje(val hora: String) : SubtituloPresenca

    /** "visto por último ontem às HH:mm". */
    data class VistoOntem(val hora: String) : SubtituloPresenca

    /** "visto por último em dd/MM às HH:mm" (com o ano, se não for o deste ano). */
    data class VistoEm(val data: String, val hora: String) : SubtituloPresenca

    /** Offline sem "visto por último" (ou escondido): nada. */
    data object Nenhum : SubtituloPresenca
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA_MES = DateTimeFormatter.ofPattern("dd/MM")
private val DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun subtituloPresenca(presenca: PresencaContato?, nestaConversa: Boolean, agora: Instant, zona: ZoneId): SubtituloPresenca {
    if (nestaConversa) return SubtituloPresenca.NestaConversa
    return when (presenca?.estado) {
        EstadoPresenca.ATIVO -> SubtituloPresenca.AtivoAgora
        EstadoPresenca.AUSENTE -> SubtituloPresenca.Ausente
        else -> presenca?.vistoEm?.let { vistoPorUltimo(it, agora, zona) } ?: SubtituloPresenca.Nenhum
    }
}

private fun vistoPorUltimo(visto: Instant, agora: Instant, zona: ZoneId): SubtituloPresenca {
    val momento = visto.atZone(zona)
    val hoje: LocalDate = agora.atZone(zona).toLocalDate()
    val hora = HORA.format(momento)
    return when (ChronoUnit.DAYS.between(momento.toLocalDate(), hoje)) {
        0L -> SubtituloPresenca.VistoHoje(hora)
        1L -> SubtituloPresenca.VistoOntem(hora)
        else -> SubtituloPresenca.VistoEm((if (momento.year == hoje.year) DIA_MES else DIA_MES_ANO).format(momento), hora)
    }
}
