package compose.project.click.click.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.models.isDisposableRollLocked
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.ui.components.GlassFullscreenMediaOverlay
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.utils.toImageBitmap
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlinx.coroutines.delay

/**
 * Tap-to-expand lightbox for chat photo messages (matches profile media preview UX).
 */
@Composable
fun ChatExpandedPhotoPreview(
    target: MessageWithUser?,
    secureMediaLoadMap: Map<String, SecureChatMediaLoadState>,
    onDismiss: () -> Unit,
) {
    var model by remember { mutableStateOf<MessageWithUser?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        when (val next = target) {
            null -> {
                if (model != null) {
                    visible = false
                    delay(320)
                    if (target == null) {
                        model = null
                    }
                }
            }
            else -> {
                model = next
                delay(16)
                visible = true
            }
        }
    }

    val messageWithUser = model ?: return
    val message = messageWithUser.message
    if (message.isDisposableRollLocked()) return

    val mediaUrl = message.mediaUrlOrNull().orEmpty()
    val isEncrypted = message.isEncryptedMedia()
    val secureState = secureMediaLoadMap[message.id]
    val bitmap = secureState?.imageBytes?.let { bytes ->
        secureChatImageBitmapCache.get(message.id)
            ?: runCatching { bytes.toImageBitmap() }.getOrNull()?.also {
                secureChatImageBitmapCache.put(message.id, it)
            }
    }
    val previewImageFade = remember(message.id) { Animatable(0f) }

    LaunchedEffect(visible, message.id, bitmap, mediaUrl, isEncrypted) {
        if (!visible) {
            previewImageFade.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        previewImageFade.snapTo(0f)
        if ((isEncrypted && bitmap != null) || (!isEncrypted && mediaUrl.isNotBlank())) {
            previewImageFade.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    GlassFullscreenMediaOverlay(
        visible = visible,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
    ) {
        val shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(shape)
                .border(1.dp, GlassSheetTokens.GlassBorder, shape),
            shape = shape,
            color = GlassSheetTokens.OledBlack,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    isEncrypted && bitmap != null -> {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .graphicsLayer { alpha = previewImageFade.value }
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    !isEncrypted && mediaUrl.isNotBlank() -> {
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .graphicsLayer { alpha = previewImageFade.value }
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    else -> {
                        Text(
                            text = "Preparing photo…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassSheetTokens.OnOledMuted,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close", color = GlassSheetTokens.OnOled)
                }
            }
        }
    }
    }
}
