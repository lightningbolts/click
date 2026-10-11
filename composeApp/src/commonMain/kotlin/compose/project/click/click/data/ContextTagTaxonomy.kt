package compose.project.click.click.data

import compose.project.click.click.data.models.ContextTag

object ContextTagTaxonomy {
    val all: List<ContextTag> =
        listOf(
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
            ContextTag("at_event", "At event", "📍"),
            ContextTag("custom", "Other...", "✏️"),
        )

    fun displayLabel(tagId: String): String {
        val trimmed = tagId.trim()
        if (trimmed.isEmpty()) return tagId
        all.firstOrNull { it.id == trimmed }?.let { return it.label }
        if (trimmed == "extended_hangout") return "Extended hangout"
        return trimmed
    }

    /** Legacy entry point: the time alone plus a place name (weekday assumed). */
    fun suggest(
        locationName: String?,
        hourOfDay: Int,
    ): List<ContextTag> = suggest(TagSignals(hourOfDay = hourOfDay, isWeekend = false, placeName = locationName))

    /**
     * Up to four tags for an encounter, best first (port of iOS `ContextTagTaxonomy.suggest`,
     * G4-11): each signal adds weight to the tags it points at, so a café at noon suggests Cafe
     * before Dining, a loud room at night leans to Party, and what these people usually tag
     * together ranks highest of all.
     */
    fun suggest(signals: TagSignals): List<ContextTag> {
        val scores = mutableMapOf<String, Double>()

        fun add(
            weight: Double,
            vararg ids: String,
        ) {
            for (id in ids) scores[id] = (scores[id] ?: 0.0) + weight
        }

        signals.placeType?.lowercase()?.let { type ->
            placeTypes.firstOrNull { type in it.first }?.second?.forEachIndexed { index, id ->
                add(if (index == 0) 3.0 else 1.5, id)
            }
        }
        val place = signals.placeName.orEmpty().lowercase()
        if (place.isNotEmpty()) {
            placeKeywords.firstOrNull { (words, _) -> words.any { place.contains(it) } }?.second?.forEachIndexed { index, id ->
                add(if (index == 0) 2.0 else 1.0, id)
            }
        }

        val hour = signals.hourOfDay
        val isWeekend = signals.isWeekend
        if (hour >= 22 || hour <= 2) add(1.5, "party", "bar")
        if (hour in 6..10) add(1.0, "cafe")
        if (hour in 8..17 && !isWeekend) add(1.0, "lecture", "study")
        if (hour in 11..14) {
            add(1.5, "dining")
            add(0.5, "cafe")
        }
        if (hour in 17..21) {
            add(1.0, "event", "club")
            add(0.5, "dining")
        }
        if (isWeekend) add(0.5, "outdoor", "party")

        if (signals.isMoving) {
            add(2.0, "transit")
            add(1.0, "outdoor", "sports")
        }
        signals.noiseDecibels?.let { db ->
            if (db >= 75) {
                add(2.0, "party", "bar")
            } else if (db >= 60) {
                add(0.5, "dining", "cafe", "event")
            }
            if (db < 40) add(1.5, "study")
        }
        if (signals.atEvent) add(4.0, "event")

        // What these people tag together, weighted by how often (capped so one habit can't
        // crowd out the place itself).
        signals.pastTags
            .groupingBy { it }
            .eachCount()
            .forEach { (tag, count) -> add(minOf(count, 3) * 2.0, tag) }

        val ranked =
            all
                .withIndex()
                .filter { it.value.id != "at_event" && it.value.id != "custom" && (scores[it.value.id] ?: 0.0) > 0 }
                .sortedWith(compareByDescending<IndexedValue<ContextTag>> { scores[it.value.id] ?: 0.0 }.thenBy { it.index })
                .map { it.value }
        return if (ranked.isEmpty()) all.take(4) else ranked.take(4)
    }

    /** OpenStreetMap place types (`semantic_location.type`); the first tag is the strongest. */
    private val placeTypes: List<Pair<Set<String>, List<String>>> =
        listOf(
            setOf("university", "college", "school") to listOf("lecture", "study"),
            setOf("library") to listOf("study"),
            setOf("research", "research_institute", "office", "coworking", "coworking_space") to listOf("study", "club"),
            setOf("cafe") to listOf("cafe", "study"),
            setOf("restaurant", "fast_food", "food_court", "ice_cream") to listOf("dining"),
            setOf("bar", "pub", "nightclub", "biergarten") to listOf("bar", "party"),
            setOf("gym", "fitness_centre", "sports_centre", "stadium", "pitch", "swimming_pool", "track") to listOf("sports", "gym"),
            setOf("park", "garden", "nature_reserve", "beach", "playground", "viewpoint") to listOf("outdoor"),
            setOf("bus_stop", "station", "platform", "halt", "subway_entrance", "ferry_terminal") to listOf("transit"),
            setOf("dormitory", "residential", "apartments", "house") to listOf("dorm"),
            setOf(
                "theatre",
                "arts_centre",
                "cinema",
                "events_venue",
                "conference_centre",
                "exhibition_centre",
                "community_centre",
            ) to listOf("event", "conference"),
        )

    /** Words in a place's name; the first tag is the strongest. */
    private val placeKeywords: List<Pair<List<String>, List<String>>> =
        listOf(
            listOf("dorm", "residence", "residential", "housing", "apartment", "suite") to listOf("dorm", "study"),
            listOf("library") to listOf("study"),
            listOf("hall", "building", "classroom", "lecture", "school", "campus", "university") to listOf("lecture", "study"),
            listOf("cafe", "café", "coffee", "espresso", "starbucks") to listOf("cafe", "study"),
            listOf("gym", "rec", "fitness", "arena") to listOf("gym", "sports"),
            listOf("bar", "pub", "club", "lounge") to listOf("bar", "party"),
            listOf("bus", "train", "station", "stop", "transit") to listOf("transit"),
            listOf("park", "trail", "beach", "garden") to listOf("outdoor"),
            listOf("stadium", "event", "center", "theater") to listOf("event", "conference"),
            listOf("food", "dining", "restaurant", "kitchen", "bistro") to listOf("dining", "cafe"),
        )

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
    val rules: List<Pair<Regex, String>> =
        listOf(
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
        val fragments =
            tag.label
                .split(Regex("[/,&()]"))
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 }
        for (f in fragments) {
            if (f.isNotEmpty() && lower.contains(f)) return tag.emoji
        }
    }
    return "🏷️"
}

/**
 * What is known about an encounter when suggesting its tags (iOS `TagSignals`). Everything but
 * the time is optional: suggestions start from the time alone and sharpen as the place and
 * sensors load.
 */
data class TagSignals(
    /** Local hour (0–23) of the encounter. */
    val hourOfDay: Int,
    /** Saturday or Sunday, local time. */
    val isWeekend: Boolean,
    val placeName: String? = null,
    /** OpenStreetMap type of the place ("cafe", "university"). */
    val placeType: String? = null,
    val isMoving: Boolean = false,
    val noiseDecibels: Double? = null,
    val atEvent: Boolean = false,
    /** Tags from earlier encounters with the same people. */
    val pastTags: List<String> = emptyList(),
) {
    companion object {
        /** Motion variance (m/s²)² above which the phone was clearly being carried along. */
        const val MOVING_VARIANCE: Double = 1.0
    }
}
