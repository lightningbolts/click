package compose.project.click.click.ui.components

import compose.project.click.click.data.models.Connection
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
    val db = resolvedExactNoiseLevelDb
    if (db != null && db.isFinite()) parts.add("${db.roundToInt()} dB")
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ")
}
