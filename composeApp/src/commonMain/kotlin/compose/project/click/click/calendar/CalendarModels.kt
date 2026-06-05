package compose.project.click.click.calendar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A contiguous busy interval from the device calendar (epoch milliseconds, UTC). */
@Serializable
data class BusyBlock(
    @SerialName("start_ms") val startEpochMs: Long,
    @SerialName("end_ms") val endEpochMs: Long,
) {
    init {
        require(startEpochMs < endEpochMs) { "BusyBlock start must be before end" }
    }
}

/** Free/busy snapshot for a rolling window (typically the next 7 days). */
@Serializable
data class CalendarFreeBusy(
    @SerialName("busy_blocks") val busyBlocks: List<BusyBlock>,
    @SerialName("window_start_ms") val windowStartEpochMs: Long,
    @SerialName("window_end_ms") val windowEndEpochMs: Long,
)

/** Mutual free gap where both calendars are open for more than [minDurationMs]. */
data class AvailabilityOverlapGap(
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    val durationMs: Long get() = endEpochMs - startEpochMs
}

enum class CalendarAccessStatus {
    Granted,
    Denied,
    NotDetermined,
    Restricted,
}

@Serializable
data class CalendarFreeBusyBroadcast(
    @SerialName("user_id") val userId: String,
    @SerialName("busy_blocks") val busyBlocks: List<BusyBlock>,
    @SerialName("window_start_ms") val windowStartEpochMs: Long,
    @SerialName("window_end_ms") val windowEndEpochMs: Long,
)
