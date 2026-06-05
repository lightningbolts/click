package compose.project.click.click.ui.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

/**
 * Shared Disposable Roll chrome: shutter morph, retake, vault dismiss animation.
 */
@Composable
internal fun DisposableCameraChrome(
    capturedImage: ByteArray?,
    onShutter: () -> Unit,
    onRetake: () -> Unit,
    onConfirmSend: () -> Unit,
    onDismiss: () -> Unit,
    vaultAnimating: Boolean,
    onVaultAnimationStarted: () -> Unit,
    previewContent: @Composable () -> Unit,
    frozenPreviewContent: @Composable (ByteArray) -> Unit,
) {
    val vaultScale = remember { Animatable(1f) }
    val vaultAlpha = remember { Animatable(1f) }

    LaunchedEffect(vaultAnimating) {
        if (!vaultAnimating) return@LaunchedEffect
        vaultScale.animateTo(0.12f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
        vaultAlpha.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
        delay(100)
        onConfirmSend()
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (capturedImage == null) 0.55f else 0.9f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "roll_glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = vaultScale.value
                scaleY = vaultScale.value
                alpha = vaultAlpha.value
            }
            .background(Color.Black),
    ) {
        if (capturedImage != null) {
            frozenPreviewContent(capturedImage)
        } else {
            previewContent()
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (capturedImage == null) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onShutter,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        PrimaryBlue.copy(alpha = glowAlpha),
                                        LightBlue.copy(alpha = glowAlpha * 0.35f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue),
                            )
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 96.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retake", tint = Color.White)
                }
                val confirmScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                    label = "confirm_morph",
                )
                Box(
                    modifier = Modifier
                        .size((72f * confirmScale).dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue))),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = {
                        if (!vaultAnimating) onVaultAnimationStarted()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        if (vaultAnimating && vaultScale.value < 0.2f) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
