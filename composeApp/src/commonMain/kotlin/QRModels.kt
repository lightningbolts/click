package compose.project.click.click.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QrPayload(
    val userId: String,
    val shareKey: String? = null, // Changed to String? as User model has shareKey as String? or removed?
    // User model in Models.kt doesn't have shareKey. It was removed in migration script.
    // So maybe we don't need shareKey.
    val name: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

fun QrPayload.toJson(): String = json.encodeToString(QrPayload.serializer(), this)

fun String.toQrPayloadOrNull(): QrPayload? =
    try {
        json.decodeFromString(QrPayload.serializer(), this)
    } catch (e: Exception) {
        null
    }
