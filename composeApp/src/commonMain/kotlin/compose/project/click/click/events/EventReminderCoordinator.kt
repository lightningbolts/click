package compose.project.click.click.events

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.parseMapBeaconMetadata
import kotlinx.datetime.Clock

data class HomeEventReminder(
    val beaconId: String,
    val description: String,
    val title: String? = null,
    val kind: EventReminderKind,
    val startEpochMs: Long,
)

/** Shared in-memory index of event beacons for home + notification surfaces. */
object EventReminderCoordinator {
    private val beaconsById = linkedMapOf<String, MapBeacon>()

    fun syncBeacons(beacons: List<MapBeacon>) {
        beacons.filter { it.kind == MapBeaconKind.EVENT }.forEach { beacon ->
            beaconsById[beacon.id] = beacon
        }
    }

    fun rememberBeacon(beacon: MapBeacon) {
        if (beacon.kind == MapBeaconKind.EVENT) {
            beaconsById[beacon.id] = beacon
        }
    }

    fun homeReminders(
        rsvpBeaconIds: Set<String>,
        userId: String?,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
        dismissedKeys: Set<String> = emptySet(),
    ): List<HomeEventReminder> {
        val reminders = mutableListOf<HomeEventReminder>()
        for ((id, beacon) in beaconsById) {
            val schedule = beacon.eventSchedule() ?: continue
            if (schedule.isEnded(nowEpochMs)) continue
            val interested = id in rsvpBeaconIds || beacon.createdByUserId == userId
            if (!interested) continue
            val description = beacon.metadata.description.orEmpty()
            val title = beacon.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
            for (kind in eventReminderKindsDue(schedule, nowEpochMs)) {
                val key = "$id:${kind.name}"
                if (key in dismissedKeys) continue
                reminders += HomeEventReminder(
                    beaconId = id,
                    description = description,
                    title = title,
                    kind = kind,
                    startEpochMs = schedule.startEpochMs,
                )
            }
        }
        return reminders.sortedBy { it.startEpochMs }
    }
}

fun MapBeacon.eventSchedule(): EventSchedule? =
    parseEventScheduleFromMetadata(metadata.raw)

fun MapBeacon.isVisibleEventBeacon(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
    if (kind != MapBeaconKind.EVENT) return true
    eventSchedule()?.let { return it.isVisible(nowEpochMs) }
    val exp = expiresAtEpochMs ?: return true
    return exp > nowEpochMs
}

/** Discovery feed + map merge visibility (events use schedule end, not raw TTL alone). */
fun MapBeacon.isActiveForDiscoveryFeed(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
    return when (kind) {
        MapBeaconKind.EVENT -> isVisibleEventBeacon(nowEpochMs)
        else -> {
            val exp = expiresAtEpochMs
            exp == null || exp > nowEpochMs
        }
    }
}
