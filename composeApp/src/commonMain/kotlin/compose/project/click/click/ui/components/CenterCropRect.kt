package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * Source rectangle that center-crops [srcWidth]×[srcHeight] into a square of [dstSize]
 * (same intent as Compose `ContentScale.Crop`).
 */
data class CenterCropRect(
    val srcX: Float,
    val srcY: Float,
    val srcW: Float,
    val srcH: Float,
)

fun centerCropSourceRect(
    srcWidth: Float,
    srcHeight: Float,
    dstSize: Float,
): CenterCropRect {
    val srcW = srcWidth.coerceAtLeast(1f)
    val srcH = srcHeight.coerceAtLeast(1f)
    val dst = dstSize.coerceAtLeast(1f)
    val scale = maxOf(dst / srcW, dst / srcH)
    val sampledW = dst / scale
    val sampledH = dst / scale
    val srcX = ((srcW - sampledW) / 2f).coerceAtLeast(0f)
    val srcY = ((srcH - sampledH) / 2f).coerceAtLeast(0f)
    return CenterCropRect(srcX, srcY, sampledW.coerceAtMost(srcW), sampledH.coerceAtMost(srcH))
}
