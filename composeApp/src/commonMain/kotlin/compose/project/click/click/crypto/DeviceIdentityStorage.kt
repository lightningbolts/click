package compose.project.click.click.crypto

/** Public portion of a locally persisted v2 device identity. */
data class DeviceIdentityInfo(
    val deviceId: String,
    val publicKeySpkiBase64: String,
    val cryptoVersion: Int = MessageCryptoV2.CRYPTO_VERSION,
)

/** Opaque platform-owned X25519 identity. Private key material is never exposed here. */
expect class DeviceIdentity {
    val info: DeviceIdentityInfo
}

/** Per-installation X25519 identity storage and key-agreement boundary. */
expect object DeviceIdentityStorage {
    fun loadOrCreate(): DeviceIdentity
    fun generateEphemeral(): DeviceIdentity
    fun deriveSharedSecret(identity: DeviceIdentity, peerPublicKeySpkiBase64: String): ByteArray
    fun destroyEphemeral(identity: DeviceIdentity)
}
