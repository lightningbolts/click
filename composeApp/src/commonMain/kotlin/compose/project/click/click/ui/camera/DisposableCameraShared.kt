package compose.project.click.click.ui.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.GlassCard
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Shared Disposable Roll chrome around the native camera preview.
 */
@Composable
internal fun DisposableCameraChrome(
    modifier: Modifier = Modifier,
    capturedImage: ByteArray?,
    isShutterEnabled: Boolean,
    onShutter: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    previewContent: @Composable () -> Unit,
    frozenPreviewContent: @Composable (ByteArray) -> Unit,
) {
    val flashAlpha = remember { Animatable(0f) }
    var flashTick by remember { mutableIntStateOf(0) }
    val hasCapture = capturedImage != null

    LaunchedEffect(flashTick) {
        if (flashTick == 0) return@LaunchedEffect
        flashAlpha.snapTo(0.82f)
        flashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing),
        )
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (isShutterEnabled || hasCapture) 0.55f else 0.22f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "roll_glow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (capturedImage == null) {
            previewContent()
        } else {
            frozenPreviewContent(capturedImage)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.68f),
                            Color.Black.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f),
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )

        GlassIconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp),
            contentDescription = "Close camera",
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.32f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
            ),
        ) {
            Text(
                text = "Disposable Roll",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.26f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                    ),
                ) {
                    Text(
                        text = when {
                            capturedImage != null -> "Ready for the roll"
                            isShutterEnabled -> "Snap once"
                            else -> "Capturing..."
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                if (capturedImage == null) {
                    ShutterButton(
                        enabled = isShutterEnabled,
                        glowAlpha = glowAlpha,
                        onClick = {
                            PlatformHapticsPolicy.lightImpact()
                            flashTick += 1
                            onShutter()
                        },
                    )
                } else {
                    SendRollButton(
                        glowAlpha = glowAlpha,
                        onClick = {
                            PlatformHapticsPolicy.successNotification()
                            onSend()
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value)),
        )
    }
}

@Composable
internal fun DisposableCameraFallback(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color(0xFF07111F),
                        Color.Black,
                    ),
                ),
            ),
    ) {
        GlassIconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp),
            contentDescription = "Close camera",
        )

        GlassCard(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            usePrimaryBorder = true,
            contentPadding = 22.dp,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(42.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.74f),
            )
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onPrimaryAction) {
                    Text(primaryActionLabel)
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    contentDescription: String,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SendRollButton(
    glowAlpha: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = glowAlpha),
                            LightBlue.copy(alpha = glowAlpha * 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue)))
                .border(4.dp, Color.White.copy(alpha = 0.92f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send to Disposable Roll",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    glowAlpha: Float,
    onClick: () -> Unit,
) {
    val shutterScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "shutter_scale",
    )

    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                scaleX = shutterScale
                scaleY = shutterScale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                .size(78.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(5.dp, Color.White.copy(alpha = 0.96f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
