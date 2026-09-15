package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * App-shell owned commands for the iOS root overflow control.
 *
 * Search is intentionally excluded because it has its own dedicated trailing control. The small
 * imperative change hook is necessary because UIKit chrome is rendered from a SideEffect rather
 * than from Compose nodes; changing shell commands must repaint the already-mounted native menu
 * without waiting for an unrelated screen recomposition.
 */
object NativeRootMenuRegistry {
    private var items: List<NativeChromeMenuItem> = emptyList()
    private var signature: List<Pair<String, String?>> = emptyList()
    internal var onChanged: (() -> Unit)? = null

    fun replace(next: List<NativeChromeMenuItem>) {
        val nextSignature = next.map { it.title to it.sfSymbol }
        val structuralChange = nextSignature != signature
        items = next
        signature = nextSignature
        if (structuralChange) onChanged?.invoke()
    }

    fun snapshot(): List<NativeChromeMenuItem> = items

    fun clear() {
        val hadItems = items.isNotEmpty()
        items = emptyList()
        signature = emptyList()
        if (hadItems) onChanged?.invoke()
    }
}
