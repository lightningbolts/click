package compose.project.click.click.notifications // pragma: allowlist secret

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import compose.project.click.click.R // pragma: allowlist secret

/**
 * Notification channels (G1-4). Android channels are immutable once created, so the Click sound
 * (`res/raw/click`, from iOS `click.caf`) needs new channel IDs: the v2 channels take over the
 * user's importance from the old ones, which are then deleted.
 *
 * The sound ships as `click.wav` (PCM) rather than the `click.ogg` the parity sheet names: no
 * transcoder was available to convert the `.caf`, and channels play WAV natively. Swapping in an
 * Ogg file later keeps the resource name `click`, so nothing else changes.
 */
internal object ClickNotificationChannels {
    const val MESSAGES = "click_messages_v2"
    const val PLANS_EVENTS = "click_plans_events_v2"

    /** Ongoing "event is live" notifications (G3-17): low importance, silent. */
    const val EVENTS_LIVE = "click_events_live"

    internal const val LEGACY_MESSAGES = "click_messages"
    internal const val LEGACY_PLANS_EVENTS = "click_plans_events"

    /** The user's choice on the old channel wins (including "off"); otherwise the default. */
    internal fun migratedImportance(
        legacyImportance: Int?,
        defaultImportance: Int,
    ): Int = legacyImportance ?: defaultImportance

    @Volatile private var ensured = false

    fun ensure(context: Context) {
        if (ensured || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val sound =
            Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.click}")
        val audio =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        fun migrate(
            id: String,
            legacyId: String,
            name: String,
            description: String?,
            defaultImportance: Int,
        ) {
            val legacy = manager.getNotificationChannel(legacyId)
            if (manager.getNotificationChannel(id) == null) {
                val importance = migratedImportance(legacy?.importance, defaultImportance)
                manager.createNotificationChannel(
                    NotificationChannel(id, name, importance).apply {
                        description?.let { this.description = it }
                        setSound(sound, audio)
                        legacy?.let { old ->
                            enableVibration(old.shouldVibrate())
                            setShowBadge(old.canShowBadge())
                            lockscreenVisibility = old.lockscreenVisibility
                        }
                    },
                )
            }
            if (legacy != null) manager.deleteNotificationChannel(legacyId)
        }

        migrate(MESSAGES, LEGACY_MESSAGES, "Click messages", null, NotificationManager.IMPORTANCE_HIGH)
        migrate(
            PLANS_EVENTS,
            LEGACY_PLANS_EVENTS,
            "Plans & events",
            "Reminders for plans you're going to and events you saved.",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        if (manager.getNotificationChannel(EVENTS_LIVE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(EVENTS_LIVE, "Live events", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The event you're at, while it's happening."
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        ensured = true
    }
}
