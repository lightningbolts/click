package compose.project.click.click.notifications

/**
 * Device-local reminders (plan reminders, event T-60/T-15). They never touch the server: each
 * device schedules its own from state it already has. Mirrors iOS `PlanReminders` /
 * `EventReminderScheduler`, which use `UNUserNotificationCenter`.
 */
data class LocalReminder(
    /** Stable id; scheduling the same id again replaces the pending reminder (e.g. `plan.<messageId>`). */
    val id: String,
    val fireAtEpochMs: Long,
    val title: String,
    val body: String,
    val target: LocalReminderTarget,
)

/** Where tapping the reminder lands. */
sealed interface LocalReminderTarget {
    data class Chat(
        val chatId: String,
        val connectionId: String = "",
    ) : LocalReminderTarget

    data class Event(
        val beaconId: String,
    ) : LocalReminderTarget
}

expect object LocalReminderScheduler {
    /**
     * Schedules (or replaces) [reminder]. Returns false when it can't be delivered: the time has
     * passed, or notifications are not permitted. Never prompts for permission.
     */
    fun schedule(reminder: LocalReminder): Boolean

    fun cancel(id: String)
}

/** Timing rules for plan reminders, shared with iOS `PlanReminders.update`. */
object PlanReminderTiming {
    const val LEAD_MS: Long = 60 * 60 * 1000L
    const val MIN_LEAD_TO_SCHEDULE_MS: Long = 5 * 60 * 1000L
    const val SOON_DELAY_MS: Long = 60 * 1000L

    fun reminderId(messageId: String): String = "plan.$messageId"

    /**
     * When to remind someone who is going: an hour before the start, or in a minute when the plan
     * starts sooner than that. Null when it starts within 5 minutes (a reminder would be noise).
     */
    fun fireAt(
        startsAtEpochMs: Long,
        nowEpochMs: Long,
    ): Long? {
        if (startsAtEpochMs - nowEpochMs <= MIN_LEAD_TO_SCHEDULE_MS) return null
        return maxOf(startsAtEpochMs - LEAD_MS, nowEpochMs + SOON_DELAY_MS)
    }
}
