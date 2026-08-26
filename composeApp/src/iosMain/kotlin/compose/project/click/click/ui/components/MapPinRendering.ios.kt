@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.ui.theme.BeaconPinShape // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.drawInRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun argbToUIColor(argb: Int): UIColor {
    val a = ((argb ushr 24) and 0xFF) / 255.0
    val r = ((argb ushr 16) and 0xFF) / 255.0
    val g = ((argb ushr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    return UIColor(red = r, green = g, blue = b, alpha = a)
}

@OptIn(ExperimentalForeignApi::class)
internal fun MapPin.markerTintUIColor(): UIColor {
    avatarFillArgb?.let { return argbToUIColor(it) }
    val key = beaconTypeKey?.lowercase()
    return when {
        key != null ->
            when (key) {
                "soundtrack" -> UIColor(red = 0.58, green = 0.29, blue = 0.98, alpha = 1.0)
                "sos" -> UIColor.redColor
                "study" -> UIColor(red = 0.20, green = 0.45, blue = 0.95, alpha = 1.0)
                "hazard" -> UIColor.orangeColor
                "utility" -> UIColor(red = 0.20, green = 0.55, blue = 1.0, alpha = 1.0)
                "hazard_utility" -> UIColor(red = 0.98, green = 0.45, blue = 0.12, alpha = 1.0)
                "transit" -> UIColor(red = 0.0, green = 0.72, blue = 0.83, alpha = 1.0)
                "recreation" -> UIColor(red = 0.18, green = 0.75, blue = 0.38, alpha = 1.0)
                "hobby" -> UIColor(red = 0.35, green = 0.68, blue = 0.40, alpha = 1.0)
                "swag" -> UIColor(red = 0.72, green = 0.33, blue = 0.82, alpha = 1.0)
                "capacity" -> UIColor(red = 0.94, green = 0.32, blue = 0.62, alpha = 1.0)
                "scavenger" -> UIColor(red = 0.96, green = 0.76, blue = 0.22, alpha = 1.0)
                else -> UIColor(red = 0.85, green = 0.75, blue = 0.18, alpha = 1.0)
            }
        kind == MapPinKind.CONNECTION -> UIColor.magentaColor
        beaconKind != null ->
            when (beaconKind!!) {
                MapBeaconKind.SOUNDTRACK -> UIColor(red = 0.58, green = 0.29, blue = 0.98, alpha = 1.0)
                MapBeaconKind.SOS -> UIColor.redColor
                MapBeaconKind.HAZARD -> UIColor.orangeColor
                MapBeaconKind.UTILITY -> UIColor(red = 0.20, green = 0.55, blue = 1.0, alpha = 1.0)
                MapBeaconKind.STUDY -> UIColor.blueColor
                MapBeaconKind.SOCIAL_VIBE -> UIColor.magentaColor
                MapBeaconKind.EVENT -> UIColor(red = 0.20, green = 0.78, blue = 0.55, alpha = 1.0)
                MapBeaconKind.OTHER -> UIColor.yellowColor
            }
        else ->
            when (kind) {
                MapPinKind.BEACON_SOUNDTRACK -> UIColor(red = 0.58, green = 0.29, blue = 0.98, alpha = 1.0)
                MapPinKind.BEACON_ALERT -> UIColor.redColor
                MapPinKind.BEACON_SOCIAL -> UIColor.magentaColor
                MapPinKind.BEACON_OTHER -> UIColor.yellowColor
                MapPinKind.COMMUNITY_HUB -> UIColor(red = 0.18, green = 0.0, blue = 0.45, alpha = 1.0)
                MapPinKind.CONNECTION -> UIColor.magentaColor
            }
    }
}

/** Circular avatar / cluster pin (~44pt) — matches Android bitmap markers (not teardrop MKMarker glyphs). */
@OptIn(ExperimentalForeignApi::class)
internal fun circularMapPinUIImage(
    sizePts: Double,
    fill: UIColor,
    initials: String,
    photo: UIImage?,
): UIImage =
    shapedMapPinUIImage(
        sizePts = sizePts,
        shape = BeaconPinShape.CIRCLE,
        fill = fill,
        fillSecondary = null,
        initials = initials,
        photo = photo,
    )

@OptIn(ExperimentalForeignApi::class)
internal fun shapedMapPinUIImage(
    sizePts: Double,
    shape: BeaconPinShape,
    fill: UIColor,
    fillSecondary: UIColor?,
    initials: String,
    photo: UIImage?,
): UIImage {
    val size = sizePts.coerceAtLeast(36.0)
    val pad = size * 0.08
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 0.0)
    try {
        val path = beaconShapeBezierPath(shape, size, pad)
        val ctx = UIGraphicsGetCurrentContext()
        if (ctx != null) {
            CGContextSaveGState(ctx)
        }
        path.addClip()
        if (photo != null) {
            val pw = photo.size.useContents { width }.coerceAtLeast(1.0)
            val ph = photo.size.useContents { height }.coerceAtLeast(1.0)
            val crop =
                centerCropSourceRect(
                    srcWidth = pw.toFloat(),
                    srcHeight = ph.toFloat(),
                    dstSize = size.toFloat(),
                )
            val scale = size / crop.srcW.toDouble()
            val dw = pw * scale
            val dh = ph * scale
            val dx = -crop.srcX.toDouble() * scale
            val dy = -crop.srcY.toDouble() * scale
            photo.drawInRect(CGRectMake(dx, dy, dw, dh))
        } else {
            fill.setFill()
            path.fill()
            fillSecondary?.colorWithAlphaComponent(0.4)?.let { overlay ->
                overlay.setFill()
                val overlayRect = CGRectMake(size / 2.0, 0.0, size / 2.0, size)
                UIBezierPath.bezierPathWithRect(overlayRect).fill()
            }
            val glyph = initials.take(2).ifEmpty { "?" }
            val fontSize = size * 0.30
            val label = UILabel(frame = CGRectMake(0.0, 0.0, size, size))
            label.text = glyph
            label.textColor = UIColor.whiteColor
            label.font = UIFont.boldSystemFontOfSize(fontSize)
            label.textAlignment = NSTextAlignmentCenter
            label.backgroundColor = UIColor.clearColor
            label.drawTextInRect(CGRectMake(0.0, 0.0, size, size))
        }
        if (ctx != null) {
            CGContextRestoreGState(ctx)
        }
        UIColor.blackColor.setStroke()
        path.lineWidth = 2.0
        path.stroke()
        return UIGraphicsGetImageFromCurrentImageContext() ?: UIImage()
    } finally {
        UIGraphicsEndImageContext()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun beaconShapeBezierPath(
    shape: BeaconPinShape,
    size: Double,
    pad: Double,
): UIBezierPath {
    val rect = CGRectMake(pad, pad, size - pad * 2.0, size - pad * 2.0)
    val cx = size / 2.0
    val cy = size / 2.0
    return when (shape) {
        BeaconPinShape.CIRCLE -> UIBezierPath.bezierPathWithOvalInRect(rect)
        BeaconPinShape.ROUNDED_SQUARE, BeaconPinShape.SQUIRCLE ->
            UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = size * 0.22)
        BeaconPinShape.ROUNDED_RECT ->
            UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = size * 0.12)
        BeaconPinShape.TRIANGLE -> {
            val path = UIBezierPath()
            path.moveToPoint(CGPointMake(cx, pad))
            path.addLineToPoint(CGPointMake(size - pad, size - pad))
            path.addLineToPoint(CGPointMake(pad, size - pad))
            path.closePath()
            path
        }
        BeaconPinShape.DIAMOND -> {
            val path = UIBezierPath()
            path.moveToPoint(CGPointMake(cx, pad))
            path.addLineToPoint(CGPointMake(size - pad, cy))
            path.addLineToPoint(CGPointMake(cx, size - pad))
            path.addLineToPoint(CGPointMake(pad, cy))
            path.closePath()
            path
        }
        BeaconPinShape.HEXAGON, BeaconPinShape.PENTAGON -> {
            val sides = if (shape == BeaconPinShape.HEXAGON) 6 else 5
            val r = (size / 2.0) - pad
            val path = UIBezierPath()
            for (i in 0 until sides) {
                val angle = -PI / 2.0 + i * (2.0 * PI / sides)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)
                if (i == 0) path.moveToPoint(CGPointMake(x, y)) else path.addLineToPoint(CGPointMake(x, y))
            }
            path.closePath()
            path
        }
    }
}
