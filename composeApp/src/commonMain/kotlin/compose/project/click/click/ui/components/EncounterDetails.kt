@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.connect.ConnectionVerification // pragma: allowlist secret
import compose.project.click.click.connect.verificationLine // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.mergeRichestEncounterEvents // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/*
 * Encounter details on the connect result (07 §6 G4-6): When / Where / With / Event / Weather /
 * Sound / Height / Pressure / Verified by.
 * iOS: `Click/Features/Connect/PostConnectView.swift` (`encounterDetails`) and
 * `PostConnectModel.swift` (`detailsPlace`, `detailsWeather`, `verificationLine`, `load`).
 */

/** Which glyph a details row shows. */
enum class EncounterDetailKind { When, Where, With, Event, Weather, Sound, Height, Pressure, VerifiedBy }

/** One details row; [pending] rows are placeholders while the place name loads. */
data class EncounterDetailRow(
    val kind: EncounterDetailKind,
    val label: String,
    val value: String,
    val pending: Boolean = false,
)

/** The server adds the place name a moment after saving; wait this long before fetching once more. */
const val ENCOUNTER_PLACE_RETRY_DELAY_MS: Long = 2_500L

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Nov 2, 2026 at 3:04 PM" (iOS `.formatted(date: .abbreviated, time: .shortened)`, en-US). */
fun encounterWhenLine(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    val hour12 = (t.hour % 12).let { if (it == 0) 12 else it }
    val minute = t.minute.toString().padStart(2, '0')
    val amPm = if (t.hour < 12) "AM" else "PM"
    return "${MONTHS[t.monthNumber - 1]} ${t.dayOfMonth}, ${t.year} at $hour12:$minute $amPm"
}

private fun String?.firstComponent(): String? =
    this
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Venue (or the first part of the stored label), else the neighbourhood (iOS `detailsPlace`). Never a full address. */
fun ConnectionEncounter.detailsPlace(): String? =
    locationName.firstComponent()
        ?: neighbourhoodFromEncounterSemanticLocation(semanticLocation)
        ?: displayLocation.firstComponent()

/** "12°C · Clear" when the server attached weather (iOS `detailsWeather`). */
fun ConnectionEncounter.detailsWeather(): String? {
    val temperature =
        weatherSnapshot
            ?.temperatureCelsius
            ?.takeIf { it.isFinite() }
            ?.let { "${it.roundToInt()}°C" }
    val condition = weatherSnapshot?.condition?.trim()?.takeIf { it.isNotEmpty() }
    return listOfNotNull(temperature, condition).joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** This tap's encounter: the newest one, with every reporter's row merged (iOS `latestEncounter`). */
fun latestTapEncounter(rows: List<ConnectionEncounter>): ConnectionEncounter? =
    rows
        .mergeRichestEncounterEvents()
        .maxByOrNull { it.encounteredAtInstant()?.toEpochMilliseconds() ?: Long.MIN_VALUE }

/** Fetch once more for the place name: none yet, and either nothing loaded or a coordinate was saved. */
fun shouldRetryForPlace(encounter: ConnectionEncounter?): Boolean =
    encounter == null || (encounter.detailsPlace() == null && encounter.gpsLat != null && encounter.gpsLon != null)

/** The details rows in iOS order; empty optional rows are left out. */
fun encounterDetailRows(
    connectedAtMs: Long,
    encounter: ConnectionEncounter?,
    isLoading: Boolean,
    groupPeerNames: List<String>,
    verification: ConnectionVerification?,
    isQr: Boolean,
    refinedAccuracyMeters: Double? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<EncounterDetailRow> =
    buildList {
        add(EncounterDetailRow(EncounterDetailKind.When, "When", encounterWhenLine(connectedAtMs, timeZone)))
        val place = encounter?.detailsPlace()
        val whereText =
            when {
                place != null -> place
                isLoading -> "Finding the place…"
                encounter?.gpsLat != null || verification?.locationAccuracyMeters != null -> "Place name unavailable"
                else -> "Not recorded"
            }
        add(EncounterDetailRow(EncounterDetailKind.Where, "Where", whereText, pending = place == null && isLoading))
        if (groupPeerNames.size > 1) {
            val names =
                groupPeerNames.map { name ->
                    name
                        .trim()
                        .substringBefore(' ')
                        .ifEmpty { name.trim() }
                }
            val joined =
                when (names.size) {
                    2 -> "${names[0]} and ${names[1]}"
                    else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
                }
            add(EncounterDetailRow(EncounterDetailKind.With, "With", joined))
        }
        encounter?.eventBeaconTitle?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(EncounterDetailRow(EncounterDetailKind.Event, "Event", it))
        }
        encounter?.detailsWeather()?.let { add(EncounterDetailRow(EncounterDetailKind.Weather, "Weather", it)) }
        encounter?.metricNoiseLabel()?.let { add(EncounterDetailRow(EncounterDetailKind.Sound, "Sound", it)) }
        encounter?.metricHeightLabel()?.let { add(EncounterDetailRow(EncounterDetailKind.Height, "Height", it)) }
        encounter?.metricPressureLabel()?.let { add(EncounterDetailRow(EncounterDetailKind.Pressure, "Pressure", it)) }
        add(
            EncounterDetailRow(
                EncounterDetailKind.VerifiedBy,
                "Verified by",
                verificationLine(verification, isQr, refinedAccuracyMeters),
            ),
        )
    }

/**
 * Loads [connectionId]'s encounters and fetches once more after [retryDelayMs] when the place
 * name hasn't arrived yet (iOS `PostConnectModel.load`). Failures leave the rows as they are.
 */
suspend fun loadTapEncounter(
    connectionId: String,
    fetch: suspend (String) -> List<ConnectionEncounter>?,
    onLoaded: (ConnectionEncounter?) -> Unit,
    sleep: suspend (Long) -> Unit = { delay(it) },
    retryDelayMs: Long = ENCOUNTER_PLACE_RETRY_DELAY_MS,
) {
    val first = fetch(connectionId)?.let(::latestTapEncounter)
    if (first != null) onLoaded(first)
    if (!shouldRetryForPlace(first)) return
    sleep(retryDelayMs)
    fetch(connectionId)?.let(::latestTapEncounter)?.let(onLoaded)
}

private fun EncounterDetailKind.icon(): ImageVector =
    when (this) {
        EncounterDetailKind.When -> Icons.Outlined.Schedule
        EncounterDetailKind.Where -> Icons.Outlined.Place
        EncounterDetailKind.With -> Icons.Outlined.Group
        EncounterDetailKind.Event -> Icons.Outlined.CalendarMonth
        EncounterDetailKind.Weather -> Icons.Outlined.Cloud
        EncounterDetailKind.Sound -> Icons.Outlined.GraphicEq
        EncounterDetailKind.Height -> Icons.Outlined.Terrain
        EncounterDetailKind.Pressure -> Icons.Outlined.Speed
        EncounterDetailKind.VerifiedBy -> Icons.Outlined.VerifiedUser
    }

/** The grouped details card for one connect result. */
@Composable
fun EncounterDetailsCard(
    connectionId: String,
    groupPeerNames: List<String>,
    verification: ConnectionVerification?,
    isQr: Boolean,
    refinedAccuracyMeters: Double?,
    modifier: Modifier = Modifier,
) {
    val connectedAtMs = remember(connectionId) { Clock.System.now().toEpochMilliseconds() }
    var encounter by remember(connectionId) { mutableStateOf<ConnectionEncounter?>(null) }
    var loading by remember(connectionId) { mutableStateOf(true) }
    LaunchedEffect(connectionId) {
        try {
            loadTapEncounter(
                connectionId = connectionId,
                fetch = { id ->
                    runCatching { SupabaseRepository().fetchConnectionById(id)?.connectionEncounters }.getOrNull()
                },
                onLoaded = { encounter = it },
            )
        } finally {
            loading = false
        }
    }
    EncounterDetailsRows(
        rows =
            encounterDetailRows(
                connectedAtMs = connectedAtMs,
                encounter = encounter,
                isLoading = loading,
                groupPeerNames = groupPeerNames,
                verification = verification,
                isQr = isQr,
                refinedAccuracyMeters = refinedAccuracyMeters,
            ),
        modifier = modifier,
    )
}

@Composable
internal fun EncounterDetailsRows(
    rows: List<EncounterDetailRow>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        imageVector = row.kind.icon(),
                        contentDescription = null,
                        tint = GlassSheetTokens.OnOledMuted(),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted(),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.pending) GlassSheetTokens.OnOledMuted() else GlassSheetTokens.OnOled(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
