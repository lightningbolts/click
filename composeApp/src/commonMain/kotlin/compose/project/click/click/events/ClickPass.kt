package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Your Click Pass for an event (`GET /api/beacons/{id}/pass`): the QR the host scans at the door.
 * The credential never changes for an attendee, so it's kept on the device and shows offline
 * (iOS `ClickPass`, `EventEngagementRepository.swift`).
 */
@Serializable
data class ClickPass(
    /** The QR payload: the event's public link carrying the pass (`https://joinclick.co/e/{id}?pass=…`). */
    @SerialName("credential_url") val credentialUrl: String,
    /** "K7P-4QX": under the QR, so the host can tell passes apart. Never a credential. */
    val code: String,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("wallet_available") val walletAvailable: Boolean = false,
) {
    val isCheckedIn: Boolean get() = !checkedInAt.isNullOrBlank()
}

/** What `GET …/pass` answered. */
sealed interface ClickPassFetch {
    data class Loaded(
        val pass: ClickPass,
    ) : ClickPassFetch

    /** 403 `not_going`: no approved RSVP (or it was cancelled). The cached pass is void. */
    data object NotGoing : ClickPassFetch

    /** 503 `pass_unavailable`, a network failure or any other error; a cached pass stays usable. */
    data class Failed(
        val message: String,
    ) : ClickPassFetch
}

/** `POST /api/beacons/{id}/pass/scan` → `result` (always a 200 for managers). */
enum class PassScanResult(
    val wire: String,
) {
    CHECKED_IN("checked_in"),
    ALREADY_CHECKED_IN("already_checked_in"),
    NOT_GOING("not_going"),
    WRONG_EVENT("wrong_event"),
    INVALID("invalid"),
    ;

    companion object {
        fun fromWire(value: String?): PassScanResult? = entries.firstOrNull { it.wire == value?.trim() }
    }
}

@Serializable
data class PassScanAttendee(
    @SerialName("user_id") val userId: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class PassScanResponse(
    val result: String,
    val attendee: PassScanAttendee? = null,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("check_in_count") val checkInCount: Int? = null,
)

data class PassScan(
    val result: PassScanResult,
    val holder: PassScanAttendee?,
    val checkedInAt: String?,
    val checkInCount: Int?,
)

fun PassScanResponse.toPassScan(): PassScan? {
    val parsed = PassScanResult.fromWire(result) ?: return null
    return PassScan(
        result = parsed,
        holder = attendee?.takeIf { it.userId.isNotBlank() },
        checkedInAt = checkedInAt,
        checkInCount = checkInCount,
    )
}

/** A door scan's outcome: the server's answer, or a network failure (retryable at once). */
sealed interface PassScanOutcome {
    data class Scanned(
        val scan: PassScan,
    ) : PassScanOutcome

    data class Failed(
        val message: String,
    ) : PassScanOutcome
}

enum class PassScanTone { SUCCESS, WARNING, ERROR }

data class PassScanLook(
    val title: String,
    val detail: String?,
    val tone: PassScanTone,
)

/** The result card copy (sheet 08 §6 G3-16; iOS `PassScannerView.look`). */
fun passScanLook(
    outcome: PassScanOutcome,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): PassScanLook =
    when (outcome) {
        is PassScanOutcome.Failed -> PassScanLook("Couldn't check this pass", outcome.message, PassScanTone.ERROR)
        is PassScanOutcome.Scanned -> {
            val scan = outcome.scan
            when (scan.result) {
                PassScanResult.CHECKED_IN ->
                    PassScanLook("Checked in", scan.checkInCount?.let { "$it here now" }, PassScanTone.SUCCESS)
                PassScanResult.ALREADY_CHECKED_IN ->
                    PassScanLook(
                        "Already checked in",
                        formatPassClock(scan.checkedInAt, timeZone)?.let { "At $it. Make sure it's them." },
                        PassScanTone.WARNING,
                    )
                PassScanResult.NOT_GOING ->
                    PassScanLook("Not on the list", "Their RSVP isn't active for this event.", PassScanTone.ERROR)
                PassScanResult.WRONG_EVENT -> PassScanLook("Pass for another event", null, PassScanTone.WARNING)
                PassScanResult.INVALID -> PassScanLook("Not a Click Pass", null, PassScanTone.ERROR)
            }
        }
    }

fun passScanHaptic(tone: PassScanTone) {
    when (tone) {
        PassScanTone.SUCCESS -> PlatformHapticsPolicy.successNotification()
        PassScanTone.WARNING -> PlatformHapticsPolicy.lightImpact()
        PassScanTone.ERROR -> PlatformHapticsPolicy.heavyImpact()
    }
}

/** "{N} checked in so far", or the hint before the first scan. */
fun passScannerSubtitle(checkedInCount: Int?): String =
    checkedInCount?.let { "$it checked in so far" } ?: "Guests find it on the event page once they RSVP."

/**
 * A pass held in frame isn't sent twice in a row: the same value is ignored for [windowMs].
 * After a network failure, [reset] lets the same pass be tried again at once.
 */
class PassScanGate(
    private val windowMs: Long = 3_000L,
) {
    private var lastValue: String? = null
    private var lastAtMs: Long = 0L

    fun shouldSend(
        value: String,
        nowMs: Long,
    ): Boolean {
        if (value == lastValue && nowMs - lastAtMs < windowMs) return false
        lastValue = value
        lastAtMs = nowMs
        return true
    }

    fun reset() {
        lastValue = null
        lastAtMs = 0L
    }
}

/** Poll `…/pass` every 4 s while not checked in, the start is within 1 h and the event hasn't ended. */
const val CLICK_PASS_POLL_MS = 4_000L

fun clickPassIsAtTheDoor(
    schedule: EventSchedule?,
    nowMs: Long,
): Boolean {
    schedule ?: return false
    return schedule.startEpochMs - nowMs < 60L * 60_000L && !schedule.isEnded(nowMs)
}

/** The holder row's status (iOS `ClickPassView.status`). */
fun clickPassStatusLine(
    pass: ClickPass,
    live: Boolean,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (pass.isCheckedIn) {
        val at = formatPassClock(pass.checkedInAt, timeZone)
        return if (at != null) "You're in · checked in $at" else "You're in"
    }
    return if (live) "Going · show this at the door" else "Going"
}

/** The event-page card: shown while going and not expired. */
fun clickPassCardSubtitle(pass: ClickPass?): String =
    if (pass?.isCheckedIn == true) "You're checked in" else "Show it at the door"

/** Whether "Scan Click Passes" shows: creator (or Place manager), events only, not expired. */
fun canScanClickPasses(
    isManager: Boolean,
    isEvent: Boolean,
    schedule: EventSchedule?,
    nowMs: Long,
): Boolean = isManager && isEvent && schedule?.isEnded(nowMs) != true

/** Whether "Your Click Pass" shows: going, and the event isn't over. */
fun showsClickPassCard(
    going: Boolean,
    schedule: EventSchedule?,
    nowMs: Long,
): Boolean = going && schedule?.isEnded(nowMs) != true

internal fun formatPassClock(
    iso: String?,
    timeZone: TimeZone,
): String? {
    val ms = iso?.let { compose.project.click.click.data.models.parseEpochMs(it) } ?: return null
    return formatEventClockLabel(Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone))
}
