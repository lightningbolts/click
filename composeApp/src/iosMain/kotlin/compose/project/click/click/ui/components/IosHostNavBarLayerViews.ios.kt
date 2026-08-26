@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequest
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CALayer
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIMenu
import platform.UIKit.UIView
import platform.UIKit.setAccessibilityLabel
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.bindIdentity(
    hasBack: Boolean,
    identity: NativeChromeIdentity?,
    presenceOnline: Boolean?,
) {
    val showAvatar = identity != null
    avatarButton.hidden = !showAvatar
    presenceDot.hidden = !showAvatar || presenceOnline != true
    titleLeadingToAvatar?.active = showAvatar
    titleLeadingToBack?.active = hasBack && !showAvatar
    titleLeadingToBar?.active = !hasBack && !showAvatar
    avatarLeadingToBack?.active = hasBack && showAvatar
    avatarLeadingToBar?.active = !hasBack && showAvatar
    avatarTarget.handler = identity?.onClick
    if (identity == null) {
        lastAvatarUrl = null
        lastAvatarUserId = null
        avatarPhoto.image = null
        avatarPhoto.hidden = true
        avatarInitialsLabel.text = null
        avatarInitialsLabel.hidden = true
        return
    }
    val spec =
        avatarFaceSpec(
            displayName = identity.displayName,
            email = identity.email,
            avatarUrl = identity.avatarUrl,
            userId = identity.userId,
        )
    applyIdentityFace(identity.userId, spec)
    avatarButton.setAccessibilityLabel(identity.displayName ?: "Profile")
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyIdentityFace(
    userId: String,
    spec: AvatarFaceSpec,
) {
    val cachedPhoto = spec.photoUrl?.let { cachedAvatarPhoto(it) }
    if (cachedPhoto != null) {
        lastAvatarUserId = userId
        lastAvatarUrl = spec.photoUrl
        showIdentityPhoto(cachedPhoto)
        return
    }
    val sameFace = userId == lastAvatarUserId && lastAvatarUrl == spec.photoUrl
    if (sameFace && (spec.photoUrl == null || avatarPhoto.image != null)) {
        if (spec.photoUrl == null) {
            showIdentityInitials(spec)
        }
        return
    }
    val previousUser = lastAvatarUserId
    lastAvatarUserId = userId
    if (previousUser != userId) {
        avatarPhoto.image = null
        avatarPhoto.hidden = true
    }
    showIdentityInitials(spec)
    loadIdentityPhoto(spec.photoUrl)
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.showIdentityInitials(spec: AvatarFaceSpec) {
    val fill =
        UIColor.colorWithRed(
            spec.background.red.toDouble(),
            green = spec.background.green.toDouble(),
            blue = spec.background.blue.toDouble(),
            alpha = spec.background.alpha.toDouble(),
        )
    avatarButton.configuration = null
    avatarButton.clipsToBounds = true
    avatarButton.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
    avatarButton.backgroundColor = fill
    avatarButton.setTitle("", forState = UIControlStateNormal)
    avatarButton.setImage(null, forState = UIControlStateNormal)
    avatarInitialsLabel.text = spec.initials
    avatarInitialsLabel.hidden = false
    if (spec.photoUrl == null || avatarPhoto.image == null) {
        avatarPhoto.hidden = true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.showIdentityPhoto(image: UIImage) {
    avatarButton.configuration = null
    avatarButton.clipsToBounds = true
    avatarButton.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
    avatarButton.setTitle("", forState = UIControlStateNormal)
    avatarPhoto.image = image
    avatarPhoto.hidden = false
    avatarInitialsLabel.hidden = true
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.loadIdentityPhoto(url: String?) {
    val trimmed = url?.trim()?.takeIf { it.isNotEmpty() }
    if (trimmed == lastAvatarUrl && avatarPhoto.image != null) return
    lastAvatarUrl = trimmed
    if (trimmed == null) return
    val immediate = cachedAvatarPhoto(trimmed)
    if (immediate != null) {
        showIdentityPhoto(immediate)
        return
    }
    val nsUrl = NSURL.URLWithString(trimmed) ?: return
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
        val data = NSData.dataWithContentsOfURL(nsUrl)
        val image = data?.let { UIImage.imageWithData(it) }
        dispatch_async(dispatch_get_main_queue()) {
            if (lastAvatarUrl == trimmed && image != null) {
                IosNavChrome.avatarPhotos[trimmed] = image
                showIdentityPhoto(image)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.rebuildTrailing(
    trailingActions: List<NativeChromeAction>,
    showSearch: Boolean,
) {
    trailingStack.arrangedSubviews.map { it as UIView }.forEach { view ->
        trailingStack.removeArrangedSubview(view)
        view.removeFromSuperview()
    }
    trailingActions.forEachIndexed { index, action ->
        val button = actionButtons[index]
        button.hidden = false
        bindNativeMenu(button, action, actionIndex = index)
        paintChromeButton(button, action.sfSymbol, action.contentDescription, clustered = true)
        trailingStack.addArrangedSubview(button)
    }
    actionButtons.drop(trailingActions.size).forEach { button ->
        button.hidden = true
        button.menu = null
        button.showsMenuAsPrimaryAction = false
    }
    if (showSearch) {
        searchButton.hidden = false
        bindNativeMenu(searchButton, null, actionIndex = -1)
        paintChromeButton(searchButton, "magnifyingglass", "Search", clustered = true)
        trailingStack.addArrangedSubview(searchButton)
    } else {
        searchButton.hidden = true
        searchButton.menu = null
        searchButton.showsMenuAsPrimaryAction = false
    }
    val hasTrailing = trailingActions.isNotEmpty() || showSearch
    trailingCluster.hidden = !hasTrailing
    titleTrailingToCluster?.active = hasTrailing
    titleTrailingToBar?.active = !hasTrailing
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.bindNativeMenu(
    button: UIButton,
    action: NativeChromeAction?,
    actionIndex: Int,
) {
    val items = action?.menuItems.orEmpty()
    if (items.isEmpty()) {
        button.menu = null
        button.showsMenuAsPrimaryAction = false
        return
    }
    button.showsMenuAsPrimaryAction = true
    button.menu =
        UIMenu.menuWithTitle(
            title = "",
            children =
                items.mapIndexed { itemIndex, item ->
                    val key = "$actionIndex:$itemIndex"
                    UIAction.actionWithTitle(
                        title = item.title,
                        image = item.sfSymbol?.let { UIImage.systemImageNamed(it) },
                        identifier = null,
                        handler = { menuClicksByKey[key]?.invoke() },
                    )
                },
        )
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyTitleSlot(
    hasBack: Boolean,
    trailingCount: Int,
) {
    val barWidth = bar.bounds.useContents { size.width }
    val leading = NativeHeaderMetrics.titleLeadingInsetPt(hasBack)
    val trailing = NativeHeaderMetrics.titleTrailingInsetPt(trailingCount)
    titleLabel.preferredMaxLayoutWidth =
        NativeHeaderMetrics.titleMaxWidthPt(barWidth, leading, trailing)
    subtitleLabel.preferredMaxLayoutWidth = titleLabel.preferredMaxLayoutWidth
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.installRowIfNeeded() {
    if (rowInstalled) return
    titleColumn.addArrangedSubview(titleLabel)
    titleColumn.addArrangedSubview(subtitleLabel)
    chromeRow.addSubview(backButton)
    chromeRow.addSubview(avatarButton)
    avatarButton.addSubview(avatarInitialsLabel)
    avatarButton.addSubview(avatarPhoto)
    chromeRow.addSubview(presenceDot)
    chromeRow.addSubview(titleColumn)
    chromeRow.addSubview(trailingCluster)
    trailingCluster.contentView.addSubview(trailingStack)
    backButton.addTarget(
        backTarget,
        action = NSSelectorFromString("didTap"),
        forControlEvents = UIControlEventTouchUpInside,
    )
    avatarButton.addTarget(
        avatarTarget,
        action = NSSelectorFromString("didTap"),
        forControlEvents = UIControlEventTouchUpInside,
    )
    searchButton.addTarget(
        searchTarget,
        action = NSSelectorFromString("didTap"),
        forControlEvents = UIControlEventTouchUpInside,
    )
    actionButtons.forEachIndexed { index, button ->
        button.addTarget(
            actionTargets[index],
            action = NSSelectorFromString("didTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
    }
    val leadingBar =
        titleColumn.leadingAnchor.constraintEqualToAnchor(
            chromeRow.leadingAnchor,
            constant = NativeHeaderMetrics.LeadingInsetPt,
        )
    val leadingBack =
        titleColumn.leadingAnchor.constraintEqualToAnchor(
            backButton.trailingAnchor,
            constant = NativeHeaderMetrics.TitleGutterPt,
        )
    leadingBack.active = false
    titleLeadingToBar = leadingBar
    titleLeadingToBack = leadingBack
    val avatarSize = NativeHeaderMetrics.ChromeButtonSizePt
    val leadingAvatar =
        titleColumn.leadingAnchor.constraintEqualToAnchor(
            avatarButton.trailingAnchor,
            constant = NativeHeaderMetrics.TitleGutterPt,
        )
    leadingAvatar.active = false
    titleLeadingToAvatar = leadingAvatar
    val avatarFromBack =
        avatarButton.leadingAnchor.constraintEqualToAnchor(
            backButton.trailingAnchor,
            constant = NativeHeaderMetrics.TitleGutterPt,
        )
    avatarFromBack.active = false
    avatarLeadingToBack = avatarFromBack
    val avatarFromBar =
        avatarButton.leadingAnchor.constraintEqualToAnchor(
            chromeRow.leadingAnchor,
            constant = NativeHeaderMetrics.LeadingInsetPt,
        )
    avatarFromBar.active = false
    avatarLeadingToBar = avatarFromBar
    val trailingClusterPin =
        titleColumn.trailingAnchor.constraintEqualToAnchor(
            trailingCluster.leadingAnchor,
            constant = -NativeHeaderMetrics.TitleGutterPt,
        )
    val trailingBarPin =
        titleColumn.trailingAnchor.constraintEqualToAnchor(
            chromeRow.trailingAnchor,
            constant = -NativeHeaderMetrics.TrailingInsetPt,
        )
    trailingBarPin.active = false
    titleTrailingToCluster = trailingClusterPin
    titleTrailingToBar = trailingBarPin
    val inset = NativeHeaderMetrics.ClusterContentInsetPt
    val chromePlane = NativeHeaderMetrics.CompactChromeCenterYPt
    val titleTop =
        titleColumn.topAnchor.constraintEqualToAnchor(
            chromeRow.topAnchor,
            constant = NativeHeaderMetrics.titleColumnTopInsetPt(0f),
        )
    titleColumnTopConstraint = titleTop
    val titleCenter =
        titleColumn.centerYAnchor.constraintEqualToAnchor(
            chromeRow.topAnchor,
            constant = NativeHeaderMetrics.CompactChromeCenterYPt,
        )
    titleCenter.active = false
    titleColumnCenterYConstraint = titleCenter
    titleColumn.setContentHuggingPriority(1000f, forAxis = UILayoutConstraintAxisVertical)
    titleColumn.setContentCompressionResistancePriority(1000f, forAxis = UILayoutConstraintAxisVertical)
    NSLayoutConstraint.activateConstraints(
        listOf(
            chromeRow.topAnchor.constraintEqualToAnchor(bar.topAnchor),
            chromeRow.leadingAnchor.constraintEqualToAnchor(bar.leadingAnchor),
            chromeRow.trailingAnchor.constraintEqualToAnchor(bar.trailingAnchor),
            chromeRow.bottomAnchor.constraintEqualToAnchor(bar.bottomAnchor),
            backButton.leadingAnchor.constraintEqualToAnchor(
                chromeRow.leadingAnchor,
                constant = NativeHeaderMetrics.LeadingInsetPt - 8.0,
            ),
            backButton.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
            avatarButton.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
            avatarInitialsLabel.leadingAnchor.constraintEqualToAnchor(avatarButton.leadingAnchor, constant = 3.0),
            avatarInitialsLabel.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor, constant = -3.0),
            avatarInitialsLabel.centerYAnchor.constraintEqualToAnchor(avatarButton.centerYAnchor),
            avatarPhoto.topAnchor.constraintEqualToAnchor(avatarButton.topAnchor),
            avatarPhoto.leadingAnchor.constraintEqualToAnchor(avatarButton.leadingAnchor),
            avatarPhoto.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor),
            avatarPhoto.bottomAnchor.constraintEqualToAnchor(avatarButton.bottomAnchor),
            presenceDot.widthAnchor.constraintEqualToConstant(8.0),
            presenceDot.heightAnchor.constraintEqualToConstant(8.0),
            presenceDot.trailingAnchor.constraintEqualToAnchor(avatarButton.trailingAnchor, constant = 1.0),
            presenceDot.bottomAnchor.constraintEqualToAnchor(avatarButton.bottomAnchor, constant = 1.0),
            trailingCluster.trailingAnchor.constraintEqualToAnchor(
                chromeRow.trailingAnchor,
                constant = -NativeHeaderMetrics.TrailingInsetPt,
            ),
            trailingCluster.centerYAnchor.constraintEqualToAnchor(chromeRow.topAnchor, constant = chromePlane),
            trailingCluster.heightAnchor.constraintEqualToConstant(NativeHeaderMetrics.ChromeButtonSizePt),
            trailingStack.leadingAnchor.constraintEqualToAnchor(trailingCluster.contentView.leadingAnchor, constant = inset),
            trailingStack.trailingAnchor.constraintEqualToAnchor(trailingCluster.contentView.trailingAnchor, constant = -inset),
            trailingStack.topAnchor.constraintEqualToAnchor(trailingCluster.contentView.topAnchor, constant = inset),
            trailingStack.bottomAnchor.constraintEqualToAnchor(trailingCluster.contentView.bottomAnchor, constant = -inset),
            leadingBar,
            trailingClusterPin,
            titleTop,
            titleColumn.bottomAnchor.constraintLessThanOrEqualToAnchor(
                chromeRow.bottomAnchor,
                constant = -NativeHeaderMetrics.CompactRowBottomPaddingPt,
            ),
        ),
    )
    avatarButton.layer.cornerRadius = avatarSize / 2.0
    avatarPhoto.layer.cornerRadius = avatarSize / 2.0
    presenceDot.layer.cornerRadius = 4.0
    presenceDot.layer.borderWidth = 1.5
    presenceDot.layer.borderColor = UIColor.whiteColor.CGColor
    rowInstalled = true
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.paintChromeButton(
    button: UIButton,
    symbol: String,
    accessibility: String,
    clustered: Boolean,
) {
    val symbolConfig =
        UIImageSymbolConfiguration.configurationWithPointSize(
            NativeHeaderMetrics.ChromeIconPointSize,
            weight = UIImageSymbolWeightMedium,
        )
    val image =
        UIImage.systemImageNamed(symbol, withConfiguration = symbolConfig)
            ?: UIImage.systemImageNamed(symbol)
    val config =
        if (usesGlassButtons && clustered) {
            UIButtonConfiguration.plainButtonConfiguration().apply {
                baseForegroundColor = if (lastIsDark) UIColor.whiteColor else UIColor.blackColor
            }
        } else if (usesGlassButtons) {
            UIButtonConfiguration.glassButtonConfiguration().apply {
                cornerStyle = UIButtonConfigurationCornerStyleCapsule
            }
        } else {
            UIButtonConfiguration.plainButtonConfiguration()
        }
    config.image = image
    config.preferredSymbolConfigurationForImage = symbolConfig
    config.contentInsets = NSDirectionalEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
    button.configuration = config
    button.setImage(image, forState = UIControlStateNormal)
    button.tintColor = if (lastIsDark) UIColor.whiteColor else UIColor.blackColor
    button.setAccessibilityLabel(accessibility)
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyClusterChrome() {
    val key =
        if (usesGlassButtons) {
            "glass-interactive-capsule"
        } else if (lastIsDark) {
            "blur-dark"
        } else {
            "blur-light"
        }
    if (key == lastClusterChromeKey) return
    lastClusterChromeKey = key
    trailingCluster.clipsToBounds = false
    trailingCluster.layer.masksToBounds = false
    trailingCluster.layer.shadowOpacity = 0f
    trailingCluster.layer.borderWidth = 0.0
    trailingCluster.backgroundColor = UIColor.clearColor
    if (usesGlassButtons) {
        val glass = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
        glass.setInteractive(true)
        trailingCluster.effect = glass
        trailingCluster.cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
    } else {
        trailingCluster.layer.cornerRadius = NativeHeaderMetrics.ChromeButtonSizePt / 2.0
        val style =
            if (lastIsDark) {
                UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
            } else {
                UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
            }
        trailingCluster.effect = UIBlurEffect.effectWithStyle(style)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.applyLeadingRevealMask(widthPt: Double?) {
    if (widthPt == null) {
        leadingRevealWidthPt = -1.0
        bar.layer.mask = null
        chromeRow.layer.mask = null
        if (!glassPlate.hidden) {
            updateGlassFadeMask()
        }
        return
    }
    leadingRevealWidthPt = widthPt.coerceAtLeast(0.0)
    barLeadingMask = leadingRectMask(bar, barLeadingMask, leadingRevealWidthPt)
    chromeLeadingMask = leadingRectMask(chromeRow, chromeLeadingMask, leadingRevealWidthPt)
    if (!glassPlate.hidden) {
        updateGlassFadeMask()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun leadingRectMask(
    view: UIView,
    existing: CALayer?,
    widthPt: Double,
): CALayer {
    val height = view.bounds.useContents { size.height }.coerceAtLeast(1.0)
    val mask =
        existing ?: CALayer().apply {
            backgroundColor = UIColor.blackColor.CGColor
        }
    mask.frame = CGRectMake(0.0, 0.0, widthPt, height)
    view.layer.mask = mask
    return mask
}

@OptIn(ExperimentalForeignApi::class)
internal fun IosHostNavBarLayer.updateGlassFadeMask() {
    val bounds = glassPlate.bounds
    val width = bounds.useContents { size.width }
    val height = bounds.useContents { size.height }
    if (width <= 1.0 || height <= 1.0) return
    val mask =
        glassFadeMask ?: CAGradientLayer().also { layer ->
            layer.startPoint = CGPointMake(0.5, 0.0)
            layer.endPoint = CGPointMake(0.5, 1.0)
            glassFadeMask = layer
        }
    val clipping = leadingRevealWidthPt >= 0.0
    val clipWidth =
        if (clipping) {
            leadingRevealWidthPt.coerceAtMost(width)
        } else {
            width
        }
    mask.frame = CGRectMake(0.0, 0.0, clipWidth, height)
    val opaqueUntil = ((height - NativeHeaderMetrics.GlassFadeExtensionPt) / height).coerceIn(0.55, 0.92)
    mask.colors =
        listOf(
            UIColor.blackColor.CGColor,
            UIColor.blackColor.CGColor,
            UIColor.clearColor.CGColor,
        )
    mask.locations = listOf(0.0, opaqueUntil, 1.0)
    if (clipping) {
        val host =
            glassClipHost ?: CALayer().also { layer ->
                glassClipHost = layer
            }
        host.frame = CGRectMake(0.0, 0.0, width, height)
        if (mask.superlayer != host) {
            mask.removeFromSuperlayer()
            host.addSublayer(mask)
        }
        glassPlate.layer.mask = host
    } else {
        if (mask.superlayer != null && mask.superlayer !== glassPlate.layer) {
            mask.removeFromSuperlayer()
        }
        glassPlate.layer.mask = mask
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cachedAvatarPhoto(url: String): UIImage? {
    IosNavChrome.avatarPhotos[url]?.let { return it }
    val nsUrl = NSURL.URLWithString(url) ?: return null
    val cached =
        NSURLCache.sharedURLCache.cachedResponseForRequest(
            NSURLRequest.requestWithURL(nsUrl),
        )
    val fromUrlCache = cached?.data?.let { UIImage.imageWithData(it) }
    val image = fromUrlCache ?: coilCachedAvatarPhoto(url)
    if (image != null) {
        IosNavChrome.avatarPhotos[url] = image
    }
    return image
}

@OptIn(ExperimentalForeignApi::class)
private fun coilCachedAvatarPhoto(url: String): UIImage? {
    val loader = coil3.SingletonImageLoader.get(coil3.PlatformContext.INSTANCE)
    val snapshot = loader.diskCache?.openSnapshot(url) ?: return null
    return try {
        val bytes = loader.diskCache?.fileSystem?.read(snapshot.data) { readByteArray() } ?: return null
        if (bytes.isEmpty()) return null
        kotlinx.cinterop.memScoped {
            val data =
                bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
            UIImage.imageWithData(data)
        }
    } finally {
        snapshot.close()
    }
}
