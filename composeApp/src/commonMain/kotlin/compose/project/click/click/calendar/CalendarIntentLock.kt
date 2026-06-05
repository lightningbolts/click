package compose.project.click.click.calendar

import compose.project.click.click.data.models.AvailabilityIntentInsert
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.datetime.Instant

suspend fun lockAvailabilityIntentForGap(
    repository: SupabaseRepository,
    userId: String,
    gap: AvailabilityOverlapGap,
    intentTag: String = "Hang out",
): Boolean {
    if (userId.isBlank()) return false
    val startsIso = Instant.fromEpochMilliseconds(gap.startEpochMs).toString()
    val endsIso = Instant.fromEpochMilliseconds(gap.endEpochMs).toString()
    val durationMinutes = (gap.durationMs / 60_000L).coerceAtLeast(45L)
    val timeframe = when {
        durationMinutes >= 120 -> "${durationMinutes / 60} hours"
        else -> "$durationMinutes min"
    }
    val result = repository.insertAvailabilityIntent(
        AvailabilityIntentInsert(
            userId = userId,
            intentTag = intentTag,
            timeframe = timeframe,
            startsAt = startsIso,
            endsAt = endsIso,
            expiresAt = endsIso,
        ),
    )
    if (result.success) {
        repository.syncUserAvailabilityProfileMirror(userId)
    }
    return result.success
}
