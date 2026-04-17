package compose.project.click.click.chat.attachments

/**
 * Client-side validation for arbitrary chat attachments (Phase 2 — B3).
 *
 * Rules (must match `click-web/lib/chat/attachmentValidator.ts` byte-for-byte):
 *   * Hard size ceiling: [MAX_ATTACHMENT_BYTES] bytes (2 MiB).
 *   * Allow-list of extensions: pdf, docx, txt, png, jpg, jpeg, mov, mp4, zip, csv.
 *   * Allow-list of MIME types: keyed from the extension list above.
 *   * Explicit blocklist of executable extensions — enforced even if the MIME claim is benign,
 *     since browsers / OSes can be tricked into renaming an attachment at download time.
 *
 * All attachments are E2EE, but we still gate the payload surface: a malicious peer must not be
 * able to ship a native binary to a connection and rely on the recipient's OS to execute it.
 */
object ChatAttachmentValidator {

    /** 2 MiB in bytes. */
    const val MAX_ATTACHMENT_BYTES: Long = 2L * 1024L * 1024L

    /** Extension allow-list (lowercase, no leading dot). */
    val ALLOWED_EXTENSIONS: Set<String> = setOf(
        "pdf",
        "docx",
        "txt",
        "png",
        "jpg",
        "jpeg",
        "mov",
        "mp4",
        "zip",
        "csv",
    )

    /** MIME allow-list — mapped from [ALLOWED_EXTENSIONS]. */
    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "image/png",
        "image/jpeg",
        "video/quicktime",
        "video/mp4",
        "application/zip",
        "application/x-zip-compressed",
        "text/csv",
        "application/csv",
    )

    /**
     * Belt-and-suspenders executable blocklist. Any filename whose extension matches one of these
     * is rejected even if somehow the MIME claim slipped past [ALLOWED_MIME_TYPES].
     */
    val BLOCKED_EXTENSIONS: Set<String> = setOf(
        "exe", "apk", "sh", "bat", "cmd", "com", "scr", "msi", "dll", "jar",
        "js", "vbs", "ps1", "bin", "deb", "dmg", "app", "rpm",
    )

    sealed class Result {
        object Ok : Result()
        data class Invalid(val reason: Reason, val message: String) : Result()
    }

    enum class Reason {
        EMPTY,
        TOO_LARGE,
        BLOCKED_EXTENSION,
        DISALLOWED_EXTENSION,
        DISALLOWED_MIME,
        MISSING_FILENAME,
    }

    /**
     * Validate an attachment **before** encryption. Size is passed as a `Long` so callers on
     * 32-bit targets don't overflow on pathological inputs.
     */
    fun validate(
        fileName: String?,
        mimeType: String?,
        sizeBytes: Long,
    ): Result {
        val trimmedName = fileName?.trim().orEmpty()
        if (trimmedName.isEmpty()) {
            return Result.Invalid(Reason.MISSING_FILENAME, "Attachment is missing a filename.")
        }

        if (sizeBytes <= 0L) {
            return Result.Invalid(Reason.EMPTY, "Attachment is empty.")
        }

        if (sizeBytes > MAX_ATTACHMENT_BYTES) {
            return Result.Invalid(
                Reason.TOO_LARGE,
                "Attachment exceeds the 2 MB size limit.",
            )
        }

        val extension = extensionOf(trimmedName)
        if (extension == null) {
            return Result.Invalid(
                Reason.DISALLOWED_EXTENSION,
                "Attachment has no recognizable extension.",
            )
        }

        if (BLOCKED_EXTENSIONS.contains(extension)) {
            return Result.Invalid(
                Reason.BLOCKED_EXTENSION,
                "Executable attachments are not permitted.",
            )
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.Invalid(
                Reason.DISALLOWED_EXTENSION,
                "Attachments of type .$extension are not supported.",
            )
        }

        val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
        if (normalizedMime.isNotEmpty() && !ALLOWED_MIME_TYPES.contains(normalizedMime)) {
            return Result.Invalid(
                Reason.DISALLOWED_MIME,
                "MIME type $normalizedMime is not supported.",
            )
        }

        return Result.Ok
    }

    /** Lowercase extension (no dot) or `null` if the filename has no extension. */
    fun extensionOf(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return null
        val ext = fileName.substring(dot + 1).lowercase()
        if (ext.isBlank() || ext.any { it == '/' || it == '\\' }) return null
        return ext
    }
}
