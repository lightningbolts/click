package compose.project.click.click.util

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.DayOfWeek as ProductDayOfWeek
import compose.project.click.click.data.models.MemoryCapsule
import kotlinx.datetime.DayOfWeek as KtxDayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Collects human-readable connection / encounter / capsule strings for substring search.
 * Keeps branching shallow (NASA Power of 10).
 */
fun connectionContextHaystack(connection: Connection): String {
    val parts = ArrayList<String>(24)
    fun addPiece(s: String?) {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return
        parts.add(t)
    }
    addPiece(connection.semanticLocation)
    addPiece(connection.displayLocationLabel)
    addPiece(connection.context_tag)
    addPiece(connection.timeOfDayUtc)
    addPiece(connection.createdUtc)
    addPiece(connection.weatherCondition)
    addPiece(connection.resolvedWeatherCondition)
    capsulePieces(connection.memoryCapsule, parts)
    connection.originMemoryCapsule()?.let { if (it !== connection.memoryCapsule) capsulePieces(it, parts) }
    connection.latestMemoryCapsule()?.let {
        if (it !== connection.memoryCapsule && it !== connection.originMemoryCapsule()) {
            capsulePieces(it, parts)
        }
    }
    for (enc in connection.connectionEncounters) {
        addPiece(enc.locationName)
        addPiece(enc.displayLocation)
        addPiece(enc.semanticLocation)
        for (tag in enc.contextTags) addPiece(tag)
    }
    return parts.joinToString(" ").lowercase()
}

private fun capsulePieces(c: MemoryCapsule?, out: MutableList<String>) {
    if (c == null) return
    c.locationName?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    c.contextTag?.label?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    c.weatherSnapshot?.condition?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
}

fun connectionMatchesMemoryOrTimeQuery(connection: Connection, queryLower: String): Boolean {
    if (queryLower.isBlank()) return false
    if (connectionContextHaystack(connection).contains(queryLower)) return true
    return connectionMatchesWeekdayEncounter(connection, queryLower)
}

private fun connectionMatchesWeekdayEncounter(connection: Connection, queryLower: String): Boolean {
    val target = dayOfWeekFromQuery(queryLower) ?: return false
    val tz = TimeZone.currentSystemDefault()
    for (enc in connection.connectionEncounters) {
        val ins = enc.encounteredAtInstant() ?: continue
        if (instantMatchesDayOfWeek(ins, target, tz)) return true
    }
    return false
}

private fun instantMatchesDayOfWeek(ins: Instant, target: KtxDayOfWeek, tz: TimeZone): Boolean {
    return ins.toLocalDateTime(tz).dayOfWeek == target
}

private fun dayOfWeekFromQuery(queryLower: String): KtxDayOfWeek? {
    for (d in ProductDayOfWeek.entries) {
        val hit = queryLower.contains(d.displayName, ignoreCase = true) ||
            queryLower.contains(d.shortName, ignoreCase = true)
        if (!hit) continue
        val k = runCatching { KtxDayOfWeek.valueOf(d.name) }.getOrNull() ?: continue
        return k
    }
    return null
}
