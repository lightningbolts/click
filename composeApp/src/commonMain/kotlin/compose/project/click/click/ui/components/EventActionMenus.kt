@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.events.EventCalendarTarget // pragma: allowlist secret
import compose.project.click.click.events.EventDirectionsLinks // pragma: allowlist secret
import compose.project.click.click.events.eventCalendarEntry // pragma: allowlist secret
import compose.project.click.click.events.eventCopyAddressText // pragma: allowlist secret
import compose.project.click.click.events.googleCalendarUrl // pragma: allowlist secret
import compose.project.click.click.events.outlookCalendarUrl // pragma: allowlist secret
import compose.project.click.click.platform.EventPlatformActions // pragma: allowlist secret

/** A filled action tile (Luma-style row under the event's "when"; iOS `EventActionTile`). */
@Composable
fun EventActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionSheet(
    title: String,
    onDismiss: () -> Unit,
    rows: List<Triple<String, ImageVector, () -> Unit>>,
) {
    ClickFormBottomSheet(onDismissRequest = onDismiss, expandable = false) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            rows.forEach { (label, icon, action) ->
                BentoGlassOptionRow(
                    title = label,
                    onClick = {
                        onDismiss()
                        action()
                    },
                    leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** "Add to" — device calendar, Google Calendar or Outlook (sheet 08 G3-19). */
@Composable
fun EventCalendarSheet(
    beacon: MapBeacon,
    title: String,
    onDismiss: () -> Unit,
    openUri: (String) -> Unit,
) {
    val entry = eventCalendarEntry(beacon, title)
    if (entry == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }
    ActionSheet(
        title = "Add to",
        onDismiss = onDismiss,
        rows =
            EventCalendarTarget.entries.map { target ->
                val icon =
                    when (target) {
                        EventCalendarTarget.DEVICE -> Icons.Filled.PhoneAndroid
                        EventCalendarTarget.GOOGLE -> Icons.Filled.Event
                        EventCalendarTarget.OUTLOOK -> Icons.Filled.Mail
                    }
                Triple(target.label, icon) {
                    when (target) {
                        EventCalendarTarget.DEVICE ->
                            if (!EventPlatformActions.insertCalendarEvent(entry)) openUri(googleCalendarUrl(entry))
                        EventCalendarTarget.GOOGLE -> openUri(googleCalendarUrl(entry))
                        EventCalendarTarget.OUTLOOK -> openUri(outlookCalendarUrl(entry))
                    }
                }
            },
    )
}

/**
 * "Get directions": Google Maps, another maps app, the Click Map (event page only) and Copy
 * Address. Never sends the user's own location (sheet 08 G3-20).
 */
@Composable
fun EventDirectionsSheet(
    beacon: MapBeacon,
    label: String?,
    onDismiss: () -> Unit,
    openUri: (String) -> Unit,
    onShowOnClickMap: (() -> Unit)? = null,
) {
    val lat = beacon.latitude
    val lng = beacon.longitude
    val rows =
        buildList {
            add(
                Triple<String, ImageVector, () -> Unit>("Google Maps", Icons.Filled.Navigation) {
                    if (!EventPlatformActions.openUri(EventDirectionsLinks.googleNavigation(lat, lng))) {
                        openUri(EventDirectionsLinks.googleMapsWeb(lat, lng))
                    }
                },
            )
            add(
                Triple("Other maps app", Icons.Filled.Directions) {
                    if (!EventPlatformActions.openGeoChooser(EventDirectionsLinks.geo(lat, lng, label))) {
                        openUri(EventDirectionsLinks.googleMapsWeb(lat, lng))
                    }
                },
            )
            onShowOnClickMap?.let { add(Triple("Show on Click Map", Icons.Filled.Map, it)) }
            add(
                Triple("Copy Address", Icons.Filled.ContentCopy) {
                    EventPlatformActions.copyToClipboard("Address", eventCopyAddressText(beacon))
                    PlatformHapticsPolicy.lightImpact()
                },
            )
        }
    ActionSheet(title = "Get directions", onDismiss = onDismiss, rows = rows)
}

/** Icon for a Calendar tile. */
val EventCalendarIcon: ImageVector get() = Icons.Filled.CalendarMonth
