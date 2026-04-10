package compose.project.click.click.util

import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * True when both users have at least one non-expired intent and either the tag or timeframe label matches.
 */
fun hasActiveAvailabilityIntentOverlap(
    a: List<ProfileAvailabilityIntentBubble>,
    b: List<ProfileAvailabilityIntentBubble>,
): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    val now = Clock.System.now()
    fun active(list: List<ProfileAvailabilityIntentBubble>) =
        list.filter { bubble ->
            val exp = bubble.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@filter true
            exp > now
        }
    val aa = active(a)
    val bb = active(b)
    if (aa.isEmpty() || bb.isEmpty()) return false
    val tagsA = aa.mapNotNull { it.intentTag?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    val tagsB = bb.mapNotNull { it.intentTag?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    if (tagsA.intersect(tagsB).isNotEmpty()) return true
    val tfA = aa.mapNotNull { it.timeframe?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    val tfB = bb.mapNotNull { it.timeframe?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    return tfA.intersect(tfB).isNotEmpty()
}

fun firstIntentOverlapLabel(
    a: List<ProfileAvailabilityIntentBubble>,
    b: List<ProfileAvailabilityIntentBubble>,
): String? {
    if (!hasActiveAvailabilityIntentOverlap(a, b)) return null
    val now = Clock.System.now()
    fun active(list: List<ProfileAvailabilityIntentBubble>) =
        list.filter { bubble ->
            val exp = bubble.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@filter true
            exp > now
        }
    val aa = active(a)
    val bb = active(b)
    val tagsA = aa.mapNotNull { it.intentTag?.trim()?.takeIf { t -> t.isNotEmpty() } }
    val tagsBSet = bb.mapNotNull { it.intentTag?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    val shared = tagsA.firstOrNull { it.lowercase() in tagsBSet } ?: return aa.firstOrNull()?.intentTag
    return shared
}
