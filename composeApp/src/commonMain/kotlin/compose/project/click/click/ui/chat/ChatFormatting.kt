package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Message
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure formatting helpers shared across the conversation UI.
 *
 * Extracted verbatim from ConnectionsScreen.kt so they're unit-testable
 * and so the screen file shrinks without changing behavior. None of
 * these touch the Supabase client, realtime channels, or LiveKit — they
 * are safe to call from any thread and from previews.
 */

/** Relative "Just now / Xm ago / Xh ago / Xd ago / Xw ago" timestamp used in connection lists. */
internal fun formatConnectionListTimestamp(timestamp: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> "${diff / 604_800_000}w ago"
    }
}

/** 12-hour clock "H:MM AM/PM" for an individual chat message timestamp. */
internal fun formatMessageTime(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = dateTime.hour
    val minute = dateTime.minute.toString().padStart(2, '0')
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    return "$displayHour:$minute $amPm"
}

/** `MM:SS` remaining-time formatter used by the Vibe Check countdown banner. */
internal fun formatVibeCheckTime(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

/** Compact call-duration formatter used in the in-chat `call_log` system row. */
internal fun formatCallDurationForLog(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return if (m > 0) "${m}m ${r.toString().padStart(2, '0')}s" else "${r}s"
}

/**
 * `M:SS` duration label for a voice message bubble. Prefers the live
 * player duration (ms) when it's positive; falls back to the cached
 * `duration_seconds` from message metadata; otherwise returns "0:00".
 */
internal fun formatChatAudioDuration(durationMs: Long, fallbackSec: Int?): String {
    val totalSec = when {
        durationMs > 0 -> (durationMs / 1000).toInt()
        fallbackSec != null && fallbackSec > 0 -> fallbackSec
        else -> 0
    }
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** `M:SS` playhead-position label for voice messages. Negative inputs are clamped to zero. */
internal fun formatChatAudioPositionMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/**
 * Resolves the human-readable label for an in-chat `call_log` system row
 * from the message metadata JSON, plus a flag for whether the label
 * should render in the missed-call accent color.
 *
 * Returns "Call" / false when the metadata is malformed so the UI always
 * has something to show.
 */
internal fun callLogLabel(message: Message): Pair<String, Boolean> {
    val meta = message.metadata as? JsonObject ?: return "Call" to false
    val state = (meta["call_state"] as? JsonPrimitive)?.content ?: return "Call" to false
    val dur = (meta["duration_seconds"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    return when (state) {
        "missed" -> "Missed Voice Call" to true
        "declined" -> "Declined Call" to false
        "completed" -> ("Call Ended • ${formatCallDurationForLog(dur)}") to false
        else -> "Call" to false
    }
}

/** Stable day-bucket key ("YYYY-M-D" in local time) for grouping messages into day separators. */
internal fun messageDayKey(timestamp: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.year}-${dateTime.monthNumber}-${dateTime.dayOfMonth}"
}

/**
 * User-facing day label for a chat-timeline day separator: "Today",
 * "Yesterday", the weekday name within the last week, or `"Mon D"` /
 * `"Mon D, YYYY"` otherwise. `nowMs` is exposed for deterministic tests.
 */
internal fun formatConversationDayLabel(
    timestamp: Long,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val zone = TimeZone.currentSystemDefault()
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)

    val dayDifference = (now.date.toEpochDays() - dateTime.date.toEpochDays())
    return when {
        dayDifference == 0L -> "Today"
        dayDifference == 1L -> "Yesterday"
        dayDifference < 7L -> {
            dateTime.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        else -> {
            val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            if (dateTime.year == now.year) {
                "$month ${dateTime.dayOfMonth}"
            } else {
                "$month ${dateTime.dayOfMonth}, ${dateTime.year}"
            }
        }
    }
}
