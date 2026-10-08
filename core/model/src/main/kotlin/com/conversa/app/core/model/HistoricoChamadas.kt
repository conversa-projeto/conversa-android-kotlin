package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ParticipanteHistorico(val usuarioId: Long, val nome: String, val status: StatusParticipante, val avatarUrl: String?)

/** Chamada finalizada (`GET /chamadas`, contrato §9.7). */
data class ChamadaHistorico(
    val id: Long,
    val tipo: TipoChamada,
    val status: StatusChamada,
    val criadoEm: Instant?,
    val criadoPor: Long?,
    /** Conversa de origem (nula quando a chamada não veio de uma conversa). */
    val conversaId: Long?,
    /** Segundos; nula se faltou início ou fim. */
    val duracao: Long?,
    val participantes: List<ParticipanteHistorico>,
) {
    /** Mais de duas pessoas: o web mostra "Grupo (N)". */
    val grupo: Boolean get() = participantes.size > 2

    fun efetuada(eu: Long): Boolean = criadoPor == eu

    /** Quem aparece na linha: o outro participante (ou o primeiro, se só houver eu). */
    fun outro(eu: Long): ParticipanteHistorico? = participantes.firstOrNull { it.usuarioId != eu } ?: participantes.firstOrNull()

    /**
     * Perdida: recebida e eu não entrei (fiquei tocando ou fui recusado sozinho, o
     * "não atendeu" do app). O web filtra pelo status 5, que o servidor nunca grava
     * (contrato §9.1), e a aba "Perdidas" dele fica sempre vazia; aqui vale o que
     * aconteceu comigo. Recusar de propósito também conta: o histórico não distingue.
     */
    fun perdida(eu: Long): Boolean {
        if (status == StatusChamada.DESCONECTADA) return true
        if (efetuada(eu)) return false
        val meu = participantes.firstOrNull { it.usuarioId == eu }?.status
        return meu == StatusParticipante.PENDENTE || meu == StatusParticipante.RECUSOU
    }
}

/** Título do grupo de linhas: "Hoje", "Ontem" ou a data. */
sealed interface DiaHistorico {
    data object Hoje : DiaHistorico

    data object Ontem : DiaHistorico

    data class Data(val dia: LocalDate) : DiaHistorico
}

data class GrupoHistorico(val dia: DiaHistorico, val chamadas: List<ChamadaHistorico>)

/** "Perdidas" e "Buscar contato" (qualquer outro participante cujo nome contenha o texto). */
fun filtrarHistorico(lista: List<ChamadaHistorico>, eu: Long, soPerdidas: Boolean, busca: String): List<ChamadaHistorico> {
    val termo = busca.trim()
    return lista.filter { chamada ->
        (!soPerdidas || chamada.perdida(eu)) &&
            (termo.isEmpty() || chamada.participantes.any { it.usuarioId != eu && it.nome.contains(termo, ignoreCase = true) })
    }
}

/** Agrupa na ordem que veio (a mais recente primeiro) por dia local: Hoje / Ontem / data (como o web). */
fun agruparHistorico(lista: List<ChamadaHistorico>, hoje: LocalDate, zona: ZoneId): List<GrupoHistorico> {
    val grupos = mutableListOf<GrupoHistorico>()
    for (chamada in lista) {
        val dia = chamada.criadoEm?.atZone(zona)?.toLocalDate() ?: hoje
        val rotulo = when (dia) {
            hoje -> DiaHistorico.Hoje
            hoje.minusDays(1) -> DiaHistorico.Ontem
            else -> DiaHistorico.Data(dia)
        }
        val ultimo = grupos.lastOrNull()
        if (ultimo != null && ultimo.dia == rotulo) {
            grupos[grupos.lastIndex] = ultimo.copy(chamadas = ultimo.chamadas + chamada)
        } else {
            grupos += GrupoHistorico(rotulo, listOf(chamada))
        }
    }
    return grupos
}
