package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardVisualHeroPriorityTest {
    @Test
    fun blankOrMissingImageKeepsGeneratedPattern() {
        assertTrue(cardVisualHeroUsesGeneratedPattern(null))
        assertTrue(cardVisualHeroUsesGeneratedPattern(""))
        assertTrue(cardVisualHeroUsesGeneratedPattern("   "))
    }

    @Test
    fun coverUrlSkipsGeneratedPatternUntilLoadFails() {
        assertFalse(cardVisualHeroUsesGeneratedPattern("https://cdn.example/cover.jpg"))
        assertTrue(
            cardVisualHeroUsesGeneratedPattern(
                imageUrl = "https://cdn.example/cover.jpg",
                imageFailed = true,
            ),
        )
    }
}
