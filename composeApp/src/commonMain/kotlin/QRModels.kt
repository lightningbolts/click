package compose.project.click.click.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.jsonimport javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlinx.serialization.json


@Serializable
data class QrPayload(
    val userId: String,
    val shareKey: Long,
    val expiryEpochSecs: Long // 有効期限を入れる
)

fun QrPayload.toJson(): String = Json.encodeToString(QrPayload.serializer(), this)

fun String.toQrPayloadOrNull(): QrPayload? =
    try {
        Json.decodeFromString(QrPayload.serializer(), this)
    } catch (e: Exception) {
        null
    }.Json

@Serializable
data class QrPayload(
    val userId: String,
    val shareKey: Long,
    val name: String? = null
)

fun QrPayload.toJson(): String = Json.encodeToString(QrPayload.serializer(), this)

fun String.toQrPayloadOrNull(): QrPayload? =
    try {
        json.decodeFromString(QrPayload.Serializer(), this)
    } catch (e: Exception) {
        null
    }
