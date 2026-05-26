package compose.project.click.click.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

expect class KeyboardHeightProvider() {
    val keyboardHeight: StateFlow<Float>
    val animationDurationMillis: StateFlow<Int>
    val animationCurve: StateFlow<Int>

    fun dispose()
}

@Composable
fun rememberKeyboardHeightProvider(): KeyboardHeightProvider {
    val provider = remember { KeyboardHeightProvider() }
    DisposableEffect(provider) {
        onDispose { provider.dispose() }
    }
    return provider
}
