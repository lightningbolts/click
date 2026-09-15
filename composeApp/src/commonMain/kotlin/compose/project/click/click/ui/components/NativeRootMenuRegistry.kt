package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * App-shell owned commands for the iOS root overflow control.
 *
 * The native chrome is hosted above Compose, so it cannot directly own navigation lambdas from
 * individual screens. The app shell publishes the currently valid root commands here; the iOS
 * overflow button snapshots them when it binds. Search is intentionally excluded because it has
 * its own dedicated trailing control.
 */
object NativeRootMenuRegistry {
    private var items: List<NativeChromeMenuItem> = emptyList()

    fun replace(next: List<NativeChromeMenuItem>) {
        items = next
    }

    fun snapshot(): List<NativeChromeMenuItem> = items

    fun clear() {
        items = emptyList()
    }
}
