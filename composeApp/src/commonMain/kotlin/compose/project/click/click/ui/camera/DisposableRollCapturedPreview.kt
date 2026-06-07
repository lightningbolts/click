package compose.project.click.click.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import compose.project.click.click.utils.toImageBitmap

@Composable
internal fun DisposableRollCapturedPreview(
    sourceBytes: ByteArray,
    filterIndex: Int,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier,
) {
    var previewBitmap by remember(sourceBytes) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(sourceBytes, filterIndex) {
        previewBitmap = renderDisposableRollFilteredPreview(sourceBytes, filterIndex)
            ?: runCatching { sourceBytes.toImageBitmap() }.getOrNull()
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
