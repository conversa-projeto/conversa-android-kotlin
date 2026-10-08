package com.conversa.app.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- Agendar mensagem (7.10, FC-514), como o `AgendarMensagemModal.vue` ---

/** Mínimo de antecedência do envio agendado (o web e o servidor). */
val ANTECEDENCIA_MINIMA_AGENDAMENTO: Duration = Duration.ofMinutes(5)

enum class ErroAgendamento { MUITO_CEDO, MUITO_LONGE }

/** Sugestão ao abrir: amanhã às 08:00. */
fun sugestaoAgendamento(agora: Instant, zona: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDate.ofInstant(agora, zona).plusDays(1).atTime(LocalTime.of(8, 0))

/** Pelo menos 5 minutos no futuro e no máximo 1 ano. */
fun validarAgendamento(quando: LocalDateTime, agora: Instant, zona: ZoneId = ZoneId.systemDefault()): ErroAgendamento? {
    val instante = quando.atZone(zona).toInstant()
    return when {
        instante < agora + ANTECEDENCIA_MINIMA_AGENDAMENTO -> ErroAgendamento.MUITO_CEDO
        instante > agora.atZone(zona).plusYears(1).toInstant() -> ErroAgendamento.MUITO_LONGE
        else -> null
    }
}

/** Agendada para depois de [agora]: selo "Agendada para …" e a bolha esmaecida (só o autor vê). */
fun Mensagem.agendadaFutura(agora: Instant): Boolean = visivelEm?.let { it > agora } == true

/** O dia do selo, sem a palavra (o "hoje"/"amanhã" vem do strings.xml). */
enum class DiaAgendado { HOJE, AMANHA, OUTRO }

/** "hoje 18:00", "amanhã 08:30" ou "12/10 18:00": o dia e o texto da hora (com a data, se for outro dia). */
fun quandoAgendada(visivelEm: Instant, agora: Instant, zona: ZoneId = ZoneId.systemDefault()): Pair<DiaAgendado, String> {
    val local = visivelEm.atZone(zona)
    val hoje = LocalDate.ofInstant(agora, zona)
    return when (local.toLocalDate()) {
        hoje -> DiaAgendado.HOJE to HORA.format(local)
        hoje.plusDays(1) -> DiaAgendado.AMANHA to HORA.format(local)
        else -> DiaAgendado.OUTRO to DIA_HORA.format(local)
    }
}

// --- Detalhe do status (7.10, FC-513), como o `DetalheStatusMensagem.vue` ---

/** Um destinatário de `GET /mensagem/status/detalhe` (aqui os status são datas). */
data class StatusDestinatario(
    val usuarioId: Long,
    val nome: String,
    val recebida: Instant?,
    val visualizada: Instant?,
    val reproduzida: Instant?,
)

enum class EtapaEntrega { ENVIADA, RECEBIDA, VISUALIZADA, OUVIDA, OCULTA }

/** Conversa direta: o horário de cada etapa (sem data = "Aguardando"); "Ouvida" só em áudio, "Oculta" só se foi ocultada. */
fun etapasDaDireta(mensagem: Mensagem, destinatarios: List<StatusDestinatario>): List<Pair<EtapaEntrega, Instant?>> {
    val destino = destinatarios.firstOrNull()
    val audio = mensagem.conteudos.any { it.tipo == TipoConteudo.AUDIO || it.tipo == TipoConteudo.GRAVACAO_AUDIO }
    return buildList {
        add(EtapaEntrega.ENVIADA to mensagem.dataEfetiva)
        add(EtapaEntrega.RECEBIDA to destino?.recebida)
        add(EtapaEntrega.VISUALIZADA to destino?.visualizada)
        if (audio) add(EtapaEntrega.OUVIDA to destino?.reproduzida)
        mensagem.excluidaEm?.let { add(EtapaEntrega.OCULTA to it) }
    }
}

enum class SecaoStatus { VISUALIZADA_POR, RECEBIDA_POR, AGUARDANDO }

/** Grupo: quem viu, quem só recebeu e quem ainda não recebeu (com o horário de cada um); seções vazias somem. */
fun secoesDoGrupo(destinatarios: List<StatusDestinatario>): List<Pair<SecaoStatus, List<Pair<String, Instant?>>>> = listOf(
    SecaoStatus.VISUALIZADA_POR to destinatarios.filter { it.visualizada != null }.map { it.nome to it.visualizada },
    SecaoStatus.RECEBIDA_POR to destinatarios.filter { it.recebida != null && it.visualizada == null }.map { it.nome to it.recebida },
    SecaoStatus.AGUARDANDO to destinatarios.filter { it.recebida == null }.map { it.nome to null },
).filter { it.second.isNotEmpty() }

/** Horário no detalhe: hoje só a hora; outros dias, "dd/MM HH:mm". */
fun horaDoStatus(em: Instant, agora: Instant, zona: ZoneId = ZoneId.systemDefault()): String {
    val local = em.atZone(zona)
    return if (local.toLocalDate() == LocalDate.ofInstant(agora, zona)) HORA.format(local) else DIA_HORA.format(local)
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")
