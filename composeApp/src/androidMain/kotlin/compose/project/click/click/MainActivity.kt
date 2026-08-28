@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click // pragma: allowlist secret

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.maps.MapsInitializer
import compose.project.click.click.calendar.initCalendarProvider // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.contacts.initContactBook // pragma: allowlist secret
import compose.project.click.click.data.storage.initTokenStorage // pragma: allowlist secret
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.encounter.initEncounterTetherWidgetBridge // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.notifications.ChatNotificationDismisser // pragma: allowlist secret
import compose.project.click.click.notifications.initPushNotificationService // pragma: allowlist secret
import compose.project.click.click.platform.AndroidActivityRuntime // pragma: allowlist secret
import compose.project.click.click.qr.toHubIdFromClickHubUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppSystemSettings // pragma: allowlist secret
import compose.project.click.click.ui.utils.initAppSystemSettings // pragma: allowlist secret
import compose.project.click.click.utils.initLocationService // pragma: allowlist secret
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        unlockHighestRefreshRate()

        MapsInitializer.initialize(applicationContext)

        passSupabaseAuthDeepLink(intent)

        AndroidChatImageSaveContext.applicationContext = applicationContext

        initTokenStorage(applicationContext)
        initLocationService(applicationContext)
        initCalendarProvider(applicationContext)
        initContactBook(applicationContext)
        initEncounterTetherWidgetBridge(applicationContext)
        initAppSystemSettings(applicationContext)
        AppSystemSettings.isDebugMode =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        AndroidActivityRuntime.init(applicationContext, this)
        initPushNotificationService(applicationContext, this)

        handleChatDeepLinkIntent(intent)
        handleEventPushDeepLinkIntent(intent)
        handleCommunityHubViewIntent(intent)
        handleEventUniversalLinkIntent(intent)
        handleConnectionUniversalLinkIntent(intent)

        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidActivityRuntime.init(applicationContext, this)
        compose.project.click.click.notifications.AndroidPushNotificationRuntime // pragma: allowlist secret
            .setAppInForeground(true)
        onApplicationDidBecomeActive()
    }

    override fun onPause() {
        compose.project.click.click.notifications.AndroidPushNotificationRuntime // pragma: allowlist secret
            .setAppInForeground(false)
        onApplicationDidEnterBackground()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        passSupabaseAuthDeepLink(intent)
        handleChatDeepLinkIntent(intent)
        handleEventPushDeepLinkIntent(intent)
        handleCommunityHubViewIntent(intent)
        handleEventUniversalLinkIntent(intent)
        handleConnectionUniversalLinkIntent(intent)
    }

    private fun handleCommunityHubViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uriString = intent.dataString ?: return
        val hubId = uriString.toHubIdFromClickHubUrl() ?: return
        ChatDeepLinkManager.setPendingCommunityHub(hubId)
    }

    private fun handleEventUniversalLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uriString = intent.dataString ?: return
        EventDeepLinkRouter.handleIncomingUrl(uriString)
    }

    private fun handleConnectionUniversalLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uriString = intent.dataString ?: return
        ConnectionDeepLinkRouter.handleIncomingUrl(uriString)
    }

    /** Required by supabase-kt: forwards `click://login` OAuth callbacks into the Auth plugin. */
    private fun passSupabaseAuthDeepLink(intent: Intent?) {
        if (intent == null) return
        runCatching { SupabaseConfig.client.handleDeeplinks(intent) }
    }

    private fun handleChatDeepLinkIntent(intent: Intent?) {
        if (intent?.action != ACTION_VIEW_CHAT) return
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val connectionId = intent.getStringExtra(EXTRA_CHAT_CONNECTION_ID)
        val deepLinkId = connectionId?.takeIf { it.isNotBlank() } ?: chatId ?: return
        ChatNotificationDismisser.dismissForThread(
            chatId = chatId.orEmpty(),
            connectionId = connectionId.orEmpty(),
        )
        ChatDeepLinkManager.setPendingChat(deepLinkId)
    }

    private fun handleEventPushDeepLinkIntent(intent: Intent?) {
        if (intent?.action != ACTION_VIEW_EVENT) return
        val beaconId = intent.getStringExtra(EXTRA_EVENT_BEACON_ID)?.trim().orEmpty()
        if (beaconId.isNotEmpty()) {
            EventDeepLinkRouter.setPendingBeaconId(beaconId)
        }
    }

    /**
     * Prefer the display's highest refresh mode (90/120Hz) so Compose scroll/animation
     * is not stuck on the default 60Hz mode many OEMs leave unset until requested.
     */
    private fun unlockHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
        val best = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes =
            window.attributes.apply {
                preferredDisplayModeId = best.modeId
            }
    }

    companion object {
        const val ACTION_VIEW_CHAT = "compose.project.click.click.action.VIEW_CHAT" // pragma: allowlist secret
        const val ACTION_VIEW_EVENT = "compose.project.click.click.action.VIEW_EVENT" // pragma: allowlist secret

        private const val EXTRA_CHAT_ID = "extra_chat_id"
        private const val EXTRA_CHAT_CONNECTION_ID = "extra_chat_connection_id"
        private const val EXTRA_EVENT_BEACON_ID = "extra_event_beacon_id"

        fun createChatDeepLinkIntent(
            context: Context,
            chatId: String = "",
            connectionId: String = "",
        ): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_CHAT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CHAT_CONNECTION_ID, connectionId)
            }

        fun createEventDeepLinkIntent(
            context: Context,
            beaconId: String,
        ): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_EVENT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_EVENT_BEACON_ID, beaconId)
            }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
