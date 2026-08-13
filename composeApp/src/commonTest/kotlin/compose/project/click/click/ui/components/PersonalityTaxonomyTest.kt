package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalityTaxonomyTest {
    @Test
    fun traitsAreUniqueAndCountIsStable() {
        val lower = PERSONALITY_TRAITS.map { it.lowercase() }
        assertEquals(lower.size, lower.toSet().size)
        assertEquals(24, PERSONALITY_TRAITS.size)
        assertEquals(5, PERSONALITY_REQUIRED_TAG_COUNT)
    }

    @Test
    fun canonicalizeDropsUnknownAndDedupes() {
        val out =
            canonicalizePersonalityTags(
                listOf("witty", "Witty", "not-a-trait", "Empathetic", "  curious  "),
            )
        assertEquals(listOf("Witty", "Empathetic", "Curious"), out)
    }

    @Test
    fun predefinedSetMatchesCanonicalLabels() {
        val predefined = predefinedPersonalityTags()
        assertTrue(PERSONALITY_TRAITS.all { it.lowercase() in predefined })
    }
}
