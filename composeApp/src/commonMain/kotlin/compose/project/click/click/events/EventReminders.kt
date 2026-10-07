package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminder // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminderScheduler // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminderTarget // pragma: allowlist secret
import kotlinx.datetime.Instant

/** An event the viewer should be reminded about. */
data class EventReminderSubject(
    val beaconId: String,
    val title: String,
    val startEpochMs: Long,
    val place: String?,
)

/**
 * Local reminders 60 and 15 minutes before events the viewer is going to or saved
 * (iOS `EventReminderScheduler`, spec §59). They honour Alerts → Event reminders, never prompt
 * for permission, and open the event when tapped.
 */
object EventReminders {
    val OFFSETS_MINUTES: List<Int> = listOf(60, 15)

    /** Group prefix shared by every event reminder id, for cancel-all. */
    const val GROUP: String = "event"

    fun reminderId(
        beaconId: String,
        minutes: Int,
    ): String = "$GROUP.$beaconId.$minutes"

    /** Fire times still in the future, as minutes-before to epoch ms. */
    fun triggers(
        startEpochMs: Long,
        nowEpochMs: Long,
    ): List<Pair<Int, Long>> =
        OFFSETS_MINUTES.mapNotNull { minutes ->
            val at = startEpochMs - minutes * 60_000L
            if (at > nowEpochMs) minutes to at else null
        }

    fun body(
        minutes: Int,
        place: String?,
    ): String {
        val lead = if (minutes == 60) "Starts in an hour" else "Starts in $minutes minutes"
        val where = place?.trim()?.takeIf { it.isNotEmpty() }
        return if (where == null) lead else "$lead · $where"
    }

    /**
     * Events worth reminding about: RSVP'd going ([goingBeaconIds], resolved through [beaconById]) or
     * saved ([bookmarks], which carry their own start). Unknown starts are skipped.
     */
    fun subjects(
        goingBeaconIds: Set<String>,
        bookmarks: List<EventBookmarkItemDto>,
        beaconById: (String) -> MapBeacon?,
    ): List<EventReminderSubject> {
        val byId = linkedMapOf<String, EventReminderSubject>()
        goingBeaconIds.forEach { id ->
            val beacon = beaconById(id) ?: return@forEach
            val start = beacon.eventSchedule()?.startEpochMs ?: return@forEach
            byId[id] =
                EventReminderSubject(
                    beaconId = id,
                    title =
                        beacon.metadata.title
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() } ?: "Your event",
                    startEpochMs = start,
                    place = beacon.metadata.locationName,
                )
        }
        bookmarks.forEach { bookmark ->
            if (bookmark.beaconId in byId) return@forEach
            val start =
                bookmark.eventStartAt
                    ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
                    ?: return@forEach
            byId[bookmark.beaconId] =
                EventReminderSubject(
                    beaconId = bookmark.beaconId,
                    title = bookmark.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Saved event",
                    startEpochMs = start,
                    place = bookmark.locationName,
                )
        }
        return byId.values.toList()
    }

    fun reminders(
        subject: EventReminderSubject,
        nowEpochMs: Long,
    ): List<LocalReminder> =
        triggers(subject.startEpochMs, nowEpochMs).map { (minutes, at) ->
            LocalReminder(
                id = reminderId(subject.beaconId, minutes),
                fireAtEpochMs = at,
                title = subject.title,
                body = body(minutes, subject.place),
                target = LocalReminderTarget.Event(subject.beaconId),
            )
        }

    /** Replaces every pending event reminder with the ones [subjects] need now. */
    fun sync(
        subjects: List<EventReminderSubject>,
        enabled: Boolean,
        nowEpochMs: Long,
    ) {
        cancelAll()
        if (!enabled) return
        subjects.flatMap { reminders(it, nowEpochMs) }.forEach(LocalReminderScheduler::schedule)
    }

    fun cancel(beaconId: String) {
        OFFSETS_MINUTES.forEach { LocalReminderScheduler.cancel(reminderId(beaconId, it)) }
    }

    /** Alerts → Event reminders off, or sign-out. */
    fun cancelAll() {
        LocalReminderScheduler.cancelGroup(GROUP)
    }
}
