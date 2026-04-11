package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.DirectionsRun
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.NoiseLevelCategory
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun shortDay(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun shortMonth(m: Month): String = when (m) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
}

private fun formatNoiseCategory(cat: NoiseLevelCategory): String = when (cat) {
    NoiseLevelCategory.VERY_QUIET -> "Very quiet"
    NoiseLevelCategory.QUIET -> "Quiet"
    NoiseLevelCategory.MODERATE -> "Moderate"
    NoiseLevelCategory.LOUD -> "Loud"
    NoiseLevelCategory.VERY_LOUD -> "Very loud"
}

/** Event / context line (emoji + label or legacy id). */
fun Connection.profileContextLine(): String? {
    latestMemoryCapsule()?.contextTag?.let { tag ->
        val e = tag.emoji.trim()
        val l = tag.label.trim()
        if (l.isEmpty()) return@let null
        return if (e.isNotEmpty()) "$e $l" else l
    }
    memoryCapsule?.contextTag?.let { tag ->
        val e = tag.emoji.trim()
        val l = tag.label.trim()
        if (l.isEmpty()) return@let null
        return if (e.isNotEmpty()) "$e $l" else l
    }
    val id = contextTagId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return id
}

private fun structuredAddressFromFull(m: Map<String, String>?): String? {
    if (m.isNullOrEmpty()) return null
    val dn = m["display_name"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: m["displayName"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: m["formatted"]?.trim()?.takeIf { it.isNotEmpty() }
    if (dn != null) return dn
    val parts = listOf("road", "neighbourhood", "city", "town", "state", "country")
        .mapNotNull { key -> m[key]?.trim()?.takeIf { it.isNotEmpty() } }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

fun Connection.profilePlaceLine(): String? {
    val sem = originEncounter()?.locationName?.trim()?.takeIf { it.isNotEmpty() }
        ?: latestEncounter()?.locationName?.trim()?.takeIf { it.isNotEmpty() }
        ?: semantic_location?.trim()?.takeIf { it.isNotEmpty() }
    val fromFull = structuredAddressFromFull(full_location)
    return when {
        sem != null && fromFull != null && sem != fromFull -> sem
        sem != null -> sem
        fromFull != null -> fromFull
        else -> null
    }
}

fun Connection.profileAddressDetailLine(): String? {
    val sem = semanticLocation?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val fromFull = structuredAddressFromFull(full_location) ?: return null
    return fromFull.takeIf { it != sem }
}

/** Local calendar + clock for one crossing (`connection_encounters.encountered_at` ISO). */
fun formatEncounterTimelineWhenLine(encounteredAtIso: String): String? {
    val instant = encounteredAtIso.trim().takeIf { it.isNotEmpty() }?.let { iso ->
        runCatching { Instant.parse(iso) }.getOrNull()
    } ?: return null
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val datePart = "${shortDay(ldt.dayOfWeek)}, ${shortMonth(ldt.month)} ${ldt.dayOfMonth}, ${ldt.year}"
    val hour24 = ldt.hour
    val minute = ldt.minute
    val ampm = if (hour24 < 12) "AM" else "PM"
    val h12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val timePart = "$h12:${minute.toString().padStart(2, '0')} $ampm"
    return "$datePart · $timePart"
}

fun Connection.profileWhenLine(): String? {
    val instant: Instant? = originEncounter()?.encounteredAt?.trim()?.takeIf { it.isNotEmpty() }?.let { iso ->
        runCatching { Instant.parse(iso) }.getOrNull()
    } ?: createdUtc?.trim()?.takeIf { it.isNotEmpty() }?.let { iso ->
        runCatching { Instant.parse(iso) }.getOrNull()
    } ?: if (created > 0L) Instant.fromEpochMilliseconds(created) else null
    instant ?: return null
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val datePart = "${shortDay(ldt.dayOfWeek)}, ${shortMonth(ldt.month)} ${ldt.dayOfMonth}, ${ldt.year}"
    val hour24 = ldt.hour
    val minute = ldt.minute
    val ampm = if (hour24 < 12) "AM" else "PM"
    val h12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val timePart = "$h12:${minute.toString().padStart(2, '0')} $ampm"
    return "$datePart · $timePart"
}

fun Connection.profileWeatherLine(): String? {
    latestMemoryCapsule()?.weatherSnapshot?.let { ws ->
        val parts = mutableListOf<String>()
        ws.condition?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        ws.temperatureCelsius?.let { c ->
            val f = (c * 9f / 5f) + 32f
            if (f.isFinite()) parts.add("${f.roundToInt()}°F")
        }
        if (parts.isNotEmpty()) return parts.joinToString(" · ")
    }
    memoryCapsule?.weatherSnapshot?.let { ws ->
        val parts = mutableListOf<String>()
        ws.condition?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        ws.temperatureCelsius?.let { c ->
            val f = (c * 9f / 5f) + 32f
            if (f.isFinite()) parts.add("${f.roundToInt()}°F")
        }
        if (parts.isNotEmpty()) return parts.joinToString(" · ")
    }
    val col = resolvedWeatherCondition?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return col
}

fun Connection.profileNoiseLine(): String? {
    val parts = mutableListOf<String>()
    val rawCat = resolvedNoiseLevel?.trim()?.takeIf { it.isNotEmpty() }
    if (rawCat != null) {
        val enumCat = runCatching {
            NoiseLevelCategory.valueOf(rawCat.uppercase().replace(' ', '_'))
        }.getOrNull()
        parts.add(
            if (enumCat != null) formatNoiseCategory(enumCat)
            else rawCat.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        )
    }
    resolvedExactNoiseLevelDb?.takeIf { it.isFinite() }?.let { parts.add("${it.roundToInt()} dB") }
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ")
}

/** Barometric elevation snapshot when a precise meter value exists (legacy rows omit this). */
fun Connection.profileBarometricLine(): String? =
    resolvedExactBarometricElevationM?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} m" }

fun ConnectionEncounter.metricLuxLabel(): String? =
    luxLevel?.takeIf { it.isFinite() && it >= 0 }?.let { "${it.roundToInt()} lx" }

fun ConnectionEncounter.metricMotionVarianceLabel(): String? =
    motionVariance?.takeIf { it.isFinite() && it >= 0 }?.let { v ->
        val rounded = (v * 100.0).roundToInt() / 100.0
        "$rounded"
    }

fun ConnectionEncounter.metricCompassAzimuthLabel(): String? =
    compassAzimuth?.takeIf { it.isFinite() }?.let {
        var deg = it % 360.0
        if (deg < 0) deg += 360.0
        "${deg.roundToInt()}°"
    }

fun ConnectionEncounter.metricBatteryLabel(): String? =
    batteryLevel?.takeIf { it in 0..100 }?.let { "$it%" }

/**
 * Compact pill used for environmental / hardware metrics (connections list + profile).
 */
@Composable
fun SmallBadge(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val body = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, border, RoundedCornerShape(50))
            .background(bg)
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionHardwareVibeBadgesRow(
    encounter: ConnectionEncounter?,
    modifier: Modifier = Modifier,
) {
    if (encounter == null) return
    val luxVal = encounter.luxLevel?.takeIf { it.isFinite() && it >= 0 }
    val isDim = luxVal != null && luxVal < 15.0
    val luxIcon = if (isDim) Icons.Outlined.NightsStay else Icons.Outlined.WbSunny
    val luxTint = if (isDim) Color(0xFF90CAF9) else Color(0xFFFFE082)
    val pills = buildList<Triple<ImageVector, Color, String>> {
        encounter.metricLuxLabel()?.let { lbl ->
            add(Triple(luxIcon, luxTint, lbl))
        }
        encounter.metricBatteryLabel()?.let { lbl ->
            add(Triple(Icons.Outlined.BatteryStd, Color(0xFFA5D6A7), lbl))
        }
        encounter.metricCompassAzimuthLabel()?.let { lbl ->
            add(Triple(Icons.Outlined.Explore, Color(0xFFB39DDB), lbl))
        }
        encounter.metricMotionVarianceLabel()?.let { lbl ->
            add(Triple(Icons.Outlined.DirectionsRun, Color(0xFFFFAB91), lbl))
        }
    }
    if (pills.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pills.forEach { (ic, tint, lbl) ->
            SmallBadge(icon = ic, iconTint = tint, label = lbl)
        }
    }
}
