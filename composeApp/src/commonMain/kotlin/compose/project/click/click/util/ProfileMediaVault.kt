package compose.project.click.click.util

/** Stable filename stem for on-disk profile media vault entries. */
fun profileMediaVaultId(cacheKey: String): String {
    var hash = 2_166_136_261u
    for (ch in cacheKey) {
        hash = hash xor ch.code.toUInt()
        hash *= 16_777_619u
    }
    return hash.toString(16)
}

/** Read decrypted profile media bytes from the on-device vault, if present. */
expect fun readProfileMediaVaultBytes(vaultId: String, extension: String): ByteArray?

/** Persist decrypted profile media bytes to the on-device vault. */
expect fun writeProfileMediaVaultBytes(vaultId: String, bytes: ByteArray, extension: String): Boolean

/** Local filesystem path for a vaulted profile media file when it already exists. */
expect fun profileMediaVaultLocalPath(vaultId: String, extension: String): String?
