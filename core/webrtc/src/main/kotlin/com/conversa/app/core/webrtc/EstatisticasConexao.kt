package com.conversa.app.core.webrtc

import java.util.Locale
import kotlin.math.roundToLong
import org.webrtc.RTCStats

/**
 * Quanto esperar a conexão com o MediaMTX fechar depois da resposta do WHIP/WHEP, antes de refazê-la.
 * - Pelo coturn da rede interna, ela fecha em 0,2–0,4 s.
 * - Presa em "conectando", o WebRTC só a dava por perdida depois de ~18 s (registro do celular de 2026-11-10).
 * - Cada refeita seguida ganha mais tempo, para uma rede lenta não ficar refazendo para sempre.
 */
internal fun prazoParaConectar(refeitas: Int): Long = PRAZOS_CONEXAO_MS[refeitas.coerceIn(0, PRAZOS_CONEXAO_MS.lastIndex)]

internal val PRAZOS_CONEXAO_MS = longArrayOf(3_000, 6_000, 10_000)

/** "1 host udp, 1 host tcp": os candidatos que o MediaMTX mandou na resposta, sem os endereços. */
internal fun candidatosDaResposta(sdp: String): String = sdp.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("a=candidate:") }
    .map { tipoDoCandidato(it.removePrefix("a=")) }
    .groupingBy { it }
    .eachCount()
    .entries
    .joinToString { "${it.value} ${it.key}" }
    .ifEmpty { "nenhum candidato" }

/**
 * Em que passo a conexão está, para o registro quando ela não fecha ou cai:
 * - o transporte: estado do ICE e do DTLS, e quanto passou;
 * - os pares de candidatos: o escolhido primeiro, com os pedidos e as respostas de conexão;
 * - os pacotes de áudio.
 * Nunca os endereços.
 */
internal fun resumoDaConexao(estatisticas: Collection<RTCStats>): String {
    if (estatisticas.isEmpty()) return "sem estatísticas"
    val porId = estatisticas.associateBy { it.id }
    val transporte = estatisticas.firstOrNull { it.type == "transport" }
    val partes = mutableListOf<String>()
    partes += transporte?.members?.let { m ->
        "ICE ${m["iceState"] ?: "?"}, DTLS ${m["dtlsState"] ?: "?"}, " +
            "enviou ${tamanhoCurto(numero(m["bytesSent"]))}, recebeu ${tamanhoCurto(numero(m["bytesReceived"]))}"
    } ?: "sem transporte"
    val escolhido = transporte?.members?.get("selectedCandidatePairId")
    val pares = estatisticas.filter { it.type == "candidate-pair" }
        .sortedWith(
            compareBy({ it.id != escolhido }, { it.members["nominated"] != true }, {
                ORDEM_PARES.indexOf(
                    it.members["state"] as? String ?: "",
                )
            }),
        )
    partes += if (pares.isEmpty()) {
        "nenhum par de candidatos"
    } else {
        val vistos = pares.take(MAX_PARES).joinToString("; ") { par -> descreverPar(par, porId, par.id == escolhido) }
        val resto = pares.size - MAX_PARES
        "pares: $vistos" + if (resto > 0) "; +$resto" else ""
    }
    val enviados = estatisticas.filter {
        it.type == "outbound-rtp" && it.members["kind"] == "audio"
    }.sumOf { numero(it.members["packetsSent"]) }
    val recebidos = estatisticas.filter {
        it.type == "inbound-rtp" && it.members["kind"] == "audio"
    }.sumOf { numero(it.members["packetsReceived"]) }
    partes += "áudio: $enviados pacote(s) enviado(s), $recebidos recebido(s)"
    return partes.joinToString("; ")
}

private fun descreverPar(par: RTCStats, porId: Map<String, RTCStats>, escolhido: Boolean): String {
    val m = par.members
    val local = descreverCandidato(porId[m["localCandidateId"]])
    val remoto = descreverCandidato(porId[m["remoteCandidateId"]])
    val marcas = listOfNotNull(
        m["state"]?.toString() ?: "?",
        "escolhido".takeIf { escolhido },
        "indicado".takeIf { m["nominated"] == true },
        "pedidos ${numero(m["requestsSent"])}, respostas ${numero(m["responsesReceived"])}",
        "pedidos do outro lado ${numero(m["requestsReceived"])}",
        (m["currentRoundTripTime"] as? Number)?.let { "RTT ${milissegundos(it)} ms" },
    )
    return "$local → $remoto: ${marcas.joinToString(", ")}"
}

/** "relay udp (TURN por tcp)", "host udp", "prflx udp". */
private fun descreverCandidato(candidato: RTCStats?): String {
    val m = candidato?.members ?: return "?"
    val tipo = "${m["candidateType"] ?: "?"} ${m["protocol"] ?: "?"}"
    val turn = m["relayProtocol"]?.takeIf { m["candidateType"] == "relay" }
    return if (turn != null) "$tipo (TURN por $turn)" else tipo
}

/**
 * A qualidade de uma conexão já de pé, a cada tanto durante a chamada: o atraso da rede (RTT), e para cada
 * mídia o que foi e veio, a perda, a variação (jitter) e o atraso do buffer de quem recebe.
 */
internal fun resumoDaQualidade(estatisticas: Collection<RTCStats>): String {
    if (estatisticas.isEmpty()) return "sem estatísticas"
    val porId = estatisticas.associateBy { it.id }
    val partes = mutableListOf<String>()
    val transporte = estatisticas.firstOrNull { it.type == "transport" }
    val par = transporte?.members?.get("selectedCandidatePairId")?.let { porId[it] }
    (par?.members?.get("currentRoundTripTime") as? Number)?.let { partes += "RTT ${milissegundos(it)} ms" }
    val envios = estatisticas.filter { it.type == "outbound-rtp" }.sortedBy { it.members["kind"].toString() }
    val recepcoes = estatisticas.filter { it.type == "inbound-rtp" }.sortedBy { it.members["kind"].toString() }
    envios.forEach { partes += descreverEnvio(it, estatisticas) }
    recepcoes.forEach { partes += descreverRecepcao(it) }
    // Conectada e sem pacote de mídia: quem publica ainda não mandou nada (ninguém ouve).
    if (envios.isEmpty() && recepcoes.isEmpty() && partes.isNotEmpty()) partes += "nenhum pacote de mídia ainda"
    return partes.ifEmpty { listOf("nada medido") }.joinToString("; ")
}

private fun descreverEnvio(envio: RTCStats, estatisticas: Collection<RTCStats>): String {
    val m = envio.members
    val tipo = midia(m["kind"])
    val itens = mutableListOf("enviou ${numero(m["packetsSent"])} pacotes")
    if (m["kind"] == "video") {
        quadro(m)?.let(itens::add)
        m["qualityLimitationReason"]?.takeIf { it != "none" }?.let { itens += "limitado por $it" }
    }
    // O que o MediaMTX diz que recebeu (RTCP): perda e atraso do lado de lá.
    estatisticas.firstOrNull { it.type == "remote-inbound-rtp" && it.members["localId"] == envio.id }?.members?.let { lado ->
        itens += "perdidos lá ${numero(lado["packetsLost"])}"
        (lado["jitter"] as? Number)?.let { itens += "jitter lá ${milissegundos(it)} ms" }
        (lado["roundTripTime"] as? Number)?.let { itens += "RTT pelo RTCP ${milissegundos(it)} ms" }
    }
    return "$tipo: ${itens.joinToString(", ")}"
}

private fun descreverRecepcao(recepcao: RTCStats): String {
    val m = recepcao.members
    val tipo = midia(m["kind"])
    val recebidos = numero(m["packetsReceived"])
    val itens = mutableListOf("recebeu $recebidos pacotes", "perdidos ${numero(m["packetsLost"])}")
    (m["jitter"] as? Number)?.let { itens += "jitter ${milissegundos(it)} ms" }
    val atrasoBuffer = m["jitterBufferDelay"] as? Number
    val saidos = numero(m["jitterBufferEmittedCount"])
    if (atrasoBuffer != null && saidos > 0) itens += "buffer ${milissegundos(atrasoBuffer.toDouble() / saidos)} ms"
    if (m["kind"] == "audio") {
        val amostras = numero(m["totalSamplesReceived"])
        if (amostras > 0) itens += "ocultado ${porcentagem(numero(m["concealedSamples"]), amostras)}"
    } else {
        quadro(m)?.let(itens::add)
        numero(m["freezeCount"]).takeIf { it > 0 }?.let { itens += "$it congelamento(s)" }
    }
    return "$tipo: ${itens.joinToString(", ")}"
}

/** "640x360 a 15 fps". */
private fun quadro(m: Map<String, Any?>): String? {
    val largura = numero(m["frameWidth"])
    val altura = numero(m["frameHeight"])
    if (largura == 0L || altura == 0L) return null
    val fps = (m["framesPerSecond"] as? Number)?.toDouble()?.roundToLong()
    return "${largura}x$altura" + if (fps != null) " a $fps fps" else ""
}

private fun midia(tipo: Any?) = when (tipo) {
    "audio" -> "áudio"
    "video" -> "vídeo"
    else -> tipo?.toString() ?: "?"
}

/** Os números das estatísticas vêm como Long, Integer, BigInteger ou Double. */
private fun numero(valor: Any?): Long = (valor as? Number)?.toLong() ?: 0L

/** Segundos (como o WebRTC mede) em milissegundos. */
private fun milissegundos(segundos: Number): Long = (segundos.toDouble() * 1000).roundToLong()

private fun porcentagem(parte: Long, total: Long) = String.format(PT_BR, "%.1f %%", parte * 100.0 / total)

/** "512 B", "2,3 kB", "4,1 MB" (como no registro do HTTP). */
private fun tamanhoCurto(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(PT_BR, "%.1f kB", bytes / 1024.0)
    else -> String.format(PT_BR, "%.1f MB", bytes / (1024.0 * 1024))
}

private val PT_BR = Locale.forLanguageTag("pt-BR")

private val ORDEM_PARES = listOf("succeeded", "in-progress", "waiting", "frozen", "failed")

private const val MAX_PARES = 3
