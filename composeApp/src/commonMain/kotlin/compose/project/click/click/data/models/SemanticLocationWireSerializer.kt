package compose.project.click.click.data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Decodes `connection_encounters.semantic_location` whether PostgREST returns a JSON object/array,
 * a stringified JSON blob, or null — always normalized to a single string for UI parsing.
 */
object SemanticLocationWireSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SemanticLocationWire")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun deserialize(decoder: Decoder): String? {
        return when (decoder) {
            is JsonDecoder -> decodeElement(decoder.decodeJsonElement())
            else -> decoder.decodeString().trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun decodeElement(element: JsonElement): String? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) {
            return element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (element is JsonObject || element is JsonArray) {
            return runCatching {
                json.encodeToString(JsonElement.serializer(), element)
            }.getOrNull()
        }
        return null
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val enc = encoder as? JsonEncoder
            ?: throw SerializationException("Expected JSON encoder for semantic_location")
        if (value == null) {
            enc.encodeJsonElement(JsonNull)
        } else {
            enc.encodeJsonElement(JsonPrimitive(value))
        }
    }
}
