@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import compose.project.click.click.data.places.PlaceOpenRequests // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret

/**
 * The app's one Place page, opened through [PlaceOpenRequests] from map pins, search, events,
 * Me → Places, `/p/{slug}` links and check-in QR codes. Its event rows open the event on the map
 * (the `/e/{id}` path) and its Community row opens the Place Hub (the `click://hub/{id}` path),
 * so the server's hub geofence still applies. iOS pushes `AppRoute.place` the same way.
 */
@Composable
internal fun PlaceSheetHost() {
    val request by PlaceOpenRequests.pending.collectAsState()
    val current = request ?: return
    key(current) {
        PlaceDetailSheet(
            idOrSlug = current.idOrSlug,
            source = current.source,
            anchorToken = current.anchorToken,
            onDismiss = { PlaceOpenRequests.dismiss() },
            onOpenEvent = { beaconId ->
                PlaceOpenRequests.dismiss()
                EventDeepLinkRouter.setPendingBeaconId(beaconId)
            },
            onOpenHub = { hubId ->
                PlaceOpenRequests.dismiss()
                ChatDeepLinkManager.setPendingCommunityHub(hubId)
            },
        )
    }
}
