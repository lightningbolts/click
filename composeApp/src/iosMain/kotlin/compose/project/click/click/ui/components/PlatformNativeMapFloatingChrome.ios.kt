@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
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
        onDispose {
            IosHostMapFloatingChrome.update(
                visible = false,
                layerLabel = "",
                layerOptions = emptyList(),
                bottomPaddingPoints = 0.0,
                usesNativeLiquidGlass = usesNativeLiquidGlass,
                onToggleLayerId = {},
                onDropBeacon = {},
                onZoomIn = {},
                onZoomOut = {},
            )
            IosHostMapFloatingChrome.detach()
        }
    }

    SideEffect {
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
internal object IosHostMapFloatingChrome {
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
    private var usesGlassButtons = false
    private var lastMenuSignature: String? = null
    private val buttonMasks = mutableMapOf<UIButton, CALayer>()
    private var leadingRevealWidthPt = -1.0

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
        val menuSignature =
            buildString {
                append(layerLabel)
                layerOptions.forEach { option ->
                    append('|')
                    append(option.id)
                    append(':')
                    append(option.selected)
                }
            }
        if (menuSignature != lastMenuSignature) {
            lastMenuSignature = menuSignature
            paintLayerButton(layerLabel)
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
        }
        bottomConstraint?.constant = -bottomPaddingPoints
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        chromeButtons().forEach { button ->
            button.hidden = !visible
            button.alpha = 1.0
        }
        CATransaction.commit()
        applyLeadingRevealMask()
    }

    /**
     * Clip floating map controls to the uncovered leading strip while Nearby (or another
     * overlay) covers the map. Do not toggle [hidden] for cover — that rematerializes glass.
     */
    fun clipLeadingUnderlay(leadingWidthPt: Double?) {
        leadingRevealWidthPt = leadingWidthPt ?: -1.0
        applyLeadingRevealMask()
    }

    fun leadingClipUncoverPt(): Double = leadingRevealWidthPt

    private fun applyLeadingRevealMask() {
        val hostView = attachedHost?.view ?: return
        val uncover = leadingRevealWidthPt
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        chromeButtons().forEach { button ->
            if (button.superview == null) {
                return@forEach
            }
            if (uncover < 0.0) {
                button.layer.mask = null
                button.userInteractionEnabled = !button.hidden
                return@forEach
            }
            val minX =
                button.convertPoint(CGPointMake(0.0, 0.0), toView = hostView).useContents { x }
            val clipW = NativeHeaderMetrics.hostLeadingClipWidthPt(uncover, minX)
            val height = button.bounds.useContents { size.height }.coerceAtLeast(1.0)
            val mask =
                buttonMasks.getOrPut(button) {
                    CALayer().apply { backgroundColor = UIColor.blackColor.CGColor }
                }
            mask.frame = CGRectMake(0.0, 0.0, clipW, height)
            button.layer.mask = mask
            button.userInteractionEnabled = !button.hidden && clipW > 8.0
        }
        CATransaction.commit()
    }

    private fun chromeButtons(): List<UIButton> = listOf(layerButton, dropButton, zoomInButton, zoomOutButton)

    fun detach() {
        chromeButtons().forEach { button ->
            button.layer.mask = null
            button.removeFromSuperview()
        }
        buttonMasks.clear()
        leadingRevealWidthPt = -1.0
        bottomConstraint = null
        lastMenuSignature = null
        attachedHost = null
        glassApplied = false
    }

    private fun applyGlass(usesNativeLiquidGlass: Boolean) {
        usesGlassButtons = usesNativeLiquidGlass
        if (glassApplied) return
        glassApplied = true
        if (!usesNativeLiquidGlass) {
            dropButton.setImage(UIImage.systemImageNamed("mappin.and.ellipse"), forState = UIControlStateNormal)
            zoomInButton.setImage(UIImage.systemImageNamed("plus"), forState = UIControlStateNormal)
            zoomOutButton.setImage(UIImage.systemImageNamed("minus"), forState = UIControlStateNormal)
            return
        }
        paintIconButton(dropButton, "mappin.and.ellipse")
        paintIconButton(zoomInButton, "plus")
        paintIconButton(zoomOutButton, "minus")
        paintLayerButton(layerButton.titleForState(UIControlStateNormal) ?: "All")
    }

    private fun paintIconButton(
        button: UIButton,
        symbol: String,
    ) {
        val config = UIButtonConfiguration.glassButtonConfiguration()
        config.image = UIImage.systemImageNamed(symbol)
        button.configuration = config
    }

    private fun paintLayerButton(label: String) {
        if (usesGlassButtons) {
            val config =
                layerButton.configuration
                    ?: UIButtonConfiguration.glassButtonConfiguration()
            config.title = label
            layerButton.configuration = config
        }
        layerButton.setTitle(label, forState = UIControlStateNormal)
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
