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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.GlassCard
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlin.math.roundToInt

/**
 * Shared Disposable Roll chrome around the native camera preview.
 */
@Composable
internal fun DisposableCameraChrome(
    modifier: Modifier = Modifier,
    capturedImage: ByteArray?,
    isShutterEnabled: Boolean,
    selectedFilterIndex: Int,
    onFilterIndexChange: (Int) -> Unit,
    onFlipCamera: () -> Unit,
    onShutter: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    extraBottomPadding: Dp = 0.dp,
    previewContent: @Composable () -> Unit,
    frozenPreviewContent: @Composable (ByteArray) -> Unit,
) {
    val flashAlpha = remember { Animatable(0f) }
    var flashTick by remember { mutableIntStateOf(0) }
    val hasCapture = capturedImage != null
    val filterColorFilter = DisposableRollFilters.colorFilterFor(selectedFilterIndex)

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val isLandscape = maxWidth > maxHeight

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { colorFilter = filterColorFilter },
        ) {
            if (capturedImage == null) {
                previewContent()
            } else {
                frozenPreviewContent(capturedImage)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(if (isLandscape) 120.dp else 190.dp)
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
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (isLandscape) {
                        Modifier
                            .fillMaxHeight()
                            .width(220.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    },
                )
                .background(
                    if (isLandscape) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        )
                    },
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

        if (isLandscape) {
            DisposableRollFilterSlider(
                selectedIndex = selectedFilterIndex,
                onSelectedIndexChange = onFilterIndexChange,
                isLandscape = true,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(end = 18.dp, top = 72.dp, bottom = 72.dp)
                    .fillMaxHeight(0.55f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 52.dp + extraBottomPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!isLandscape) {
                DisposableRollFilterSlider(
                    selectedIndex = selectedFilterIndex,
                    onSelectedIndexChange = onFilterIndexChange,
                    isLandscape = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            DisposableCameraStatusChip(
                capturedImage = capturedImage,
                isShutterEnabled = isShutterEnabled,
                compact = isLandscape,
            )
            Spacer(modifier = Modifier.height(14.dp))
            DisposableCameraCaptureControls(
                capturedImage = capturedImage,
                isShutterEnabled = isShutterEnabled,
                glowAlpha = glowAlpha,
                isLandscape = isLandscape,
                onFlipCamera = onFlipCamera,
                onShutter = {
                    PlatformHapticsPolicy.lightImpact()
                    flashTick += 1
                    onShutter()
                },
                onSend = {
                    PlatformHapticsPolicy.successNotification()
                    onSend()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value)),
        )
    }
    }
}

@Composable
private fun DisposableCameraStatusChip(
    capturedImage: ByteArray?,
    isShutterEnabled: Boolean,
    compact: Boolean,
) {
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
                capturedImage != null -> if (compact) "Ready" else "Ready for the roll"
                isShutterEnabled -> if (compact) "Snap" else "Snap once"
                else -> if (compact) "..." else "Capturing..."
            },
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 7.dp,
            ),
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun DisposableCameraCaptureControls(
    capturedImage: ByteArray?,
    isShutterEnabled: Boolean,
    glowAlpha: Float,
    isLandscape: Boolean,
    onFlipCamera: () -> Unit,
    onShutter: () -> Unit,
    onSend: () -> Unit,
) {
    if (isLandscape) {
        // Landscape: shutter stays bottom-center; flip sits directly above it.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlipCameraButton(onClick = onFlipCamera)
            Spacer(modifier = Modifier.height(14.dp))
            if (capturedImage == null) {
                ShutterButton(
                    enabled = isShutterEnabled,
                    glowAlpha = glowAlpha,
                    onClick = onShutter,
                )
            } else {
                SendRollButton(
                    glowAlpha = glowAlpha,
                    onClick = onSend,
                )
            }
        }
    } else {
        // Portrait: flip beside shutter on one row; spacer balances flip width so shutter stays centered.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FlipCameraButton(onClick = onFlipCamera)
            if (capturedImage == null) {
                ShutterButton(
                    enabled = isShutterEnabled,
                    glowAlpha = glowAlpha,
                    onClick = onShutter,
                )
            } else {
                SendRollButton(
                    glowAlpha = glowAlpha,
                    onClick = onSend,
                )
            }
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun DisposableRollFilterSlider(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember(selectedIndex) { mutableIntStateOf(DisposableRollFilters.clampIndex(selectedIndex)) }
    LaunchedEffect(selectedIndex) {
        dragIndex = DisposableRollFilters.clampIndex(selectedIndex)
    }
    val clampedIndex = dragIndex

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = DisposableRollFilters.nameFor(clampedIndex),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.92f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        BoxWithConstraints(
            modifier = Modifier
                .then(
                    if (isLandscape) {
                        Modifier
                            .width(52.dp)
                            .fillMaxHeight()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    },
                )
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .pointerInput(isLandscape) {
                    val thresholdPx = with(density) { 28.dp.toPx() }
                    if (isLandscape) {
                        detectVerticalDragGestures { _, dragAmount ->
                            dragAccumulator += dragAmount
                            while (dragAccumulator <= -thresholdPx) {
                                dragAccumulator += thresholdPx
                                val next = DisposableRollFilters.clampIndex(dragIndex - 1)
                                if (next != dragIndex) {
                                    dragIndex = next
                                    PlatformHapticsPolicy.lightImpact()
                                    onSelectedIndexChange(next)
                                }
                            }
                            while (dragAccumulator >= thresholdPx) {
                                dragAccumulator -= thresholdPx
                                val next = DisposableRollFilters.clampIndex(dragIndex + 1)
                                if (next != dragIndex) {
                                    dragIndex = next
                                    PlatformHapticsPolicy.lightImpact()
                                    onSelectedIndexChange(next)
                                }
                            }
                        }
                    } else {
                        detectHorizontalDragGestures { _, dragAmount ->
                            dragAccumulator += dragAmount
                            while (dragAccumulator <= -thresholdPx) {
                                dragAccumulator += thresholdPx
                                val next = DisposableRollFilters.clampIndex(dragIndex - 1)
                                if (next != dragIndex) {
                                    dragIndex = next
                                    PlatformHapticsPolicy.lightImpact()
                                    onSelectedIndexChange(next)
                                }
                            }
                            while (dragAccumulator >= thresholdPx) {
                                dragAccumulator -= thresholdPx
                                val next = DisposableRollFilters.clampIndex(dragIndex + 1)
                                if (next != dragIndex) {
                                    dragIndex = next
                                    PlatformHapticsPolicy.lightImpact()
                                    onSelectedIndexChange(next)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val trackLength = if (isLandscape) maxHeight else maxWidth
            val dotSize = 10.dp
            val spacing = (trackLength - dotSize * DisposableRollFilters.COUNT) /
                (DisposableRollFilters.COUNT - 1).coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .then(
                        if (isLandscape) {
                            Modifier
                                .width(dotSize)
                                .fillMaxHeight()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(dotSize)
                        },
                    ),
            ) {
                DisposableRollFilters.all.forEachIndexed { index, _ ->
                    val selected = index == clampedIndex
                    val step = dotSize + spacing
                    val offset = if (isLandscape) {
                        IntOffset(
                            x = 0,
                            y = with(density) { (step * index).roundToPx() },
                        )
                    } else {
                        IntOffset(
                            x = with(density) { (step * index).roundToPx() },
                            y = 0,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset { offset }
                            .size(if (selected) 14.dp else dotSize)
                            .clip(CircleShape)
                            .background(
                                if (selected) PrimaryBlue else Color.White.copy(alpha = 0.42f),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                PlatformHapticsPolicy.lightImpact()
                                dragIndex = index
                                onSelectedIndexChange(index)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlipCameraButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    PlatformHapticsPolicy.lightImpact()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "Flip camera",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
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
