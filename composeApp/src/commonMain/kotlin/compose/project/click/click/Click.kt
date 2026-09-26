package compose.project.click.click

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter
import compose.project.click.click.deeplink.EventDeepLinkRouter
import compose.project.click.click.telemetry.TelemetryBatcher

/**
 * Called from platform lifecycle when the app returns to the foreground so Compose re-reads
 * location and notification permission state after returning from the system UI.
 */
fun onApplicationDidBecomeActive() {
    notifyPlatformApplicationForeground()
    AppDataManager.handleApplicationForegrounded()
}

/** Called from platform lifecycle when the app moves to background. */
fun onApplicationDidEnterBackground() {
    TelemetryBatcher.onAppBackgrounded()
}

/** Parse and queue a connection URL. Returns true when recognized. */
fun handleConnectionUniversalLink(url: String): Boolean = ConnectionDeepLinkRouter.handleIncomingUrl(url)

/** Parse and queue an event URL. Returns true when recognized. */
fun handleEventUniversalLink(url: String): Boolean = EventDeepLinkRouter.handleIncomingUrl(url)
