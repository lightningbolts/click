@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIButton
import platform.UIKit.UIMenu
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionTransitionCrossDissolve
import platform.UIKit.setAccessibilityLabel

/**
 * Snapshot of the route-owned button plane. The glass plate, trailing glass capsule, title column,
 * and their constraints stay mounted; only button meaning changes while media is visible.
 */
private data class StableOverlayChromeSnapshot(
    val backHandler: (() -> Unit)?,
    val backHidden: Boolean,
    val backSymbol: String?,
    val backAccessibility: String?,
    val searchHandler: (() -> Unit)?,
    val searchHidden: Boolean,
    val searchSymbol: String?,
    val searchAccessibility: String?,
    val searchMenu: UIMenu?,
    val searchShowsMenu: Boolean,
    val actionHandlers: List<(() -> Unit)?>,
    val actionHidden: List<Boolean>,
    val actionSymbols: List<String?>,
    val actionAccessibility: List<String?>,
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

    DisposableEffect(layer) {
        val snapshot = layer.captureStableOverlayChrome()
        layer.installStableMediaChrome(
            onClose = { close() },
            trailing = latestTrailing,
            snapshot = snapshot,
        )
        onDispose {
            layer.restoreStableOverlayChrome(snapshot)
        }
    }

    // Handler closures can change while the lightbox is open. Refresh them without replacing the
    // UIView instances, glass effect view, owner token, or title container.
    SideEffect {
        layer.refreshStableMediaChrome(
            onClose = { close() },
            trailing = latestTrailing,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.captureStableOverlayChrome(): StableOverlayChromeSnapshot =
    StableOverlayChromeSnapshot(
        backHandler = backTarget.handler,
        backHidden = backButton.hidden,
        backSymbol = paintedSymbols[backButton],
        backAccessibility = backButton.accessibilityLabel,
        searchHandler = searchTarget.handler,
        searchHidden = searchButton.hidden,
        searchSymbol = paintedSymbols[searchButton],
        searchAccessibility = searchButton.accessibilityLabel,
        searchMenu = searchButton.menu,
        searchShowsMenu = searchButton.showsMenuAsPrimaryAction,
        actionHandlers = actionTargets.map { it.handler },
        actionHidden = actionButtons.map { it.hidden },
        actionSymbols = actionButtons.map { paintedSymbols[it] },
        actionAccessibility = actionButtons.map { it.accessibilityLabel },
        actionMenus = actionButtons.map { it.menu },
        actionShowsMenu = actionButtons.map { it.showsMenuAsPrimaryAction },
        arrangedSubviews = trailingStack.arrangedSubviews.map { it as UIView },
        clusterHidden = trailingCluster.hidden,
        titleTrailingToClusterActive = titleTrailingToCluster?.active == true,
        titleTrailingToBarActive = titleTrailingToBar?.active == true,
        menuClicks = menuClicksByKey.toMap(),
    )

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.installStableMediaChrome(
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
    snapshot: StableOverlayChromeSnapshot,
) {
    backTarget.handler = onClose
    backButton.hidden = false
    morphLeadingChromeSymbol("xmark", "Close")
    configureStableMediaTrailing(trailing, snapshot)
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.refreshStableMediaChrome(
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
) {
    backTarget.handler = onClose
    // Do not call the normal bind path here. It owns route identity and its signature cache; media
    // only retargets the already-mounted anchor buttons.
    val currentVisible = trailingStack.arrangedSubviews.map { it as UIView }.filter { !it.hidden }
    if (currentVisible.size != trailing.size.coerceAtMost(actionButtons.size)) return

    currentVisible.forEachIndexed { index, view ->
        val button = view as? UIButton ?: return@forEachIndexed
        val action = trailing.getOrNull(index) ?: return@forEachIndexed
        setStableTrailingHandler(button, action.onClick)
        val menuKey = 100 + index
        action.menuItems.forEachIndexed { itemIndex, item ->
            menuClicksByKey["$menuKey:$itemIndex"] = item.onClick
        }
        bindNativeMenu(button, action, actionIndex = menuKey)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.configureStableMediaTrailing(
    trailing: List<NativeChromeAction>,
    snapshot: StableOverlayChromeSnapshot,
) {
    val actions = trailing.take(actionButtons.size)
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
                reusableRightAnchors.none { it === candidate }
            }.take(extraCount)
    val mediaButtons = extraButtons + reusableRightAnchors

    // Keep the trailing glass effect view itself alive. Only its arranged UIButton children are
    // retargeted. Choosing the existing right-most buttons keeps their screen-space anchors fixed
    // when media uses fewer actions than the route underneath.
    trailingStack.arrangedSubviews.map { it as UIView }.forEach { view ->
        trailingStack.removeArrangedSubview(view)
        view.removeFromSuperview()
    }

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
        val menuKey = 100 + index
        action.menuItems.forEachIndexed { itemIndex, item ->
            menuClicksByKey["$menuKey:$itemIndex"] = item.onClick
        }
        bindNativeMenu(button, action, actionIndex = menuKey)
        morphClusteredChromeSymbol(
            button = button,
            symbol = action.sfSymbol,
            accessibility = action.contentDescription,
        )
        trailingStack.addArrangedSubview(button)
    }

    val hasTrailing = mediaButtons.isNotEmpty()
    trailingCluster.hidden = !hasTrailing
    titleTrailingToCluster?.active = hasTrailing
    titleTrailingToBar?.active = !hasTrailing
    trailingCluster.superview?.layoutIfNeeded()
}

@OptIn(ExperimentalForeignApi::class)
private fun IosHostNavBarLayer.restoreStableOverlayChrome(snapshot: StableOverlayChromeSnapshot) {
    backTarget.handler = snapshot.backHandler
    snapshot.backSymbol?.let { symbol ->
        morphLeadingChromeSymbol(
            symbol = symbol,
            accessibility = snapshot.backAccessibility ?: if (symbol == "xmark") "Close" else "Back",
        )
    } ?: paintedSymbols.remove(backButton)
    backButton.hidden = snapshot.backHidden
    snapshot.backAccessibility?.let { backButton.setAccessibilityLabel(it) }

    menuClicksByKey.clear()
    menuClicksByKey.putAll(snapshot.menuClicks)

    searchTarget.handler = snapshot.searchHandler
    searchButton.hidden = snapshot.searchHidden
    searchButton.menu = snapshot.searchMenu
    searchButton.showsMenuAsPrimaryAction = snapshot.searchShowsMenu
    snapshot.searchSymbol?.let { symbol ->
        morphClusteredChromeSymbol(
            button = searchButton,
            symbol = symbol,
            accessibility = snapshot.searchAccessibility ?: "Search",
        )
    } ?: paintedSymbols.remove(searchButton)
    snapshot.searchAccessibility?.let { searchButton.setAccessibilityLabel(it) }

    actionButtons.forEachIndexed { index, button ->
        actionTargets[index].handler = snapshot.actionHandlers[index]
        button.hidden = snapshot.actionHidden[index]
        button.menu = snapshot.actionMenus[index]
        button.showsMenuAsPrimaryAction = snapshot.actionShowsMenu[index]
        val symbol = snapshot.actionSymbols[index]
        if (symbol != null) {
            morphClusteredChromeSymbol(
                button = button,
                symbol = symbol,
                accessibility = snapshot.actionAccessibility[index] ?: "Action",
            )
        } else {
            paintedSymbols.remove(button)
        }
        snapshot.actionAccessibility[index]?.let { button.setAccessibilityLabel(it) }
    }

    trailingStack.arrangedSubviews.map { it as UIView }.forEach { view ->
        trailingStack.removeArrangedSubview(view)
        view.removeFromSuperview()
    }
    snapshot.arrangedSubviews.forEach { view ->
        trailingStack.addArrangedSubview(view)
    }
    trailingCluster.hidden = snapshot.clusterHidden
    titleTrailingToCluster?.active = snapshot.titleTrailingToClusterActive
    titleTrailingToBar?.active = snapshot.titleTrailingToBarActive
    trailingCluster.superview?.layoutIfNeeded()
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
