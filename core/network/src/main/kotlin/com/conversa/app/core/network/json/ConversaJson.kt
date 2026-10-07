package com.conversa.app.core.network.json

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Configuração de JSON do contrato:
 * - campos desconhecidos são ignorados (o servidor evolui);
 * - `null` não é enviado (vários campos do servidor são "opcionais sem null",
 *   contrato §3); para mandar `null` de propósito use [JsonNull] via [nuloExplicito];
 * - valores nulos em campos com valor padrão viram o padrão.
 */
val ConversaJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Para campos que aceitam `null` de propósito (ex.: `token_fcm: null` no logout). */
val nuloExplicito: JsonElement = JsonNull

/**
 * Data do servidor → [Instant]. O padrão é ISO-8601 com `Z` (`2026-10-06T12:00:00.123Z`),
 * mas aceita também offset, ausência de fuso (tratada como UTC, ex.: o JSON de resumo
 * da chamada, contrato §9.8) e espaço no lugar do `T`.
 */
object InstantFlexivelSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantFlexivel", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = lerInstant(decoder.decodeString())
        ?: throw SerializationException("Data inválida")
}

fun lerInstant(texto: String): Instant? {
    val t = texto.trim().replace(' ', 'T')
    if (t.isEmpty()) return null
    return try {
        Instant.parse(t)
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(t).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(t).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * Id que às vezes vem como texto (ex.: `id` do anexo já existente em `PUT /anexo`,
 * contrato §8.2). Aceita número ou string numérica.
 */
object IdFlexivelSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IdFlexivel", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

    override fun deserialize(decoder: Decoder): Long {
        if (decoder is JsonDecoder) {
            val elemento = decoder.decodeJsonElement()
            val primitivo = elemento as? JsonPrimitive ?: throw SerializationException("Id inválido")
            return primitivo.longOrNull ?: primitivo.content.toLongOrNull()
                ?: throw SerializationException("Id inválido: ${primitivo.content}")
        }
        return decoder.decodeLong()
    }
}
