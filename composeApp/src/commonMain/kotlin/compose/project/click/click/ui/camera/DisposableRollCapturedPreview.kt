package compose.project.click.click.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import compose.project.click.click.utils.toImageBitmap
import kotlinx.coroutines.launch

@Composable
internal fun DisposableRollCapturedPreview(
    sourceBytes: ByteArray,
    filterIndex: Int,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewCache = remember(sourceBytes) { mutableStateMapOf<Int, ImageBitmap>() }
    var previewBitmap by remember(sourceBytes) { mutableStateOf<ImageBitmap?>(null) }
    val renderScope = rememberCoroutineScope()

    LaunchedEffect(sourceBytes) {
        previewCache.clear()
        val natural = renderDisposableRollFilteredPreview(sourceBytes, 0)
            ?: runCatching { sourceBytes.toImageBitmap() }.getOrNull()
        if (natural != null) {
            previewCache[0] = natural
            previewBitmap = natural
        }
        for (index in 1 until DisposableRollFilters.COUNT) {
            renderDisposableRollFilteredPreview(sourceBytes, index)?.let { bitmap ->
                previewCache[index] = bitmap
                if (index == filterIndex) {
                    previewBitmap = bitmap
                }
            }
        }
    }

    LaunchedEffect(filterIndex) {
        previewCache[filterIndex]?.let { cached ->
            previewBitmap = cached
            return@LaunchedEffect
        }
        val target = filterIndex
        renderScope.launch {
            val bitmap = renderDisposableRollFilteredPreview(sourceBytes, target)
                ?: runCatching { sourceBytes.toImageBitmap() }.getOrNull()
            if (bitmap != null) {
                previewCache[target] = bitmap
                if (target == filterIndex) {
                    previewBitmap = bitmap
                }
            }
        }
    }

    Box(modifier = modifier) {
        previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (mirrorHorizontally) {
                            Modifier.graphicsLayer { scaleX = -1f }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
