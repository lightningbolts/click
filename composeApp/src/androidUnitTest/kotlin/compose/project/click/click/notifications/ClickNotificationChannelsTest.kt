package compose.project.click.click.notifications

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClickNotificationChannelsTest {
    @Test
    fun userImportanceCarriesOverToTheV2Channel() {
        // A user who silenced messages (or turned them off) keeps that choice.
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            ClickNotificationChannels.migratedImportance(NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_HIGH),
        )
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            ClickNotificationChannels.migratedImportance(NotificationManager.IMPORTANCE_NONE, NotificationManager.IMPORTANCE_HIGH),
        )
    }

    @Test
    fun freshInstallUsesTheDefault() {
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            ClickNotificationChannels.migratedImportance(null, NotificationManager.IMPORTANCE_HIGH),
        )
    }

    @Test
    fun v2ChannelIdsDifferFromTheImmutableOldOnes() {
        assertEquals("click_messages_v2", ClickNotificationChannels.MESSAGES)
        assertEquals("click_plans_events_v2", ClickNotificationChannels.PLANS_EVENTS)
        assertEquals("click_events_live", ClickNotificationChannels.EVENTS_LIVE)
        assertNotEquals(ClickNotificationChannels.LEGACY_MESSAGES, ClickNotificationChannels.MESSAGES)
        assertNotEquals(ClickNotificationChannels.LEGACY_PLANS_EVENTS, ClickNotificationChannels.PLANS_EVENTS)
    }
}
