package compose.project.click.click.encounter

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionEncounter
import kotlinx.datetime.Clock

/** Returns the latest encounter id when it is younger than [maxAgeHours] (default 12h). */
fun Connection.recentEncounterId(maxAgeHours: Long = 12L): String? {
    val encounter = latestEncounter() ?: return null
    return encounter.takeIfRecent(maxAgeHours)?.id
}

fun ConnectionEncounter.takeIfRecent(maxAgeHours: Long = 12L): ConnectionEncounter? {
    val instant = encounteredAtInstant() ?: return null
    val ageMs = Clock.System.now().toEpochMilliseconds() - instant.toEpochMilliseconds()
    val maxMs = maxAgeHours.coerceAtLeast(1) * 60L * 60L * 1000L
    return if (ageMs in 0 until maxMs) this else null
}
