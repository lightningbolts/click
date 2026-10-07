package compose.project.click.click.data.models

/** Send Later time rules shared with iOS `ScheduleSendSheet` and enforced again by click-web. */
object ScheduleSendTiming {
    const val MIN_LEAD_MS: Long = 60_000L
    const val MAX_AHEAD_MS: Long = 365L * 24 * 60 * 60 * 1000
    private const val QUARTER_HOUR_MS: Long = 15 * 60 * 1000L

    /** The next quarter hour that is at least 15 minutes away. */
    fun defaultSendAt(nowEpochMs: Long): Long {
        val earliest = nowEpochMs + QUARTER_HOUR_MS
        val remainder = earliest % QUARTER_HOUR_MS
        return if (remainder == 0L) earliest else earliest + (QUARTER_HOUR_MS - remainder)
    }

    fun isValid(
        sendAtEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean = sendAtEpochMs >= nowEpochMs + MIN_LEAD_MS && sendAtEpochMs <= nowEpochMs + MAX_AHEAD_MS

    fun clamp(
        sendAtEpochMs: Long,
        nowEpochMs: Long,
    ): Long = sendAtEpochMs.coerceIn(nowEpochMs + MIN_LEAD_MS, nowEpochMs + MAX_AHEAD_MS)
}
