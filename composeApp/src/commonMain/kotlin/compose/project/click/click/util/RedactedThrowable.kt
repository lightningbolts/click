package compose.project.click.click.util // pragma: allowlist secret

/**
 * Supabase [RestException] messages often embed URL, Http Method, and Headers including
 * Bearer tokens and apikey. Never log [Throwable.message] raw in production.
 */
fun Throwable.redactedRestMessage(): String {
    val raw = message ?: return this::class.simpleName ?: "Error"
    val idxNewlineUrl = raw.indexOf("\nURL:")
    val idxSpaceUrl = raw.indexOf(" URL:")
    val cut = when {
        idxNewlineUrl >= 0 -> idxNewlineUrl
        idxSpaceUrl >= 0 -> idxSpaceUrl
        else -> -1
    }
    return (if (cut >= 0) raw.substring(0, cut) else raw).trim().take(400)
}
