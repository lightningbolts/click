package compose.project.click.click.ui.components.native

import kotlin.test.Test
import kotlin.test.assertTrue

class NavIconSfSymbolsTest {

    @Test
    fun migratedNavIcons_resolveToNonBlankSfSymbols() {
        migratedNavIcons.forEach { icon ->
            val symbol = icon.toSfSymbolName()
            assertTrue(
                !symbol.isNullOrBlank(),
                "Missing SF Symbol mapping for $icon",
            )
        }
    }
}
