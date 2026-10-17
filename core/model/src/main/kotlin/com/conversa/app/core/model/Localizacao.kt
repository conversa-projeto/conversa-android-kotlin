package com.conversa.app.core.model

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

// Localização e trajeto (conteúdo tipo 9; servidor `ac710eb`, contrato §10.16; web `utils/localizacao.ts`).

/** Um ponto do mapa; [nome] é o endereço (até [LIMITE_NOME_PONTO] caracteres) ou nulo. */
data class PontoMapa(val lat: Double, val lng: Double, val nome: String? = null)

/**
 * O conteúdo tipo 9: um ponto é um local; dois são um trajeto (origem e destino). [rota] é a
 * polyline do OSRM (precisão 5); [distancia] em metros e [duracao] em segundos. Trajeto sem rota
 * (o OSRM falhou) leva só a distância em linha reta.
 */
data class Localizacao(val pontos: List<PontoMapa>, val rota: String? = null, val distancia: Double? = null, val duracao: Double? = null) {
    val trajeto: Boolean get() = pontos.size == 2
}

/** A rota de carro do OSRM: a polyline, a distância (metros) e o tempo (segundos), inteiros como no web. */
data class RotaCarro(val rota: String, val distancia: Double, val duracao: Double)

/**
 * O que vai na mensagem (web `LocalizacaoModal.resultado`): um ponto é o local; dois, o trajeto
 * com a rota de carro ou, sem ela, só a distância em linha reta.
 */
fun montarLocalizacao(pontos: List<PontoMapa>, rota: RotaCarro?): Localizacao = when {
    pontos.size < 2 -> Localizacao(pontos)
    rota == null -> Localizacao(pontos, distancia = kotlin.math.round(distanciaReta(pontos[0], pontos[1])))
    else -> Localizacao(pontos, rota.rota, rota.distancia, rota.duracao)
}

/** O servidor aceita nomes de até 300 caracteres. */
const val LIMITE_NOME_PONTO = 300

/** Raio da Terra usado pelo web (haversine). */
private const val RAIO_TERRA_M = 6_371_000.0

/** "-23.50000, -46.60000" (ponto decimal, 5 casas), como o web. */
fun formatarCoordenadas(lat: Double, lng: Double): String = "%.5f, %.5f".format(Locale.ROOT, lat, lng)

/** "850 m"; "12,3 km"; "150 km" (sem casa decimal a partir de 100 km), como o web. */
fun formatarDistancia(metros: Double): String {
    if (metros < 1000) return "${metros.roundToLong()} m"
    val formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = if (metros < 100_000) 1 else 0
        minimumFractionDigits = 0
    }
    return "${formato.format(metros / 1000)} km"
}

/** "1 min"; "45 min"; "1 h 30 min"; "2 h" (pelo menos 1 min), como o web. */
fun formatarTempoTrajeto(segundos: Double): String {
    val minutos = max(1, (segundos / 60).roundToInt())
    if (minutos < 60) return "$minutos min"
    val horas = minutos / 60
    val resto = minutos % 60
    return if (resto == 0) "$horas h" else "$horas h $resto min"
}

/** Distância em linha reta entre dois pontos (haversine), em metros. */
fun distanciaReta(a: PontoMapa, b: PontoMapa): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * RAIO_TERRA_M * asin(sqrt(h))
}

/** O nome do ponto, ou as coordenadas quando não tem. */
fun nomePonto(ponto: PontoMapa): String = ponto.nome?.trim()?.takeIf { it.isNotEmpty() } ?: formatarCoordenadas(ponto.lat, ponto.lng)

/** Local: o nome; trajeto: "Casa → Trabalho · 12,3 km". */
fun resumoLocalizacao(localizacao: Localizacao): String {
    if (!localizacao.trajeto) return nomePonto(localizacao.pontos.first())
    val nomes = localizacao.pontos.joinToString(" → ") { nomePonto(it) }
    return localizacao.distancia?.let { "$nomes · ${formatarDistancia(it)}" } ?: nomes
}

/** A linha do trajeto (o texto fica no strings.xml): "Trajeto", "… em linha reta" ou "… de carro". */
sealed interface DescricaoTrajeto {
    data object SoTrajeto : DescricaoTrajeto

    data class LinhaReta(val distancia: String) : DescricaoTrajeto

    data class DeCarro(val distancia: String, val tempo: String) : DescricaoTrajeto
}

fun descricaoTrajeto(localizacao: Localizacao): DescricaoTrajeto {
    val distancia = localizacao.distancia ?: return DescricaoTrajeto.SoTrajeto
    val duracao = localizacao.duracao ?: return DescricaoTrajeto.LinhaReta(formatarDistancia(distancia))
    return DescricaoTrajeto.DeCarro(formatarDistancia(distancia), formatarTempoTrajeto(duracao))
}

/** O link "Abrir no Google Maps" do web: a rota de carro no trajeto, a busca pelo ponto no local. */
fun linkMapa(localizacao: Localizacao): String {
    fun coordenadas(p: PontoMapa) = "${p.lat}%2C${p.lng}"
    val primeiro = localizacao.pontos.first()
    return if (localizacao.trajeto) {
        "https://www.google.com/maps/dir/?api=1&origin=${coordenadas(
            primeiro,
        )}&destination=${coordenadas(localizacao.pontos[1])}&travelmode=driving"
    } else {
        "https://www.google.com/maps/search/?api=1&query=${coordenadas(primeiro)}"
    }
}

/**
 * Coordenadas digitadas na busca ("-23.55, -46.63", "-23,55; -46,63", "-23.55 -46.63"): decimal com
 * ponto ou vírgula; separador vírgula, ponto e vírgula ou espaço; |lat| ≤ 90 e |lng| ≤ 180.
 * Regex sem flags nem classes Unicode (o Android usa o ICU).
 */
fun lerCoordenadas(texto: String): PontoMapa? {
    val achado = Regex("""^(-?[0-9]{1,2}(?:[.,][0-9]+)?)\s*[,;\s]\s*(-?[0-9]{1,3}(?:[.,][0-9]+)?)$""").find(texto.trim()) ?: return null
    val lat = achado.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val lng = achado.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
    if (kotlin.math.abs(lat) > 90 || kotlin.math.abs(lng) > 180) return null
    return PontoMapa(lat, lng)
}

/** A polyline do Google/OSRM (precisão 1e5) em pontos (lat, lng). Texto quebrado: o que deu para ler. */
fun decodificarRota(polyline: String): List<Pair<Double, Double>> {
    val pontos = mutableListOf<Pair<Double, Double>>()
    var indice = 0
    var lat = 0
    var lng = 0
    while (indice < polyline.length) {
        val dLat = proximoValor(polyline, indice) ?: break
        indice = dLat.second
        val dLng = proximoValor(polyline, indice) ?: break
        indice = dLng.second
        lat += dLat.first
        lng += dLng.first
        pontos += lat / 1e5 to lng / 1e5
    }
    return pontos
}

/** Lê um valor da polyline a partir de [inicio]; devolve o valor e onde o próximo começa. */
private fun proximoValor(texto: String, inicio: Int): Pair<Int, Int>? {
    var resultado = 0
    var deslocamento = 0
    var indice = inicio
    while (true) {
        if (indice >= texto.length) return null
        val b = texto[indice++].code - 63
        resultado = resultado or ((b and 0x1f) shl deslocamento)
        deslocamento += 5
        if (b < 0x20) break
    }
    val valor = if (resultado and 1 != 0) (resultado shr 1).inv() else resultado shr 1
    return valor to indice
}
