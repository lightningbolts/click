@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package compose.project.click.click.ui.components

import androidx.compose.ui.test.runComposeUiTest
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PersistentNativeChromeTest {
    @Test
    fun compactTitleLandsOnTheMeasuredLabelAtEveryWidth() =
        runComposeUiTest {
            runOnUiThread {
                NativeRootMenuRegistry.replace(listOf(NativeChromeMenuItem("Settings", "gearshape", {})))
                try {
                    for (width in listOf(320.0, 375.0, 430.0, 768.0, 844.0, 1024.0)) {
                        val host = UIViewController()
                        host.view.setFrame(CGRectMake(0.0, 0.0, width, 844.0))
                        val layer = IosHostNavBarLayer()
                        layer.renderTestHeader(host, root = true)
                        val expected = layer.titleLabel.convertRect(layer.titleLabel.bounds, toView = layer.chromeRow)
                        host.view.setFrame(CGRectMake(0.0, 0.0, 390.0, 844.0))
                        layer.renderTestHeader(host, root = true)
                        layer.renderTestHeader(host, root = false)
                        host.view.setFrame(CGRectMake(0.0, 0.0, width, 844.0))
                        host.view.layoutIfNeeded()
                        layer.applyPersistentChromeMorphProgress(1f)
                        val incoming =
                            layer.chromeRow.subviews
                                .filterIsInstance<UIView>()
                                .flatMap { it.subviews.filterIsInstance<UILabel>() }
                                .single { it != layer.titleLabel && it.text == "Clicks" }
                        val actual = incoming.convertRect(incoming.bounds, toView = layer.chromeRow)
                        assertEquals(expected.useContents { origin.x }, actual.useContents { origin.x }, 0.5, "width=$width")
                        assertEquals(expected.useContents { origin.y }, actual.useContents { origin.y }, 0.5, "width=$width")
                        layer.resetPersistentChromeMorphVisuals(animated = false)
                    }
                } finally {
                    NativeRootMenuRegistry.clear()
                }
            }
        }

    @Test
    fun popKeepsControlsAndCallbacksThroughCommitAndNextRootRender() =
        runComposeUiTest {
            runOnUiThread {
                val host = UIViewController()
                host.view.setFrame(CGRectMake(0.0, 0.0, 390.0, 844.0))
                val layer = IosHostNavBarLayer()
                layer.applyAppearance(true, false, true)
                NativeRootMenuRegistry.replace(listOf(NativeChromeMenuItem("Settings", "gearshape", {})))
                try {
                    layer.renderTestHeader(host, root = true)
                    layer.renderTestHeader(host, root = false)
                    val carrier = layer.actionButtons[0]
                    val glyph = layer.trailingGlyphs[carrier]
                    layer.applyPersistentChromeMorphProgress(0.25f)
                    assertEquals("chevron.backward", layer.paintedSymbols[layer.backButton])
                    assertEquals("ellipsis", layer.paintedSymbols[carrier])
                    layer.applyPersistentChromeMorphProgress(0.75f)
                    assertEquals("ellipsis", layer.paintedSymbols[layer.backButton])
                    assertEquals("magnifyingglass", layer.paintedSymbols[carrier])
                    assertEquals(glyph, layer.trailingGlyphs[carrier])
                    val menu = layer.backButton.menu
                    layer.applyPersistentChromeMorphProgress(1f)
                    layer.resetPersistentChromeMorphVisuals(animated = false)
                    var searches = 0
                    repeat(2) {
                        layer.renderTestHeader(host, root = true, search = { searches++ })
                        assertEquals(menu, layer.backButton.menu)
                        assertEquals(carrier, layer.trailingStack.arrangedSubviews.single())
                        assertFalse(layer.backButton.hidden)
                    }
                    layer.actionTargets[0].handler?.invoke()
                    assertEquals(1, searches)
                } finally {
                    NativeRootMenuRegistry.clear()
                }
            }
        }

    @Test
    fun reversingPopRestoresBothSourceSymbols() =
        runComposeUiTest {
            runOnUiThread {
                val host = UIViewController()
                host.view.setFrame(CGRectMake(0.0, 0.0, 390.0, 844.0))
                val layer = IosHostNavBarLayer()
                NativeRootMenuRegistry.replace(listOf(NativeChromeMenuItem("Settings", "gearshape", {})))
                try {
                    layer.renderTestHeader(host, root = true)
                    layer.renderTestHeader(host, root = false)
                    layer.applyPersistentChromeMorphProgress(0.8f)
                    layer.applyPersistentChromeMorphProgress(0.2f)
                    layer.resetPersistentChromeMorphVisuals(animated = false)
                    assertEquals("chevron.backward", layer.paintedSymbols[layer.backButton])
                    assertEquals("ellipsis", layer.paintedSymbols[layer.actionButtons[0]])
                    assertFalse(layer.backButton.showsMenuAsPrimaryAction)
                } finally {
                    NativeRootMenuRegistry.clear()
                }
            }
        }

    @Test
    fun mediaHeaderFollowsTheNewHostsSafeAreaAndWidth() =
        runComposeUiTest {
            runOnUiThread {
                val layer = IosHostNavBarLayer()
                for (width in listOf(320.0, 390.0, 430.0, 768.0, 844.0, 1024.0)) {
                    val host = UIViewController()
                    host.view.setFrame(CGRectMake(0.0, 0.0, width, 844.0))
                    layer.renderTestHeader(host, root = false)
                    host.view.layoutIfNeeded()
                    val safe = host.view.safeAreaLayoutGuide.layoutFrame
                    val row = layer.chromeRow.frame
                    assertEquals(safe.useContents { origin.y }, row.useContents { origin.y }, 0.5, "top at width=$width")
                    assertEquals(safe.useContents { origin.x }, row.useContents { origin.x }, 0.5, "left at width=$width")
                    assertEquals(safe.useContents { size.width }, row.useContents { size.width }, 0.5, "width=$width")
                }
            }
        }
}

private fun IosHostNavBarLayer.renderTestHeader(
    host: UIViewController,
    root: Boolean,
    search: () -> Unit = {},
) {
    update(
        owner = host,
        host = host,
        title = if (root) "Clicks" else "Conversation",
        subtitle = null,
        presenceOnline = null,
        identity = null,
        visible = true,
        collapseFraction = 1f,
        hasSubtitle = false,
        onOpenSearch = if (root) search else null,
        onNavigateBack = if (root) null else ({}),
        trailingActions = if (root) emptyList() else listOf(NativeChromeAction("ellipsis", "More options", {})),
        collapseSearchIntoBar = false,
    )
    if (root) {
        applyPersistentRootTitleGeometry(true)
        applyPersistentRootMenu(null)
    }
    animatePersistentSemanticSettle(enabled = false)
}
