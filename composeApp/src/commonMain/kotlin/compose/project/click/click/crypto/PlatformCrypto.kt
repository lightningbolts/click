package compose.project.click.click.crypto

/**
 * Platform-specific cryptographic primitives used by [MessageCrypto].
 *
 * Android  → javax.crypto + java.security
 * iOS      → CommonCrypto (CCCrypt / CC_SHA256 / CCHmac)
 */
expect object PlatformCrypto {
    fun sha256(data: ByteArray): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    fun aesCbcEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray
    fun aesCbcDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray
    fun secureRandomBytes(count: Int): ByteArray
}
