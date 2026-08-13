package compose.project.click.click.ui.components // pragma: allowlist secret

/** Exactly five traits required during onboarding and settings save. */
const val PERSONALITY_REQUIRED_TAG_COUNT = 5

/**
 * Curated social traits (not Big Five jargon). Keep in sync with
 * `click-web/lib/personality/taxonomy.ts`.
 */
val PERSONALITY_TRAITS: List<String> =
    listOf(
        "Adventurous",
        "Empathetic",
        "Witty",
        "Curious",
        "Grounded",
        "Spontaneous",
        "Thoughtful",
        "Outgoing",
        "Chill",
        "Ambitious",
        "Creative",
        "Loyal",
        "Playful",
        "Independent",
        "Optimistic",
        "Analytical",
        "Warm",
        "Bold",
        "Easygoing",
        "Passionate",
        "Observant",
        "Supportive",
        "Humorous",
        "Authentic",
    )

internal fun predefinedPersonalityTags(): Set<String> = PERSONALITY_TRAITS.map { it.lowercase() }.toSet()

internal fun canonicalizePersonalityTags(tags: List<String>): List<String> {
    val byLower = PERSONALITY_TRAITS.associateBy { it.lowercase() }
    return tags.mapNotNull { byLower[it.trim().lowercase()] }.distinct()
}
