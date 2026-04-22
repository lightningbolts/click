package compose.project.click.click.ui.chat

/**
 * Persist decrypted attachment bytes to a platform-appropriate, user-visible location
 * (Phase 2 — C6). Returns a human-readable path / URL on success, or `null` on failure
 * (full-disk, permission revoked, etc). Implementations MUST NEVER leak the path into
 * shared logs — treat the return value as PII.
 *
 * * Android: saves into the app's `Downloads/Click` folder using `MediaStore.Downloads`
 *   on API 29+, or a scoped `getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` fallback.
 * * iOS: writes to the app sandbox Documents directory and returns a `file://` URL suitable
 *   for a follow-up `UIActivityViewController` share sheet (caller decides whether to open).
 */
expect fun saveDecryptedAttachmentToDownloads(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): String?
