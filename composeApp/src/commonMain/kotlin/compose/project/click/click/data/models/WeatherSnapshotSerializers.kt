package compose.project.click.click.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Decodes `weather_snapshot` from Postgres/PostgREST whether stored as a JSON object,
 * a stringified JSON object, or a legacy human-readable label.
 */
object FlexibleWeatherSnapshotSerializer : KSerializer<WeatherSnapshot?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("WeatherSnapshotOrLegacy")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun deserialize(decoder: Decoder): WeatherSnapshot? {
        return runCatching {
            when (decoder) {
                is JsonDecoder -> parseWeatherSnapshotElement(decoder.decodeJsonElement(), json)
                else -> throw SerializationException("Expected JSON decoder for weather_snapshot")
            }
        }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: WeatherSnapshot?) {
        val enc = encoder as? JsonEncoder ?: throw SerializationException("Expected JSON encoder")
        if (value == null) {
            enc.encodeJsonElement(JsonNull)
        } else {
            enc.encodeJsonElement(JsonPrimitive(json.encodeToString(WeatherSnapshot.serializer(), value)))
        }
    }
}

internal fun parseWeatherSnapshotElement(element: JsonElement, json: Json): WeatherSnapshot? {
    if (element is JsonNull) return null
    if (element is JsonPrimitive) {
        if (!element.isString) return null
        val s = element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { json.decodeFromString(WeatherSnapshot.serializer(), s) }.getOrNull()?.let { return it }
        return legacyWeatherLabelToSnapshot(s)
    }
    if (element is JsonObject) {
        return runCatching { json.decodeFromJsonElement(WeatherSnapshot.serializer(), element) }.getOrNull()
    }
    return null
}

private val legacyWeatherRegex = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*°C\s*,\s*(.+)$""", RegexOption.IGNORE_CASE)

private fun legacyWeatherLabelToSnapshot(raw: String): WeatherSnapshot? {
    val m = legacyWeatherRegex.matchEntire(raw.trim()) ?: return null
    val temp = m.groupValues[1].toDoubleOrNull() ?: return null
    val cond = m.groupValues[2].trim().takeIf { it.isNotEmpty() } ?: return null
    return WeatherSnapshot(
        iconCode = "",
        condition = cond,
        windSpeedKph = null,
        pressureMslHpa = null,
        temperatureCelsius = temp,
        windDirectionDegrees = null,
    )
}
