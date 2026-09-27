package compose.project.click.click.events

/** Fixed taxonomy for event beacon category chips (drop sheet + detail). */
val EVENT_CATEGORY_OPTIONS: List<String> =
    listOf(
        "Promotional",
        "Social",
        "School Event",
    )

const val EVENT_CATEGORIES_METADATA_KEY = "event_categories"

/** Presets plus custom categories, at most this many in total (iOS `BeaconFormRules.maxCategories`). */
const val EVENT_CATEGORIES_MAX = 3

const val EVENT_CUSTOM_CATEGORY_MAX_LENGTH = 24

/** A cleaned custom category, or null when empty, too long, or already chosen (case-insensitive). */
fun customEventCategory(
    raw: String,
    existing: Collection<String>,
): String? {
    val clean = raw.trim().replace(Regex("""\s+"""), " ")
    if (clean.isEmpty() || clean.length > EVENT_CUSTOM_CATEGORY_MAX_LENGTH) return null
    if (existing.any { it.equals(clean, ignoreCase = true) }) return null
    return clean
}

/** What goes on the wire: trimmed, de-duplicated, length-checked, capped at [EVENT_CATEGORIES_MAX]. */
fun sanitizeEventCategories(categories: Collection<String>): List<String> =
    categories
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= EVENT_CUSTOM_CATEGORY_MAX_LENGTH }
        .distinctBy { it.lowercase() }
        .take(EVENT_CATEGORIES_MAX)
