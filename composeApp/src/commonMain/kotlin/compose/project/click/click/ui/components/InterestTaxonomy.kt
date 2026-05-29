package compose.project.click.click.ui.components

/** Minimum tags required during mobile onboarding (web onboarding uses 3). */
const val INTEREST_ONBOARDING_MIN_TAGS = 5

internal data class InterestCategory(
    val emoji: String,
    val label: String,
    val subcategories: List<String>,
)

internal val INTEREST_CATEGORIES = listOf(
    InterestCategory("🎵", "Music", listOf("Live Shows", "DJing", "Producing", "Guitar", "Piano", "Singing", "Alto Sax", "Tenor Sax", "Drums", "Violin", "Bass", "Songwriting")),
    InterestCategory("🎼", "Instruments", listOf("Alto Sax", "Tenor Sax", "Trumpet", "Clarinet", "Cello", "Flute", "Ukulele", "Synth", "Beat Making")),
    InterestCategory("🥾", "Hiking", listOf("Day Hikes", "Backpacking", "Trail Running", "Rock Climbing", "Scrambling", "Nature Walks")),
    InterestCategory("☕", "Coffee", listOf("Espresso", "Pour Over", "Cafe Hopping", "Latte Art", "Home Brewing")),
    InterestCategory("🎮", "Gaming", listOf("PC", "Console", "Indie", "Board Games", "VR", "Competitive", "Co-op", "RPG", "Strategy")),
    InterestCategory("📚", "Reading", listOf("Fiction", "Non-Fiction", "Sci-Fi", "Fantasy", "Book Clubs", "Poetry")),
    InterestCategory("💪", "Fitness", listOf("Gym", "Yoga", "CrossFit", "Running", "Swimming", "Martial Arts", "Pilates", "Cycling")),
    InterestCategory("💻", "Tech", listOf("AI/ML", "Web Dev", "Mobile Dev", "Cybersecurity", "Hardware", "Open Source", "Cloud", "Data Science")),
    InterestCategory("🎨", "Art", listOf("Painting", "Sketching", "Digital Art", "Sculpture", "Ceramics", "Street Art", "Calligraphy", "Graphic Design")),
    InterestCategory("🎬", "Film", listOf("Indie Film", "Horror", "Documentaries", "Animation", "Film Making")),
    InterestCategory("🍕", "Food", listOf("Cooking", "Baking", "Food Trucks", "Fine Dining", "Vegan", "Meal Prep")),
    InterestCategory("✈️", "Travel", listOf("Backpacking", "Road Trips", "City Breaks", "Solo Travel", "Camping", "Digital Nomad", "Hostels")),
    InterestCategory("⚽", "Sports", listOf("Basketball", "Soccer", "Baseball", "Football", "Softball", "Ultimate", "Tennis", "Volleyball", "Skiing", "Surfing")),
    InterestCategory("🏃", "Outdoor Sports", listOf("Running", "Cycling", "Triathlon", "Climbing", "Skiing", "Snowboarding", "Surfing")),
    InterestCategory("🤝", "Volunteering", listOf("Environment", "Education", "Community", "Animal Welfare", "Mentoring")),
    InterestCategory("📸", "Photography", listOf("Street", "Portrait", "Landscape", "Film Photography", "Drone", "Concert Photography", "Editing")),
    InterestCategory("🧘", "Wellness", listOf("Meditation", "Mindfulness", "Breathwork", "Journaling", "Mental Health")),
    InterestCategory("🗣️", "Languages", listOf("Spanish", "French", "Mandarin", "Japanese", "Korean", "Language Exchange")),
    InterestCategory("🎭", "Performing Arts", listOf("Theater", "Improv", "Acting", "Stand-up Comedy", "Dance")),
    InterestCategory("🐶", "Animals", listOf("Dogs", "Cats", "Birds", "Animal Rescue", "Pet Training")),
    InterestCategory("🧩", "Puzzles & Strategy", listOf("Chess", "Sudoku", "Escape Rooms", "Crosswords", "Go")),
)

internal fun predefinedInterestTags(): Set<String> =
    INTEREST_CATEGORIES.flatMap { category ->
        listOf(category.label) + category.subcategories
    }.map { it.lowercase() }.toSet()

/** Keeps only taxonomy tags (drops legacy custom strings from saved profiles). */
internal fun filterToPredefinedInterestTags(tags: List<String>): List<String> {
    val predefined = predefinedInterestTags()
    return tags.filter { it.lowercase() in predefined }
}
