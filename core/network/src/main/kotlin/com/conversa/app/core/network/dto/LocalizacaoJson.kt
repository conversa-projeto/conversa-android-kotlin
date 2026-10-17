package com.conversa.app.core.network.dto

import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.network.json.ConversaJson
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// O `conteudo` do tipo 9 é um JSON dentro do texto (contrato §10.16; web `utils/localizacao.ts`).

@Serializable
private data class PontoDto(val lat: Double, val lng: Double, val nome: String? = null)

@Serializable
private data class LocalizacaoDto(
    val pontos: List<PontoDto> = emptyList(),
    val rota: String? = null,
    val distancia: Double? = null,
    val duracao: Double? = null,
)

/** O conteúdo tipo 9 lido; nulo se não for uma localização com pelo menos um ponto (o web mostra "Localização"). */
fun lerLocalizacao(conteudo: String): Localizacao? {
    val dto = runCatching { ConversaJson.decodeFromString(LocalizacaoDto.serializer(), conteudo) }.getOrNull() ?: return null
    if (dto.pontos.isEmpty()) return null
    return Localizacao(dto.pontos.map { PontoMapa(it.lat, it.lng, it.nome) }, dto.rota, dto.distancia, dto.duracao)
}

/**
 * O texto que vai no `conteudo`, como o web manda: chaves `pontos`, `rota`, `distancia`, `duracao`
 * (as duas últimas inteiras) e, no ponto, `lat`, `lng`, `nome` (até 300 caracteres). Nada de `null`:
 * o servidor recusa ("Localização inválida!").
 */
fun localizacaoParaJson(localizacao: Localizacao): String {
    val pontos = localizacao.pontos.map { ponto ->
        JsonObject(
            buildMap {
                put("lat", JsonPrimitive(ponto.lat))
                put("lng", JsonPrimitive(ponto.lng))
                ponto.nome?.trim()?.takeIf { it.isNotEmpty() }?.let { put("nome", JsonPrimitive(it.take(300))) }
            },
        )
    }
    val objeto = JsonObject(
        buildMap {
            put("pontos", JsonArray(pontos))
            if (localizacao.trajeto) localizacao.rota?.let { put("rota", JsonPrimitive(it)) }
            localizacao.distancia?.let { put("distancia", JsonPrimitive(it.roundToLong())) }
            localizacao.duracao?.let { put("duracao", JsonPrimitive(it.roundToLong())) }
        },
    )
    return ConversaJson.encodeToString(JsonObject.serializer(), objeto)
}
