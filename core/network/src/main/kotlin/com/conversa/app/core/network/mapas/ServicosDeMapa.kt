package com.conversa.app.core.network.mapas

import com.conversa.app.core.model.LIMITE_NOME_PONTO
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.conversa.app.core.network.json.ConversaJson
import java.util.Locale
import kotlin.math.round
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// Os serviços públicos do OpenStreetMap que o web chama direto (web `services/mapas.ts`): a busca
// de endereço e o nome do lugar (Nominatim) e a rota de carro (OSRM). Aqui ficam os endereços e a
// leitura das respostas; quem chama é o `MapasRepositorio`.

val NOMINATIM: HttpUrl = "https://nominatim.openstreetmap.org/".toHttpUrl()
val OSRM: HttpUrl = "https://router.project-osrm.org/".toHttpUrl()

/** Os domínios dos mapas (tiles, busca e rota). */
private val DOMINIOS_DE_MAPA = listOf("openstreetmap.org", "arcgisonline.com", "project-osrm.org")

/** Requisição para um serviço de mapa: fica fora do log, que mostraria o endereço buscado e a região vista. */
fun ehServicoDeMapa(host: String): Boolean = DOMINIOS_DE_MAPA.any { host == it || host.endsWith(".$it") }

/** Coordenada no endereço: ponto decimal e sem notação científica. */
private fun coordenada(valor: Double): String = "%.6f".format(Locale.ROOT, valor)

/** Busca de endereço (até 6 resultados, nomes em português). */
fun urlBuscaEndereco(texto: String, base: HttpUrl = NOMINATIM): HttpUrl = base.newBuilder()
    .addPathSegment("search")
    .addQueryParameter("format", "jsonv2")
    .addQueryParameter("limit", "6")
    .addQueryParameter("accept-language", "pt-BR")
    .addQueryParameter("q", texto)
    .build()

/** O nome do lugar num ponto (GPS ou toque no mapa). */
fun urlNomeDoLugar(ponto: PontoMapa, base: HttpUrl = NOMINATIM): HttpUrl = base.newBuilder()
    .addPathSegment("reverse")
    .addQueryParameter("format", "jsonv2")
    .addQueryParameter("zoom", "18")
    .addQueryParameter("accept-language", "pt-BR")
    .addQueryParameter("lat", coordenada(ponto.lat))
    .addQueryParameter("lon", coordenada(ponto.lng))
    .build()

/** A rota de carro da origem ao destino, com a linha inteira em polyline. */
fun urlRota(origem: PontoMapa, destino: PontoMapa, base: HttpUrl = OSRM): HttpUrl = base.newBuilder()
    .addPathSegments("route/v1/driving")
    .addPathSegment("${coordenada(origem.lng)},${coordenada(origem.lat)};${coordenada(destino.lng)},${coordenada(destino.lat)}")
    .addQueryParameter("overview", "full")
    .addQueryParameter("geometries", "polyline")
    .build()

@Serializable
private data class ResultadoNominatimDto(
    val lat: String? = null,
    val lon: String? = null,
    @SerialName("display_name") val nome: String? = null,
)

@Serializable
private data class RespostaOsrmDto(val code: String = "", val routes: List<RotaOsrmDto> = emptyList())

@Serializable
private data class RotaOsrmDto(val geometry: String = "", val distance: Double = 0.0, val duration: Double = 0.0)

/** Os resultados da busca, com o nome cortado no limite do servidor. Resposta que não se lê: lista vazia. */
fun lerBuscaEndereco(corpo: String): List<PontoMapa> {
    val lista = runCatching { ConversaJson.decodeFromString(ListSerializer(ResultadoNominatimDto.serializer()), corpo) }
        .getOrDefault(emptyList())
    return lista.mapNotNull { item ->
        val lat = item.lat?.toDoubleOrNull() ?: return@mapNotNull null
        val lng = item.lon?.toDoubleOrNull() ?: return@mapNotNull null
        PontoMapa(lat, lng, item.nome?.trim()?.takeIf { it.isNotEmpty() }?.take(LIMITE_NOME_PONTO))
    }
}

/** O nome do lugar; nulo quando o Nominatim não acha (`{"error": …}`) ou a resposta não se lê. */
fun lerNomeDoLugar(corpo: String): String? = runCatching { ConversaJson.decodeFromString(ResultadoNominatimDto.serializer(), corpo) }
    .getOrNull()?.nome?.trim()?.takeIf { it.isNotEmpty() }?.take(LIMITE_NOME_PONTO)

/** A primeira rota, com distância e tempo arredondados como o web; nula quando o OSRM não acha. */
fun lerRota(corpo: String): RotaCarro? {
    val resposta = runCatching { ConversaJson.decodeFromString(RespostaOsrmDto.serializer(), corpo) }.getOrNull() ?: return null
    if (resposta.code != "Ok") return null
    val rota = resposta.routes.firstOrNull()?.takeIf { it.geometry.isNotEmpty() } ?: return null
    return RotaCarro(rota.geometry, round(rota.distance), round(rota.duration))
}
