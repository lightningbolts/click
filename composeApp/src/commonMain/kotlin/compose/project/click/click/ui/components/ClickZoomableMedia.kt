@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Full-bleed photo host that consumes taps (so they do not dismiss a parent scrim)
 * and supports pinch-to-zoom / pan / double-tap zoom.
 */
@Composable
fun ClickZoomableMedia(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(minScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(minScale, maxScale) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = next
                        offset = if (next <= minScale + 0.01f) Offset.Zero else offset + pan
                    }
                }.pointerInput(minScale, maxScale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > minScale + 0.01f) {
                                scale = minScale
                                offset = Offset.Zero
                            } else {
                                scale = (minScale * 2.5f).coerceAtMost(maxScale)
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        content(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
