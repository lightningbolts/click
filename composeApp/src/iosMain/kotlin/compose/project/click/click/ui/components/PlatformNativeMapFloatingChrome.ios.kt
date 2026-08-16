@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIViewController
import platform.UIKit.setAccessibilityLabel
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeMapFloatingChrome(
    visible: Boolean,
    layerLabel: String,
    layerOptions: List<NativeMapLayerOption>,
    onToggleLayerId: (String) -> Unit,
    onDropBeacon: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    bottomPadding: Dp,
) {
    val host = LocalUIViewController.current
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val toggleHandler by rememberUpdatedState(onToggleLayerId)
    val dropHandler by rememberUpdatedState(onDropBeacon)
    val zoomInHandler by rememberUpdatedState(onZoomIn)
    val zoomOutHandler by rememberUpdatedState(onZoomOut)

    DisposableEffect(host) {
        IosHostMapFloatingChrome.attach(host, usesNativeLiquidGlass)
        onDispose { IosHostMapFloatingChrome.detach() }
    }

    LaunchedEffect(visible, layerLabel, layerOptions, bottomPadding, usesNativeLiquidGlass) {
        IosHostMapFloatingChrome.update(
            visible = visible,
            layerLabel = layerLabel,
            layerOptions = layerOptions,
            bottomPaddingPoints = bottomPadding.value.toDouble(),
            usesNativeLiquidGlass = usesNativeLiquidGlass,
            onToggleLayerId = { id -> toggleHandler(id) },
            onDropBeacon = { dropHandler() },
            onZoomIn = { zoomInHandler() },
            onZoomOut = { zoomOutHandler() },
        )
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private object IosHostMapFloatingChrome {
    private val layerButton = makeIconButton()
    private val dropButton = makeIconButton()
    private val zoomInButton = makeIconButton()
    private val zoomOutButton = makeIconButton()
    private val dropTarget = MapChromeTapTarget()
    private val zoomInTarget = MapChromeTapTarget()
    private val zoomOutTarget = MapChromeTapTarget()
    private var attachedHost: UIViewController? = null
    private var bottomConstraint: NSLayoutConstraint? = null
    private var glassApplied = false

    fun attach(
        host: UIViewController,
        usesNativeLiquidGlass: Boolean,
    ) {
        if (attachedHost === host && layerButton.superview == host.view) {
            applyGlass(usesNativeLiquidGlass)
            return
        }
        detach()
        attachedHost = host
        val hostView = host.view
        applyGlass(usesNativeLiquidGlass)
        listOf(layerButton, dropButton, zoomInButton, zoomOutButton).forEach { button ->
            button.translatesAutoresizingMaskIntoConstraints = false
            hostView.addSubview(button)
        }
        dropButton.addTarget(
            dropTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        zoomInButton.addTarget(
            zoomInTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        zoomOutButton.addTarget(
            zoomOutTarget,
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        val bottom =
            dropButton.bottomAnchor.constraintEqualToAnchor(
                hostView.safeAreaLayoutGuide.bottomAnchor,
                constant = -88.0,
            )
        bottomConstraint = bottom
        NSLayoutConstraint.activateConstraints(
            listOf(
                layerButton.topAnchor.constraintEqualToAnchor(hostView.safeAreaLayoutGuide.topAnchor, constant = 8.0),
                layerButton.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor, constant = -16.0),
                layerButton.heightAnchor.constraintEqualToConstant(40.0),
                layerButton.widthAnchor.constraintGreaterThanOrEqualToConstant(72.0),
                dropButton.leadingAnchor.constraintEqualToAnchor(hostView.leadingAnchor, constant = 16.0),
                dropButton.widthAnchor.constraintEqualToConstant(56.0),
                dropButton.heightAnchor.constraintEqualToConstant(56.0),
                bottom,
                zoomOutButton.trailingAnchor.constraintEqualToAnchor(hostView.trailingAnchor, constant = -16.0),
                zoomOutButton.bottomAnchor.constraintEqualToAnchor(dropButton.bottomAnchor),
                zoomOutButton.widthAnchor.constraintEqualToConstant(48.0),
                zoomOutButton.heightAnchor.constraintEqualToConstant(48.0),
                zoomInButton.trailingAnchor.constraintEqualToAnchor(zoomOutButton.trailingAnchor),
                zoomInButton.bottomAnchor.constraintEqualToAnchor(zoomOutButton.topAnchor, constant = -8.0),
                zoomInButton.widthAnchor.constraintEqualToConstant(48.0),
                zoomInButton.heightAnchor.constraintEqualToConstant(48.0),
            ),
        )
        dropButton.setImage(UIImage.systemImageNamed("mappin.and.ellipse"), forState = UIControlStateNormal)
        zoomInButton.setImage(UIImage.systemImageNamed("plus"), forState = UIControlStateNormal)
        zoomOutButton.setImage(UIImage.systemImageNamed("minus"), forState = UIControlStateNormal)
        dropButton.setAccessibilityLabel("Drop beacon")
        zoomInButton.setAccessibilityLabel("Zoom in")
        zoomOutButton.setAccessibilityLabel("Zoom out")
        layerButton.showsMenuAsPrimaryAction = true
    }

    fun update(
        visible: Boolean,
        layerLabel: String,
        layerOptions: List<NativeMapLayerOption>,
        bottomPaddingPoints: Double,
        usesNativeLiquidGlass: Boolean,
        onToggleLayerId: (String) -> Unit,
        onDropBeacon: () -> Unit,
        onZoomIn: () -> Unit,
        onZoomOut: () -> Unit,
    ) {
        applyGlass(usesNativeLiquidGlass)
        dropTarget.handler = onDropBeacon
        zoomInTarget.handler = onZoomIn
        zoomOutTarget.handler = onZoomOut
        layerButton.setTitle(layerLabel, forState = UIControlStateNormal)
        layerButton.menu =
            UIMenu.menuWithTitle(
                title = "",
                children =
                    layerOptions.map { option ->
                        UIAction.actionWithTitle(
                            title = option.label,
                            image =
                                if (option.selected) {
                                    UIImage.systemImageNamed("checkmark")
                                } else {
                                    null
                                },
                            identifier = null,
                            handler = { onToggleLayerId(option.id) },
                        )
                    },
            )
        bottomConstraint?.constant = -bottomPaddingPoints
        val show = visible
        listOf(layerButton, dropButton, zoomInButton, zoomOutButton).forEach { button ->
            button.hidden = !show
            button.userInteractionEnabled = show
            if (show) {
                attachedHost?.view?.bringSubviewToFront(button)
            }
        }
    }

    fun detach() {
        listOf(layerButton, dropButton, zoomInButton, zoomOutButton).forEach { it.removeFromSuperview() }
        bottomConstraint = null
        attachedHost = null
        glassApplied = false
    }

    private fun applyGlass(usesNativeLiquidGlass: Boolean) {
        if (glassApplied) return
        glassApplied = true
        if (!usesNativeLiquidGlass) return
        listOf(layerButton, dropButton, zoomInButton, zoomOutButton).forEach { button ->
            button.configuration = UIButtonConfiguration.glassButtonConfiguration()
        }
    }

    private fun makeIconButton(): UIButton =
        UIButton.buttonWithType(UIButtonTypeSystem).apply {
            translatesAutoresizingMaskIntoConstraints = false
        }
}

@OptIn(BetaInteropApi::class)
private class MapChromeTapTarget : NSObject() {
    var handler: (() -> Unit)? = null

    @ObjCAction
    fun didTap() {
        handler?.invoke()
    }
}
