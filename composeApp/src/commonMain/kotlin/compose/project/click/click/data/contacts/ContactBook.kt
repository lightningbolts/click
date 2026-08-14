package compose.project.click.click.data.contacts // pragma: allowlist secret

/**
 * Platform address-book reader. Hashes are computed on-device; plaintext never leaves the actual.
 */
expect object ContactBook {
    suspend fun hashedContacts(): List<String>
}
