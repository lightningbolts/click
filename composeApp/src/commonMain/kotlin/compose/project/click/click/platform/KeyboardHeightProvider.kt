package compose.project.click.click.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

expect class KeyboardHeightProvider() {
    val keyboardHeight: StateFlow<Float>
    val animationDurationMillis: StateFlow<Int>
    val animationCurve: StateFlow<Int>

    /** Re-read the live keyboard overlap — call when a chat thread becomes active. */
    fun syncFromSystem()

    /**
     * Optional synchronous lift hook. Invoked on the main queue from the keyboard
     * notification **before** StateFlow subscribers run — use this to drive composer
     * translation without Flow/collect lag.
     */
    fun setComposerLiftListener(listener: ((heightPoints: Float, durationMs: Int, curve: Int) -> Unit)?)

    fun dispose()
}

/** Process-wide last keyboard overlap in points (0 when hidden). Main-thread only on iOS. */
expect fun currentNativeKeyboardHeightPoints(): Float

@Composable
fun rememberKeyboardHeightProvider(): KeyboardHeightProvider {
    val provider = remember { KeyboardHeightProvider() }
    DisposableEffect(provider) {
        onDispose { provider.dispose() }
    }
    return provider
}
