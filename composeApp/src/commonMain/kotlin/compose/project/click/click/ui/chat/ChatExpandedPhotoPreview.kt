@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.isDisposableRollLocked // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickZoomableMedia // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassFullscreenMediaOverlay // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupTokens // pragma: allowlist secret
import compose.project.click.click.utils.toChatDisplayImageBitmap // pragma: allowlist secret
import compose.project.click.click.viewmodel.SecureChatMediaHost // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Full-screen tap-to-expand lightbox for chat photo messages.
 * Pinch/pan/double-tap zoom; close via the top control or system back — not by tapping the photo.
 */
@Composable
fun ChatExpandedPhotoPreview(
    target: MessageWithUser?,
    secureMediaHost: SecureChatMediaHost,
    onDismiss: () -> Unit,
) {
    var model by remember { mutableStateOf<MessageWithUser?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        when (val next = target) {
            null -> {
                if (model != null) {
                    visible = false
                    delay(UnifiedPopupTokens.ContentClearDelayMillis.toLong())
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
    val mediaFlow = secureMediaHost.secureChatMediaLoadState
    val secureState by produceState(
        initialValue = mediaFlow.value[message.id],
        message.id,
        mediaFlow,
    ) {
        mediaFlow.collect { map ->
            val next = map[message.id]
            if (next != value) value = next
        }
    }
    var bitmap by remember(message.id) {
        mutableStateOf(secureChatImageBitmapCache.get(message.id))
    }
    LaunchedEffect(message.id, secureState?.imageBytes) {
        secureChatImageBitmapCache.get(message.id)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val bytes = secureState?.imageBytes ?: return@LaunchedEffect
        val decoded =
            withContext(Dispatchers.Default) {
                runCatching { bytes.toChatDisplayImageBitmap() }.getOrNull()
            } ?: return@LaunchedEffect
        secureChatImageBitmapCache.put(message.id, decoded)
        bitmap = decoded
    }

    GlassFullscreenMediaOverlay(
        visible = visible,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        scrimAlpha = 1f,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            when {
                isEncrypted -> {
                    val bmp = bitmap
                    if (bmp != null) {
                        ClickZoomableMedia {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Photo",
                                contentScale = ContentScale.Fit,
                                modifier = it,
                            )
                        }
                    } else {
                        Text(
                            text = "Preparing photo…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                mediaUrl.isNotBlank() -> {
                    ClickZoomableMedia {
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Fit,
                            modifier = it,
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Preparing photo…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
