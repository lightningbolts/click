package compose.project.click.click.crypto

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "PlatformCrypto"

actual object PlatformCrypto {

    actual fun sha256(data: ByteArray): ByteArray = try {
        MessageDigest.getInstance("SHA-256").digest(data)
    } catch (e: Exception) {
        Log.e(TAG, "sha256 failed: ${e::class.simpleName} — ${e.message}", e)
        throw e
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = try {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.doFinal(data)
    } catch (e: Exception) {
        Log.e(TAG, "hmacSha256 failed: ${e::class.simpleName} — ${e.message}", e)
        throw e
    }

    actual fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray = try {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES key must be 16, 24, or 32 bytes but was ${key.size}"
        }
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes but was ${iv.size}" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        cipher.doFinal(plaintext)
    } catch (e: Exception) {
        Log.e(TAG, "aesCbcEncrypt failed: ${e::class.simpleName} — ${e.message}", e)
        throw e
    }

    actual fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray = try {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "AES key must be 16, 24, or 32 bytes but was ${key.size}"
        }
        require(iv.size == 16) { "AES-CBC IV must be 16 bytes but was ${iv.size}" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        cipher.doFinal(ciphertext)
    } catch (e: Exception) {
        Log.e(TAG, "aesCbcDecrypt failed: ${e::class.simpleName} — ${e.message}", e)
        throw e
    }

    actual fun secureRandomBytes(count: Int): ByteArray = try {
        ByteArray(count).also { SecureRandom().nextBytes(it) }
    } catch (e: Exception) {
        Log.e(TAG, "secureRandomBytes failed: ${e::class.simpleName} — ${e.message}", e)
        throw e
    }
}
