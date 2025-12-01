kotlin
package compose.project.click.click.qr

import kotlinx.serialization.json.Json
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object QrToken {
    private const val HMAC_ALGO = "HmacSHA256"
    private val json = Json { encodeDefaults = true }

    fun createToken(payload: QrPayload, secret: ByteArray): String {
        val jsonText = json.encodeToString(QrPayload.serializer(), payload)
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonText.toByteArray(Charsets.UTF_8))
        val sig = hmacSha256(jsonText.toByteArray(Charsets.UTF_8), secret)
        val sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig)
        return "$payloadB64.$sigB64"
    }

    fun parseToken(token: String, secret: ByteArray): QrPayload? {
        val parts = token.split(".")
        if (parts.size != 2) return null
        val payloadJson = try {
            String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val expectedSig = try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(hmacSha256(payloadJson.toByteArray(Charsets.UTF_8), secret))
        } catch (e: Exception) {
            return null
        }
        if (!constantTimeEquals(parts[1], expectedSig)) return null
        return try {
            json.decodeFromString(QrPayload.serializer(), payloadJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun hmacSha256(data: ByteArray, secret: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(secret, HMAC_ALGO))
        return mac.doFinal(data)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}