package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun displayGroupsPartitionTheCanonicalTraits() {
        val grouped = PERSONALITY_TRAIT_GROUPS.flatMap { it.traits }
        assertEquals(PERSONALITY_TRAITS.size, grouped.size)
        assertEquals(PERSONALITY_TRAITS.toSet(), grouped.toSet())
        PERSONALITY_TRAIT_GROUPS.forEach { group ->
            assertEquals(group.traits.size, group.traits.toSet().size)
        }
    }
}
