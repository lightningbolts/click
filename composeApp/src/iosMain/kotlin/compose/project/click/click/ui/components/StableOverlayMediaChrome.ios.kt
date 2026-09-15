@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.QuartzCore.CATransaction
import platform.UIKit.UIButton
import platform.UIKit.UIMenu
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionTransitionCrossDissolve
import platform.UIKit.setAccessibilityLabel

/**
 * Snapshot of the route-owned button plane. The bar, glass plate, title column, trailing glass
 * capsule, and existing route buttons remain mounted while media temporarily changes their role.
 */
private data class StableOverlayChromeSnapshot(
    val backHandler: (() -> Unit)?,
    val backHidden: Boolean,
    val backSymbol: String?,
    val backAccessibility: String,
    val searchHandler: (() -> Unit)?,
    val searchHidden: Boolean,
    val searchSymbol: String?,
    val searchAccessibility: String,
    val searchMenu: UIMenu?,
    val searchShowsMenu: Boolean,
    val actionHandlers: List<(() -> Unit)?>,
    val actionHidden: List<Boolean>,
    val actionSymbols: List<String?>,
    val actionAccessibility: List<String>,
    val actionMenus: List<UIMenu?>,
    val actionShowsMenu: List<Boolean>,
    val arrangedSubviews: List<UIView>,
    val clusterHidden: Boolean,
    val titleTrailingToClusterActive: Boolean,
    val titleTrailingToBarActive: Boolean,
    val menuClicks: Map<String, () -> Unit>,
)

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun ApplyStableOverlayMediaChrome(
    active: Boolean,
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
) {
    // Profile/root lightboxes do not necessarily have an overlay route underneath. Keep the
    // existing fallback for that case; the important chat/media path never takes ownership away
    // from the conversation binder.
    val hasUnderlyingOverlay = IosNavChrome.hasOverlayBinder()
    if (!active || !hasUnderlyingOverlay) {
        ApplyOverlayMediaChrome(
            active = active,
            onClose = onClose,
            trailing = trailing,
        )
        return
    }

    val close by rememberUpdatedState(onClose)
    val latestTrailing by rememberUpdatedState(trailing)
    val layer = IosNavChrome.overlay
    val snapshotHolder = remember(layer) { arrayOfNulls<StableOverlayChromeSnapshot>(1) }

    DisposableEffect(layer) {
        val snapshot = layer.captureStableOverlayChrome()
        snapshotHolder[0] = snapshot
        layer.logStableChromeIdentity("conversation-before-media")
        layer.installStableMediaChrome(
            onClose = { close() },
            trailing = latestTrailing,
            snapshot = snapshot,
        )
        layer.logStableChromeIdentity("media-installed")
        onDispose {
            // The route may have rebound handlers/actions while media was open. Restore the most
            // recently observed route snapshot instead of the one captured on lightbox entry.
            layer.restoreStableOverlayChrome(snapshotHolder[0] ?: snapshot)
            snapshotHolder[0] = null
            layer.logStableChromeIdentity("conversation-restored")
        }
    }

    // Handler closures and chat presence can change while the lightbox is open. The underlying
    // route binder runs before this overlay in composition order. If it repaints the leading route
    // symbol, capture that newly authoritative route plane before putting media semantics back on
    // the same UIViews. If nothing underneath changed, the leading button is still xmark and the
    // last route snapshot remains authoritative.
    SideEffect {
        snapshotHolder[0]?.let { previousSnapshot ->
            val routeSnapshot =
                if (layer.paintedSymbols[layer.backButton] != "xmark") {
                    layer.captureStableOverlayChrome()
                } else {
                    previousSnapshot
                }
            snapshotHolder[0] = routeSnapshot
            layer.refreshStableMediaChrome(
                onClose = { close() },
                trailing = latestTrailing,
                snapshot = routeSnapshot,
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.captureStableOverlayChrome(): StableOverlayChromeSnapshot {
    val actionSymbols = actionButtons.map { paintedSymbols[it] }
    return StableOverlayChromeSnapshot(
        backHandler = backTarget.handler,
        backHidden = backButton.hidden,
        backSymbol = paintedSymbols[backButton],
        backAccessibility = paintedAccessibility[backButton] ?: stableChromeAccessibility(paintedSymbols[backButton], leading = true),
        searchHandler = searchTarget.handler,
        searchHidden = searchButton.hidden,
        searchSymbol = paintedSymbols[searchButton],
        searchAccessibility = paintedAccessibility[searchButton] ?: "Search",
        searchMenu = searchButton.menu,
        searchShowsMenu = searchButton.showsMenuAsPrimaryAction,
        actionHandlers = actionTargets.map { it.handler },
        actionHidden = actionButtons.map { it.hidden },
        actionSymbols = actionSymbols,
        actionAccessibility =
            actionButtons.mapIndexed { index, button ->
                paintedAccessibility[button]
                    ?: stableChromeAccessibility(actionSymbols[index], leading = false)
            },
        actionMenus = actionButtons.map { it.menu },
        actionShowsMenu = actionButtons.map { it.showsMenuAsPrimaryAction },
        arrangedSubviews = trailingStack.arrangedSubviews.map { it as UIView },
        clusterHidden = trailingCluster.hidden,
        titleTrailingToClusterActive = titleTrailingToCluster?.active == true,
        titleTrailingToBarActive = titleTrailingToBar?.active == true,
        menuClicks = menuClicksByKey.toMap(),
    )
}

private fun stableChromeAccessibility(
    symbol: String?,
    leading: Boolean,
): String =
    when (symbol) {
        "xmark" -> "Close"
        "chevron.backward", "arrow.left" -> "Back"
        "ellipsis", "ellipsis.circle" -> "More options"
        "magnifyingglass" -> "Search"
        "pencil" -> "Rename group"
        "phone", "phone.fill" -> "Call"
        "video", "video.fill" -> "Video call"
        "square.and.arrow.up" -> "Share"
        "square.and.arrow.down" -> "Save"
        else -> if (leading) "Back" else "Action"
    }

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.installStableMediaChrome(
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
    snapshot: StableOverlayChromeSnapshot,
) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    backTarget.handler = onClose
    backButton.hidden = false
    configureStableMediaTrailing(trailing, snapshot)
    CATransaction.commit()
    morphLeadingChromeSymbol("xmark", "Close")
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.refreshStableMediaChrome(
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
    snapshot: StableOverlayChromeSnapshot,
) {
    backTarget.handler = onClose
    backButton.hidden = false
    if (paintedSymbols[backButton] != "xmark") {
        morphLeadingChromeSymbol("xmark", "Close")
    }

    val actions = trailing.take(actionButtons.size)
    var currentVisible =
        trailingStack.arrangedSubviews
            .mapNotNull { it as? UIButton }
            .filter { !it.hidden }
    if (currentVisible.size != actions.size) {
        configureStableMediaTrailing(trailing, snapshot)
        currentVisible =
            trailingStack.arrangedSubviews
                .mapNotNull { it as? UIButton }
                .filter { !it.hidden }
    }
    if (currentVisible.size != actions.size) return

    currentVisible.forEachIndexed { index, button ->
        val action = actions[index]
        setStableTrailingHandler(button, action.onClick)
        bindStableMediaMenu(button, action, index)
        if (paintedSymbols[button] != action.sfSymbol) {
            morphClusteredChromeSymbol(
                button = button,
                symbol = action.sfSymbol,
                accessibility = action.contentDescription,
            )
        } else {
            button.setAccessibilityLabel(action.contentDescription)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.configureStableMediaTrailing(
    trailing: List<NativeChromeAction>,
    snapshot: StableOverlayChromeSnapshot,
) {
    val actions = trailing.take(actionButtons.size)
    val currentlyArranged = trailingStack.arrangedSubviews.map { it as UIView }
    val baseVisibleButtons =
        snapshot.arrangedSubviews
            .mapNotNull { it as? UIButton }
            .filter { button -> !button.hidden }
    val reuseCount = minOf(actions.size, baseVisibleButtons.size)
    val reusableRightAnchors =
        if (reuseCount == 0) emptyList() else baseVisibleButtons.takeLast(reuseCount)
    val extraCount = actions.size - reusableRightAnchors.size
    val extraButtons =
        actionButtons
            .filter { candidate ->
                reusableRightAnchors.none { it === candidate } &&
                    baseVisibleButtons.none { it === candidate }
            }.take(extraCount)

    // Extra media controls are inserted to the LEFT of the route-owned controls. The route's
    // right-most button therefore remains at the exact trailing anchor while the cluster grows
    // leftward. We intentionally keep these extra UIButtons arranged-but-hidden after dismissal;
    // UIStackView collapses hidden arranged views, so they cost no geometry but can be reused on the
    // next media transition without detaching/re-attaching anything.
    extraButtons.asReversed().forEach { button ->
        if (currentlyArranged.none { it === button }) {
            button.hidden = true
            trailingStack.insertArrangedSubview(button, atIndex = 0uL)
        }
    }

    val mediaButtons = extraButtons + reusableRightAnchors
    val allTrailingButtons = listOf(searchButton) + actionButtons
    allTrailingButtons.forEach { button ->
        button.hidden = true
        button.menu = null
        button.showsMenuAsPrimaryAction = false
        setStableTrailingHandler(button, null)
    }

    mediaButtons.forEachIndexed { index, button ->
        val action = actions[index]
        button.hidden = false
        setStableTrailingHandler(button, action.onClick)
        bindStableMediaMenu(button, action, index)
        morphClusteredChromeSymbol(
            button = button,
            symbol = action.sfSymbol,
            accessibility = action.contentDescription,
        )
    }

    val hasTrailing = mediaButtons.isNotEmpty()
    trailingCluster.hidden = !hasTrailing
    titleTrailingToCluster?.active = hasTrailing
    titleTrailingToBar?.active = !hasTrailing
    trailingCluster.superview?.layoutIfNeeded()
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.bindStableMediaMenu(
    button: UIButton,
    action: NativeChromeAction,
    mediaIndex: Int,
) {
    val menuKey = 100 + mediaIndex
    action.menuItems.forEachIndexed { itemIndex, item ->
        menuClicksByKey["$menuKey:$itemIndex"] = item.onClick
    }
    bindNativeMenu(button, action, actionIndex = menuKey)
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.restoreStableOverlayChrome(snapshot: StableOverlayChromeSnapshot) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)

    backTarget.handler = snapshot.backHandler
    backButton.hidden = snapshot.backHidden

    menuClicksByKey.clear()
    menuClicksByKey.putAll(snapshot.menuClicks)

    searchTarget.handler = snapshot.searchHandler
    searchButton.hidden = snapshot.searchHidden
    searchButton.menu = snapshot.searchMenu
    searchButton.showsMenuAsPrimaryAction = snapshot.searchShowsMenu

    actionButtons.forEachIndexed { index, button ->
        actionTargets[index].handler = snapshot.actionHandlers[index]
        button.hidden = snapshot.actionHidden[index]
        button.menu = snapshot.actionMenus[index]
        button.showsMenuAsPrimaryAction = snapshot.actionShowsMenu[index]
    }

    trailingCluster.hidden = snapshot.clusterHidden
    titleTrailingToCluster?.active = snapshot.titleTrailingToClusterActive
    titleTrailingToBar?.active = snapshot.titleTrailingToBarActive
    trailingCluster.superview?.layoutIfNeeded()
    CATransaction.commit()

    snapshot.backSymbol?.let { symbol ->
        morphLeadingChromeSymbol(
            symbol = symbol,
            accessibility = snapshot.backAccessibility,
        )
    } ?: paintedSymbols.remove(backButton)
    backButton.setAccessibilityLabel(snapshot.backAccessibility)
    paintedAccessibility[backButton] = snapshot.backAccessibility

    snapshot.searchSymbol?.let { symbol ->
        morphClusteredChromeSymbol(
            button = searchButton,
            symbol = symbol,
            accessibility = snapshot.searchAccessibility,
        )
    } ?: paintedSymbols.remove(searchButton)
    searchButton.setAccessibilityLabel(snapshot.searchAccessibility)
    paintedAccessibility[searchButton] = snapshot.searchAccessibility

    actionButtons.forEachIndexed { index, button ->
        val symbol = snapshot.actionSymbols[index]
        if (symbol != null) {
            morphClusteredChromeSymbol(
                button = button,
                symbol = symbol,
                accessibility = snapshot.actionAccessibility[index],
            )
        } else {
            paintedSymbols.remove(button)
        }
        button.setAccessibilityLabel(snapshot.actionAccessibility[index])
        paintedAccessibility[button] = snapshot.actionAccessibility[index]
    }
}

private fun IosHostNavBarLayer.setStableTrailingHandler(
    button: UIButton,
    handler: (() -> Unit)?,
) {
    when {
        button === searchButton -> searchTarget.handler = handler
        else -> {
            val index = actionButtons.indexOfFirst { it === button }
            if (index >= 0) actionTargets[index].handler = handler
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.morphLeadingChromeSymbol(
    symbol: String,
    accessibility: String,
) {
    if (paintedSymbols[backButton] == symbol) {
        backButton.setAccessibilityLabel(accessibility)
        paintedAccessibility[backButton] = accessibility
        return
    }
    val transitionView = if (usesGlassButtons) backGlyph else backButton
    UIView.transitionWithView(
        transitionView,
        duration = 0.16,
        options = UIViewAnimationOptionTransitionCrossDissolve,
        animations = {
            paintChromeButton(
                button = backButton,
                symbol = symbol,
                accessibility = accessibility,
                clustered = false,
            )
        },
        completion = null,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.morphClusteredChromeSymbol(
    button: UIButton,
    symbol: String,
    accessibility: String,
) {
    if (paintedSymbols[button] == symbol) {
        button.setAccessibilityLabel(accessibility)
        paintedAccessibility[button] = accessibility
        return
    }
    UIView.transitionWithView(
        button,
        duration = 0.16,
        options = UIViewAnimationOptionTransitionCrossDissolve,
        animations = {
            paintChromeButton(
                button = button,
                symbol = symbol,
                accessibility = accessibility,
                clustered = true,
            )
        },
        completion = null,
    )
}

private fun IosHostNavBarLayer.logStableChromeIdentity(stage: String) {
    if (NSProcessInfo.processInfo.environment["CLICK_CHROME_IDENTITY_DEBUG"] != "1") return
    val trailingIds =
        (listOf(searchButton) + actionButtons)
            .joinToString(prefix = "[", postfix = "]") { it.hashCode().toString(16) }
    println(
        "[ClickChromeIdentity] stage=$stage " +
            "lead=${backButton.hashCode().toString(16)} " +
            "center=${titleColumn.hashCode().toString(16)} " +
            "trail=$trailingIds " +
            "glass=${glassPlate.hashCode().toString(16)} " +
            "cluster=${trailingCluster.hashCode().toString(16)} " +
            "bar=${bar.hashCode().toString(16)}",
    )
}
