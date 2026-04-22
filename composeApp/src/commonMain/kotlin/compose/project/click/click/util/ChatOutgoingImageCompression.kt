package compose.project.click.click.util

/** Long edge cap before encryption / upload (matches product expectation for chat photos). */
internal const val MAX_OUTGOING_CHAT_IMAGE_PX = 1080

/** JPEG quality 1–100 for re-encoded outgoing chat images. */
internal const val OUTGOING_CHAT_JPEG_QUALITY_PERCENT = 80

/**
 * Decode [bytes] as a bitmap image when possible, downscale so the longer side is at most
 * [MAX_OUTGOING_CHAT_IMAGE_PX], and re-encode as JPEG at [OUTGOING_CHAT_JPEG_QUALITY_PERCENT].
 * Returns original [bytes] if decoding fails (caller may still upload as opaque bytes).
 */
expect suspend fun compressOutgoingChatImageForUpload(bytes: ByteArray, mimeType: String): ByteArray
