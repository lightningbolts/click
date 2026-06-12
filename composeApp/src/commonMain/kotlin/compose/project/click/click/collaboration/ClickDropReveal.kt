package compose.project.click.click.collaboration

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus

/** Reveal TTL for Click Drops: 24 hours after the user sends the photo. */
fun computeClickDropRevealTtlIso(now: Instant = Clock.System.now()): String =
    now.plus(24, DateTimeUnit.HOUR).toString()

/** Milliseconds from [now] until Click Drop reveal. */
fun clickDropRevealDelayMs(now: Instant = Clock.System.now()): Long {
    val revealAt = runCatching { Instant.parse(computeClickDropRevealTtlIso(now)) }.getOrNull()
        ?: return 24L * 60L * 60_000L
    return (revealAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
}
