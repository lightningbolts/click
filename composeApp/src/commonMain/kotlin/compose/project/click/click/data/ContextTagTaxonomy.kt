package compose.project.click.click.data

import compose.project.click.click.data.models.ContextTag

object ContextTagTaxonomy {
    val all: List<ContextTag> = listOf(
        ContextTag("lecture", "Lecture / Class", "🎓"),
        ContextTag("study", "Study Session", "📚"),
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
        }

        if (suggestions.isEmpty()) {
            all.take(4).forEach(suggestions::add)
        }

        return suggestions.take(4)
    }
}

private fun String.containsAny(vararg needles: String): Boolean = needles.any { contains(it) }