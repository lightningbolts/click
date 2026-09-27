package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.events.EventReminders // pragma: allowlist secret
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.datetime.Clock

/** Rebuilds the T-60 / T-15 event reminders from the RSVP cache, saved events and the Alerts preference. */
internal suspend fun AppDataManager.resyncEventReminders() {
    val userId = _currentUser.value?.id ?: return
    val going =
        BeaconRsvpPersistence
            .load(tokenStorage, userId)
            .filterValues { it.currentUserSignedUp }
            .keys
    val subjects = EventReminders.subjects(going, _cachedEventBookmarks.value, EventReminderCoordinator::beaconById)
    EventReminders.sync(
        subjects = subjects,
        enabled = _notificationPreferences.value.eventReminderPushEnabled,
        nowEpochMs = Clock.System.now().toEpochMilliseconds(),
    )
}

/**
 * Runs for the signed-in session: re-syncs on start, after RSVP / save changes (the RSVP cache is
 * persisted asynchronously, hence the debounce), and when Alerts → Event reminders changes.
 */
@OptIn(FlowPreview::class)
suspend fun AppDataManager.runEventReminderSync() {
    merge(
        eventEngagementVersion,
        cachedEventBookmarks.map { },
        notificationPreferences.map { it.eventReminderPushEnabled }.distinctUntilChanged().map { },
    ).debounce(1_000L)
        .collect { runCatching { resyncEventReminders() } }
}
