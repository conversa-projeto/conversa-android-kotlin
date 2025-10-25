import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class UtcToLocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDateTime? {
        if (json == null || json.isJsonNull) return null

        val dateString = json.asString

        // Parse a data como UTC
        val utcDateTime = ZonedDateTime.parse(
            dateString,
            DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC"))
        )

        // Converte para o timezone local
        return utcDateTime
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}