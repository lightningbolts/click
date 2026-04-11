package compose.project.click.click.data

import compose.project.click.click.data.models.ContextTag

object ContextTagTaxonomy {
    val all: List<ContextTag> = listOf(
        ContextTag("lecture", "Lecture / Class", "🎓"),
        ContextTag("study", "Study Session", "📚"),
        ContextTag("dorm", "Dorms / Residence Hall", "🛏️"),
        ContextTag("party", "Party", "🎉"),
        ContextTag("cafe", "Cafe / Coffee", "☕"),
        ContextTag("bar", "Bar / Nightlife", "🍻"),
        ContextTag("event", "Campus Event", "🏟️"),
        ContextTag("sports", "Sports / Rec", "⚽"),
        ContextTag("club", "Club / Org Meeting", "🤝"),
        ContextTag("transit", "Transit / Commute", "🚌"),
        ContextTag("gym", "Gym / Workout", "💪"),
        ContextTag("conference", "Conference", "🎤"),
        ContextTag("outdoor", "Outdoors / Nature", "🌲"),
        ContextTag("dining", "Dining / Food", "🍽️"),
        ContextTag("custom", "Other...", "✏️")
    )

    fun suggest(locationName: String?, hourOfDay: Int): List<ContextTag> {
        val suggestions = linkedSetOf<ContextTag>()
        val normalizedLocation = locationName.orEmpty().lowercase()

        fun add(id: String) {
            all.firstOrNull { it.id == id }?.let(suggestions::add)
        }

        when {
            normalizedLocation.containsAny("dorm", "residence", "residential", "housing", "apartment", "suite") -> {
                add("dorm")
                add("study")
            }
            normalizedLocation.containsAny("hall", "building", "classroom", "lecture", "school", "campus") -> {
                add("lecture")
                add("study")
            }
            normalizedLocation.containsAny("cafe", "café", "coffee", "espresso", "starbucks") -> {
                add("cafe")
                add("study")
            }
            normalizedLocation.containsAny("gym", "rec", "fitness", "arena") -> {
                add("gym")
                add("sports")
            }
            normalizedLocation.containsAny("bar", "pub", "club", "lounge") -> {
                add("bar")
                add("party")
            }
            normalizedLocation.containsAny("bus", "train", "station", "stop", "transit") -> {
                add("transit")
            }
            normalizedLocation.containsAny("park", "trail", "beach", "garden") -> {
                add("outdoor")
            }
            normalizedLocation.containsAny("stadium", "event", "center", "theater") -> {
                add("event")
                add("conference")
            }
            normalizedLocation.containsAny("food", "dining", "restaurant", "kitchen", "bistro") -> {
                add("dining")
                add("cafe")
            }
        }

        if (hourOfDay in 22..23 || hourOfDay in 0..2) {
            add("party")
            add("bar")
        }

        if (hourOfDay in 8..17 && normalizedLocation.isNotBlank()) {
            add("lecture")
            add("study")
        }

        if (hourOfDay in 11..14) {
            add("dining")
            add("cafe")
        }

        if (hourOfDay in 17..21) {
            add("event")
            add("club")
            add("dorm")
        }

        if (suggestions.isEmpty()) {
            all.take(4).forEach(suggestions::add)
        }

        return suggestions.take(4)
    }

    /**
     * Normalizes free-text custom context to match preset chips: title-case label plus an emoji
     * inferred from keywords / taxonomy (e.g. "study session" → 📚, "Study Session").
     */
    fun formatCustomUserContextTag(raw: String): ContextTag {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) {
            return ContextTag(id = "custom", label = "", emoji = "🏷️")
        }
        val lower = collapsed.lowercase()
        val label = titleCaseWords(collapsed)
        val emoji = inferEmojiForCustom(lower)
        return ContextTag(id = "custom", label = label, emoji = emoji)
    }
}

private fun titleCaseWords(s: String): String =
    s.split(' ').joinToString(" ") { w ->
        if (w.isEmpty()) {
            w
        } else {
            w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
    }

private fun inferEmojiForCustom(lower: String): String {
    val rules: List<Pair<Regex, String>> = listOf(
        Regex("(study|studying|homework|library|exam|midterm|read(ing)?\\s+group)") to "📚",
        Regex("(lecture|class(room)?|seminar|lab\\s*section)") to "🎓",
        Regex("(party|celebrat|birthday\\s*party)") to "🎉",
        Regex("(coffee|cafe|café|espresso|latte|starbucks)") to "☕",
        Regex("(bar|pub|nightclub|night\\s*life)") to "🍻",
        Regex("(dorm|residence|suite|roommate)") to "🛏️",
        Regex("(gym|workout|lift|run(ning)? club)") to "💪",
        Regex("(bus|train|transit|commute|subway)") to "🚌",
        Regex("(park|trail|hike|outdoor|beach)") to "🌲",
        Regex("(food|dinner|lunch|dining|restaurant|kitchen)") to "🍽️",
    )
    for ((pattern, emoji) in rules) {
        if (pattern.containsMatchIn(lower)) return emoji
    }
    for (tag in ContextTagTaxonomy.all) {
        if (tag.id == "custom") continue
        if (lower.contains(tag.id)) return tag.emoji
        val fragments = tag.label.split(Regex("[/,&()]"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 }
        for (f in fragments) {
            if (f.isNotEmpty() && lower.contains(f)) return tag.emoji
        }
    }
    return "🏷️"
}

private fun String.containsAny(vararg needles: String): Boolean = needles.any { contains(it) }