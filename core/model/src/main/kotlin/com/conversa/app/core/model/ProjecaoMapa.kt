package com.conversa.app.core.model

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

// Projeção de Mercator dos mapas em tiles (OSM e Esri), como o Leaflet do web, para desenhar a
// localização sem biblioteca de mapa: a bolha, o visualizador e a janela de escolher.

/** Lado de cada tile, em pixels do mapa. */
const val TAMANHO_TILE = 256

/** O Mercator para em ±85,0511° (o mapa vira um quadrado). */
private const val LATITUDE_MAXIMA = 85.05112878

private fun ladoDoMundo(zoom: Double): Double = TAMANHO_TILE * 2.0.pow(zoom)

/** Coordenada X no mapa inteiro, em pixels, no [zoom]. */
fun xNoMapa(lng: Double, zoom: Double): Double = (lng + 180.0) / 360.0 * ladoDoMundo(zoom)

/** Coordenada Y no mapa inteiro, em pixels, no [zoom]. */
fun yNoMapa(lat: Double, zoom: Double): Double {
    val s = sin(Math.toRadians(lat.coerceIn(-LATITUDE_MAXIMA, LATITUDE_MAXIMA)))
    return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * ladoDoMundo(zoom)
}

fun lngDoX(x: Double, zoom: Double): Double = x / ladoDoMundo(zoom) * 360.0 - 180.0

fun latDoY(y: Double, zoom: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / ladoDoMundo(zoom)))))

/** Centro e zoom (inteiro, para os tiles saírem nítidos) de um mapa. */
data class Enquadramento(val lat: Double, val lng: Double, val zoom: Int)

/** Sem pontos, o Brasil inteiro (como o web). */
val ENQUADRAMENTO_PADRAO = Enquadramento(-15.78, -47.93, 4)

/**
 * O enquadramento do web: um ponto no zoom 16; mais de um (inclusive os da rota), o maior zoom
 * até 17 em que todos cabem em [largura] × [altura] pixels com [margem] de folga, centrado neles.
 * [margemTopo] (maior) deixa espaço para o alfinete, que fica acima do ponto.
 */
fun enquadrar(
    pontos: List<Pair<Double, Double>>,
    largura: Double,
    altura: Double,
    margem: Double = 28.0,
    margemTopo: Double = margem,
    zoomMaximo: Int = 17,
    zoomUnico: Int = 16,
): Enquadramento {
    if (pontos.isEmpty()) return ENQUADRAMENTO_PADRAO
    val distintos = pontos.distinct()
    if (distintos.size == 1) return Enquadramento(distintos[0].first, distintos[0].second, zoomUnico)
    for (zoom in zoomMaximo downTo 0) {
        val z = zoom.toDouble()
        val xs = distintos.map { xNoMapa(it.second, z) }
        val ys = distintos.map { yNoMapa(it.first, z) }
        val cabe = xs.max() - xs.min() <= largura - 2 * margem && ys.max() - ys.min() <= altura - margem - margemTopo
        if (cabe || zoom == 0) {
            // O centro desce um pouco: a folga de cima é a do alfinete.
            val centroY = (ys.min() - margemTopo + ys.max() + margem) / 2
            return Enquadramento(latDoY(centroY, z), lngDoX((xs.min() + xs.max()) / 2, z), zoom)
        }
    }
    return ENQUADRAMENTO_PADRAO
}

/** Endereços dos tiles (os mesmos do web), em ordem de desenho. [zoomNativo]: acima dele, o tile é ampliado. */
enum class CamadaMapa(val enderecos: List<Pair<String, Int>>) {
    RUAS(listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png" to 19)),

    /** Imagem da Esri com as ruas e os nomes por cima; ruas e nomes só até o 16 (acima, vêm deslocados). */
    SATELITE(
        listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}" to 18,
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}" to 16,
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}" to 16,
        ),
    ),
}

/** O endereço de um tile; [x] dá a volta no mundo (o mapa se repete na horizontal). */
fun enderecoDoTile(modelo: String, zoom: Int, x: Int, y: Int): String {
    val quantos = 1 shl zoom
    val xNoMundo = ((x % quantos) + quantos) % quantos
    return modelo.replace("{z}", zoom.toString()).replace("{x}", xNoMundo.toString()).replace("{y}", y.toString())
}

/** O zoom máximo dos mapas (o web usa 19). */
const val ZOOM_MAXIMO_MAPA = 19
