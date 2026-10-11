@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.project.click.click.collaboration.ClickDropDevelopController
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.clickDropRevealAt
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.platform.rememberReduceMotionEnabled
import compose.project.click.click.ui.drops.ClickDropDevelopEffect
import compose.project.click.click.ui.drops.ClickDropPixelatedImage
import compose.project.click.click.ui.drops.clickDropCountdownLabel
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.toChatDisplayImageBitmap
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * What a chat timeline gives its Click Drop bubbles (sheet 07 G0-5 / G2-2): the develop state
 * machine, developed originals, and the develop action. Absent on surfaces without the flow
 * (hub previews), where drops stay pixelated.
 */
@Stable
class ClickDropBubbleHost(
    val controller: ClickDropDevelopController,
    val originals: StateFlow<Map<String, ByteArray>>,
    val onDevelop: (Message) -> Unit,
    val requestOriginal: (Message) -> Unit,
)

val LocalClickDropBubbleHost = staticCompositionLocalOf<ClickDropBubbleHost?> { null }

private val dropShape = RoundedCornerShape(16.dp)

/**
 * A Click Drop photo in the develop flow:
 * - pending: 12-block pixels and "Develops in …"
 * - ready: the same pixels, quiet, "Tap to develop"
 * - developed: the photo (a gated drop's original, never its preview), blooming in once
 */
@Composable
internal fun ClickDropFlowPhoto(
    message: Message,
    host: ClickDropBubbleHost,
    secureState: SecureChatMediaLoadState?,
    borderIfReceived: Boolean,
    onExpand: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
) {
    val developedAt by host.controller.developedAt.collectAsState()
    val developing by host.controller.developing.collectAsState()
    val fresh by host.controller.freshlyDeveloped.collectAsState()
    val originals by host.originals.collectAsState()
    val now by produceState(Clock.System.now(), message.id) {
        while (true) {
            value = Clock.System.now()
            val reveal = message.clickDropRevealAt()
            val untilReveal = reveal?.let { (it - value).inWholeMilliseconds } ?: Long.MAX_VALUE
            delay(if (untilReveal in 1..30_000) untilReveal + 50 else 30_000)
        }
    }
    val state = host.controller.state(message, developedAt, now) ?: return
    val gated = message.isGatedDrop()
    val reduceMotion = rememberReduceMotionEnabled()

    // The message's own media: a gated drop's preview, or a legacy drop's photo.
    var mediaBitmap by remember(message.id) { mutableStateOf(secureChatImageBitmapCache.get(message.id)) }
    LaunchedEffect(message.id, secureState?.imageBytes) {
        if (mediaBitmap != null) return@LaunchedEffect
        val bytes = secureState?.imageBytes ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) { runCatching { bytes.toChatDisplayImageBitmap() }.getOrNull() } ?: return@LaunchedEffect
        secureChatImageBitmapCache.put(message.id, decoded)
        mediaBitmap = decoded
    }
    val originalBytes = originals[message.id]
    var originalBitmap by remember(message.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(message.id, state, originalBytes) {
        if (state != ClickDropDevelopState.Developed || !gated) return@LaunchedEffect
        val bytes = originalBytes ?: return@LaunchedEffect host.requestOriginal(message)
        originalBitmap = withContext(Dispatchers.Default) { runCatching { bytes.toChatDisplayImageBitmap(1080) }.getOrNull() }
    }
    val photo: ImageBitmap? =
        when {
            state != ClickDropDevelopState.Developed -> null
            gated -> originalBitmap
            else -> mediaBitmap
        }
    val isDeveloping = message.id in developing
    val tapLabel =
        when {
            isDeveloping -> null
            state == ClickDropDevelopState.Ready -> "Tap to develop"
            else -> clickDropCountdownLabel(state, now)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .heightIn(max = chatBubbleScaledDp(330f))
                .clip(dropShape)
                .then(if (borderIfReceived) Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), dropShape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .semantics { contentDescription = if (photo != null) "Click Drop photo" else tapLabel ?: "Click Drop" }
                .pointerInput(message.id, state, photo != null) {
                    detectTapGestures(
                        onTap = {
                            when {
                                state == ClickDropDevelopState.Ready -> host.onDevelop(message)
                                photo != null && !gated -> onExpand?.invoke()
                            }
                        },
                        onLongPress = { onLongPress?.invoke() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        val preview = mediaBitmap
        when {
            photo != null && message.id in fresh ->
                ClickDropDevelopEffect(
                    original = photo,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.fillMaxSize(),
                    onFinished = { host.controller.didShowDevelop(message.id) },
                )
            photo != null -> Image(photo, "Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            preview != null -> ClickDropPixelatedImage(preview, Modifier.fillMaxSize())
            else -> Unit
        }
        if (photo == null && (isDeveloping || preview == null || state == ClickDropDevelopState.Developed)) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = Color.White)
        }
        if (photo == null && tapLabel != null && state != ClickDropDevelopState.Developed) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.42f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            ) {
                Text(
                    text = tapLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        val up = secureState?.uploadProgress
        if (up != null && up < 1f) {
            CircularProgressIndicator(progress = { up }, modifier = Modifier.size(chatBubbleScaledDp(44f)), color = PrimaryBlue)
        }
    }
}
