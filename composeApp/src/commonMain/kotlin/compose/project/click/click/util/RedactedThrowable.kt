package compose.project.click.click.util // pragma: allowlist secret

/**
 * Supabase [RestException] messages often embed URL, Http Method, and Headers including
 * Bearer tokens and apikey. Never surface [Throwable.message] raw in logs or UI.
 */
fun Throwable.redactedRestMessage(): String {
    val raw = message ?: return this::class.simpleName ?: "Error"
    val lower = raw.lowercase()
    val markerEnds = listOf(
        "\nurl:",
        " url:",
        "\nheaders:",
        " headers:",
        "headers:",
        "\napikey=",
        " apikey=",
        "\nauthorization:",
        " authorization:",
        "\nbearer ",
        " bearer ",
    )
    var cut = raw.length
    for (m in markerEnds) {
        val i = lower.indexOf(m)
        if (i >= 0) cut = minOf(cut, i)
    }
    val trimmed = raw.substring(0, cut).trim()
    return trimmed.ifEmpty { this::class.simpleName ?: "Error" }.take(400)
}
