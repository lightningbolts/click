package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreRingTest {
    @Test
    fun ringMatchesIosCoreRing() {
        // iOS DesignSystem/Colors.swift `coreRing`: gold → amber → violet → action violet → gold.
        assertEquals(
            listOf(Color(0xFFF5C451), Color(0xFFE39A3B), Color(0xFFA855F7), Color(0xFF7C3AED), Color(0xFFF5C451)),
            CoreRingColors,
        )
        assertEquals(CoreRingColors.first(), CoreRingColors.last(), "the sweep closes without a seam")
    }
}
