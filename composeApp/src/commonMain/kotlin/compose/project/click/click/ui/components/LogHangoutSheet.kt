@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatRelativeDay // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.GeocodingService // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** The server accepts hangouts up to 7 days back. */
internal const val LOG_HANGOUT_MAX_BACKDATE_MS: Long = 7L * 24 * 60 * 60 * 1000

/** "Use my current location" only makes sense for a hangout that just happened. */
internal fun canUseCurrentLocationFor(
    occurredAtEpochMs: Long,
    nowEpochMs: Long,
): Boolean = nowEpochMs - occurredAtEpochMs in 0..(3L * 60 * 60 * 1000)

/**
 * Log a hangout that happened without a tap (iOS `LogHangoutSheet`): when (up to 7 days back) and
 * optionally where. The peer is asked to confirm before it reaches your shared timeline.
 */
@Composable
fun LogHangoutSheet(
    peerFirstName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLog: (occurredAtEpochMs: Long, placeName: String?, latitude: Double?, longitude: Double?) -> Unit,
) {
    val openedAt = remember { Clock.System.now().toEpochMilliseconds() }
    val locationService = remember { LocationService() }
    val scope = rememberCoroutineScope()
    var occurredAt by remember { mutableLongStateOf(openedAt) }
    var placeQuery by remember { mutableStateOf("") }
    var placeLat by remember { mutableStateOf<Double?>(null) }
    var placeLon by remember { mutableStateOf<Double?>(null) }
    var results by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }

    LaunchedEffect(placeQuery) {
        val q = placeQuery.trim()
        if (q.length < 2 || placeLat != null) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = runCatching { GeocodingService.searchAddresses(q, limit = 5) }.getOrDefault(emptyList())
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = "Log a hangout",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            Text(
                "$peerFirstName will be asked to confirm before it's added to your story.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("When", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatRelativeDay(occurredAt, Clock.System.now().toEpochMilliseconds())} · ${formatClockTime(occurredAt)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = { picking = true }) { Text("Change") }
            }
            OutlinedTextField(
                value = placeQuery,
                onValueChange = {
                    placeQuery = it
                    placeLat = null
                    placeLon = null
                },
                placeholder = { Text("Where? (optional)") },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            results.forEach { result ->
                Text(
                    result.shortLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                placeQuery = result.shortLabel
                                placeLat = result.latitude
                                placeLon = result.longitude
                                results = emptyList()
                            }.padding(vertical = 10.dp),
                )
            }
            if (canUseCurrentLocationFor(occurredAt, Clock.System.now().toEpochMilliseconds())) {
                TextButton(
                    enabled = !locating,
                    onClick = {
                        locating = true
                        scope.launch {
                            val fix = runCatching { locationService.getCurrentLocation() }.getOrNull()
                            if (fix != null) {
                                placeLat = fix.latitude
                                placeLon = fix.longitude
                                val label =
                                    runCatching { GeocodingService.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
                                placeQuery = label?.shortLabel?.takeIf { it.isNotBlank() } ?: "Current location"
                                results = emptyList()
                            }
                            locating = false
                        }
                    },
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(if (locating) "  Locating…" else "  Use my current location")
                }
            }
            Spacer(Modifier.height(16.dp))
            ClickSheetPrimaryButton(
                text = if (busy) "Logging…" else "Log hangout",
                enabled = !busy,
                onClick = { onLog(occurredAt, placeQuery.trim().takeIf { it.isNotEmpty() }, placeLat, placeLon) },
            )
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }

    if (picking) {
        val now = Clock.System.now().toEpochMilliseconds()
        ClickDateTimePickerDialog(
            initialEpochMs = occurredAt,
            minEpochMs = now - LOG_HANGOUT_MAX_BACKDATE_MS + 60_000L,
            maxEpochMs = now,
            onPicked = {
                occurredAt = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
