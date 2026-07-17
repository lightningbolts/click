package compose.project.click.click.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import compose.project.click.click.PlatformHapticsPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.BorderHard
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class ConnectionRevealPhase {
    Connecting,
    Success
}

data class ConnectionRevealUiState(
    val methodLabel: String,
    val phase: ConnectionRevealPhase,
    val connectedName: String? = null
)

@Composable
fun ConnectionRevealOverlay(
    state: ConnectionRevealUiState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.phase) {
        when (state.phase) {
            ConnectionRevealPhase.Connecting -> {
                PlatformHapticsPolicy.heavyImpact()
                while (isActive) {
                    delay(680)
                    PlatformHapticsPolicy.heavyImpact()
                }
            }
            ConnectionRevealPhase.Success -> {
                PlatformHapticsPolicy.successNotification()
                PlatformHapticsPolicy.heavyImpact()
            }
        }
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 360f,
        ),
        label = "reveal_card_scale",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 420f,
        ),
        label = "reveal_card_alpha",
    )
    val pulseActive = state.phase == ConnectionRevealPhase.Connecting
    val (handshakeScale, handshakeAlpha) = rememberConnectionHandshakePulse(pulseActive)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassSheetTokens.OledBlack),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(if (pulseActive) handshakeScale else 1f)
                .alpha(if (pulseActive) handshakeAlpha * 0.45f else 0.28f)
                .border(2.dp, PrimaryBlue, CircleShape)
        )

        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .scale(cardScale)
                .alpha(cardAlpha)
                .border(2.dp, BorderHard, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(min = 280.dp)
                    .padding(horizontal = 28.dp, vertical = 34.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(if (pulseActive) handshakeScale else 1f)
                        .alpha(if (pulseActive) handshakeAlpha else 1f)
                        .border(2.dp, PrimaryBlue, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.phase == ConnectionRevealPhase.Success) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Filled.QrCodeScanner
                            },
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (state.phase == ConnectionRevealPhase.Success) {
                        state.connectedName?.let { "You and $it are connected" } ?: "Connection created"
                    } else {
                        "Sparking a new connection…"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (state.phase == ConnectionRevealPhase.Success) {
                        "Opening your connections so the new reveal lands in context."
                    } else {
                        "Hold for a beat while Click turns the scan into a real connection."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )

            }
        }
    }
}
