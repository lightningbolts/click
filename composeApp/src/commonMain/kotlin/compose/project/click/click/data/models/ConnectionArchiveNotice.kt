package compose.project.click.click.data.models // pragma: allowlist secret

private const val TWELVE_H_MS = 12L * 60 * 60 * 1000

data class ConnectionArchiveNotice(
    val headline: String,
    val body: String,
    val urgent: Boolean,
)

/**
 * Banner copy for connections in the 48h "Say Hi" window or the 7-day idle window.
 * Returns null when no warning should be shown.
 */
fun Connection.archiveNotice(nowMs: Long): ConnectionArchiveNotice? {
    if (!isVisibleInActiveUi()) return null
    if (isKept()) return null

    if (isPending() && last_message_at == null) {
        val remaining = getPendingRemainingMs(nowMs)
        if (remaining <= 0L) return null
        val urgent = remaining <= TWELVE_H_MS
        return ConnectionArchiveNotice(
            headline = if (urgent) "Say hi soon" else "New connection",
            body = formatRemainingLabel(remaining, "until this connection is archived if no one sends a message"),
            urgent = urgent,
        )
    }

    if (isActive()) {
        val remaining = getIdleArchiveRemainingMs(nowMs)
        if (remaining <= 0L || remaining == Long.MAX_VALUE) return null
        val urgent = remaining <= TWELVE_H_MS
        return ConnectionArchiveNotice(
            headline = if (urgent) "Reconnect soon" else "Stay in touch",
            body = formatRemainingLabel(remaining, "until this chat may archive without new messages"),
            urgent = urgent,
        )
    }

    return null
}

/**
 * Single banner for dashboard: most urgent expiring connection in the list.
 */
fun Iterable<Connection>.mostUrgentArchiveNotice(nowMs: Long): ConnectionArchiveNotice? {
    data class Scored(val notice: ConnectionArchiveNotice, val remainingMs: Long)
    val scored = mapNotNull { conn ->
        val notice = conn.archiveNotice(nowMs) ?: return@mapNotNull null
        val remaining = when {
            conn.isPending() && conn.last_message_at == null -> conn.getPendingRemainingMs(nowMs)
            conn.isActive() -> conn.getIdleArchiveRemainingMs(nowMs).takeIf { it != Long.MAX_VALUE } ?: Long.MAX_VALUE
            else -> Long.MAX_VALUE
        }
        Scored(notice, remaining)
    }
    if (scored.isEmpty()) return null
    return scored.minWith(
        compareBy<Scored> { !it.notice.urgent }.thenBy { it.remainingMs },
    ).notice
}

private fun formatRemainingLabel(remainingMs: Long, suffix: String): String {
    val totalMinutes = (remainingMs / 60_000).coerceAtLeast(1L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timePart = when {
        hours >= 48 -> "${hours / 24} day(s)"
        hours >= 1 -> "${hours}h ${minutes}m".trimEnd()
        else -> "$minutes min"
    }
    return "About $timePart left $suffix."
}
