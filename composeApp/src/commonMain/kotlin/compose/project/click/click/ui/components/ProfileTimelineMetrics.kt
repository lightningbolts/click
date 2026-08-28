@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.ContextTagTaxonomy // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.ProvideSheetSwipeDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberSheetScrollAtTop // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

internal fun sharedInterestTags(
    viewer: List<String>,
    other: List<String>,
): List<String> {
    if (viewer.isEmpty() || other.isEmpty()) return emptyList()
    val viewerNorm = viewer.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    return other.map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() in viewerNorm }.distinct()
}

internal fun ProfileAvailabilityIntentBubble.displayLabel(): String {
    val tag = intentTag?.trim().orEmpty()
    val tf = timeframe?.trim().orEmpty()
    return when {
        tag.isNotEmpty() && tf.isNotEmpty() -> "$tag · $tf"
        tag.isNotEmpty() -> tag
        tf.isNotEmpty() -> tf
        else -> ""
    }.trim()
}

internal fun ProfileAvailabilityIntentBubble.activeUntilShort(): String {
    val iso = expiresAt ?: return ""
    val instant = runCatching { kotlinx.datetime.Instant.parse(iso) }.getOrNull() ?: return ""
    val tz = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(tz)
    val today = Clock.System.todayIn(tz)
    val d = local.date

    fun pad(n: Int) = n.toString().padStart(2, '0')
    val timePart = "${pad(local.hour)}:${pad(local.minute)}"
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    return when {
        d == today -> "Today · $timePart"
        d == tomorrow -> "Tomorrow · $timePart"
        else -> "${d.monthNumber}/${d.dayOfMonth} · $timePart"
    }
}

internal fun formatEncounterEventSchedule(
    startIso: String?,
    endIso: String?,
): String? {
    val startMs =
        startIso
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
            ?: return null
    val endMs =
        endIso
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
            ?: return null
    if (endMs <= startMs) return null
    return formatEventScheduleRange(EventSchedule(startEpochMs = startMs, endEpochMs = endMs))
}

internal fun ageFromBirthdayIso(birthday: String?): Int? {
    if (birthday.isNullOrBlank()) return null
    return try {
        val d = LocalDate.parse(birthday.take(10))
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var age = today.year - d.year
        if (today.monthNumber < d.monthNumber ||
            (today.monthNumber == d.monthNumber && today.dayOfMonth < d.dayOfMonth)
        ) {
            age--
        }
        age.takeIf { it in 0..120 }
    } catch (_: Exception) {
        null
    }
}

internal fun ConnectionEncounter.metricNoiseLabel(): String? {
    val raw = noiseLevel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val friendly =
        runCatching { NoiseLevelCategory.valueOf(raw.uppercase().replace(' ', '_')) }
            .getOrNull()
            ?.let { formatNoiseCategoryForTimeline(it) }
            ?: raw.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    return friendly
}

internal fun formatNoiseCategoryForTimeline(cat: NoiseLevelCategory): String =
    when (cat) {
        NoiseLevelCategory.VERY_QUIET -> "Very quiet"
        NoiseLevelCategory.QUIET -> "Quiet"
        NoiseLevelCategory.MODERATE -> "Moderate"
        NoiseLevelCategory.LOUD -> "Loud"
        NoiseLevelCategory.VERY_LOUD -> "Very loud"
    }

internal fun ConnectionEncounter.metricElevationLabel(): String? {
    val parts = mutableListOf<String>()
    elevationCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val friendly =
            runCatching { HeightCategory.valueOf(raw.uppercase().replace(' ', '_')) }
                .getOrNull()
                ?.let { hc ->
                    when (hc) {
                        HeightCategory.BELOW_GROUND -> "Below ground"
                        HeightCategory.GROUND_LEVEL -> "Ground level"
                        HeightCategory.ELEVATED -> "Elevated"
                        HeightCategory.HIGH_RISE -> "High rise"
                    }
                } ?: raw.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        parts.add(friendly)
    }
    relativeAltitudeM?.takeIf { it.isFinite() }?.let { parts.add("${it.roundToInt()} m") }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

internal fun ConnectionEncounter.metricWindLabel(): String? {
    val ws = weatherSnapshot ?: return null
    val kph = ws.windSpeedKph ?: return null
    if (!kph.isFinite()) return null
    val deg = ws.windDirectionDegrees
    val suffix =
        deg?.takeIf { it in 0..359 }?.let { d ->
            val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val x = ((d % 360) + 360) % 360
            val idx = (kotlin.math.floor((x + 22.5) / 45.0).toInt() % 8 + 8) % 8
            " ${dirs[idx]}"
        } ?: ""
    return "${kph.roundToInt()} km/h$suffix"
}

internal fun ConnectionEncounter.metricPressureLabel(): String? =
    weatherSnapshot?.pressureMslHpa?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} hPa" }

internal fun ConnectionEncounter.metricConditionLabel(): String? =
    weatherSnapshot?.condition?.trim()?.takeIf { it.isNotEmpty() }
        ?: weatherSnapshot
            ?.iconCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.titlecase() }

internal fun ConnectionEncounter.metricTemperatureLabel(): String? {
    val c = weatherSnapshot?.temperatureCelsius ?: return null
    if (!c.isFinite()) return null
    val f = (c * 9.0 / 5.0) + 32.0
    if (!f.isFinite()) return null
    return "${f.roundToInt()}°F (${c.roundToInt()}°C)"
}

@Composable
internal fun TimelineMetricPill(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    cardBorder: Color,
    cardBg: Color,
    body: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, cardBorder, RoundedCornerShape(50))
                .background(cardBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun OurTimelineSection(
    encounters: List<ConnectionEncounter>,
    emptyCopy: String = "No crossing history on file yet.",
) {
    if (encounters.isEmpty()) {
        Text(
            text = emptyCopy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val sorted =
        encounters.sortedWith(
            compareByDescending<ConnectionEncounter> { it.encounteredAt }
                .thenByDescending { it.id },
        )
    val oldestId = sorted.lastOrNull()?.id
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val dotBorder = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.colorScheme.onSurface

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val x = 16.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = stroke,
                    )
                },
    ) {
        sorted.forEach { enc ->
            val isOldest = enc.id == oldestId
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(32.dp)
                            .padding(top = 2.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .border(2.dp, dotBorder, CircleShape)
                                .background(PrimaryBlue),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (isOldest) {
                        Text(
                            text = "Where it started",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LightBlue.copy(alpha = 0.95f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text =
                            formatEncounterTimelineWhenLine(enc.encounteredAt)
                                ?: enc.encounteredAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(
                        text =
                            formatEncounterPlaceLine(
                                locationName = enc.locationName,
                                displayLocation = enc.displayLocation,
                                semanticLocationJson = enc.semanticLocation,
                            ) ?: "Unknown place",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = body,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    val eventTitle = enc.eventBeaconTitle?.trim()?.takeIf { it.isNotEmpty() }
                    val eventBeaconId = enc.eventBeaconId?.trim()?.takeIf { it.isNotEmpty() }
                    if (eventTitle != null || eventBeaconId != null) {
                        val scheduleLabel =
                            formatEncounterEventSchedule(
                                enc.eventBeaconStartAt,
                                enc.eventBeaconEndAt,
                            )
                        Text(
                            text = eventTitle ?: "Event",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (scheduleLabel != null) {
                            Text(
                                text = scheduleLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (eventBeaconId != null) {
                            TextButton(
                                onClick = { EventDeepLinkRouter.setPendingBeaconId(eventBeaconId) },
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text("View on map", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    val momentTags =
                        enc.contextTags
                            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                            .distinct()
                    if (momentTags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            momentTags.forEach { tag ->
                                TimelineMetricPill(
                                    icon = Icons.Filled.AutoAwesome,
                                    iconTint = PrimaryBlue.copy(alpha = 0.9f),
                                    text = ContextTagTaxonomy.displayLabel(tag),
                                    cardBorder = cardBorder,
                                    cardBg = cardBg,
                                    body = body,
                                )
                            }
                        }
                    }
                    val pills =
                        buildList {
                            enc.metricConditionLabel()?.let { add(Triple(Icons.Outlined.Cloud, Color(0xFFB0BEC5), it)) }
                            enc.metricTemperatureLabel()?.let { add(Triple(Icons.Outlined.Thermostat, Color(0xFFFFCC80), it)) }
                            enc.metricWindLabel()?.let { add(Triple(Icons.Outlined.Air, Color(0xFF81D4FA), it)) }
                            enc.metricNoiseLabel()?.let { add(Triple(Icons.Outlined.GraphicEq, Color(0xFF69F0AE), it)) }
                            enc.metricElevationLabel()?.let { add(Triple(Icons.Outlined.Terrain, LightBlue.copy(alpha = 0.95f), it)) }
                            enc.metricCompassAzimuthLabel()?.let { add(Triple(Icons.Outlined.Explore, Color(0xFFB39DDB), it)) }
                        }
                    if (pills.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pills.forEach { (ic, tint, lbl) ->
                                TimelineMetricPill(ic, tint, lbl, cardBorder, cardBg, body)
                            }
                        }
                    }
                }
            }
        }
    }
}
