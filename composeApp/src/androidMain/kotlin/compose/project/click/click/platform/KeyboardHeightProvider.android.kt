package compose.project.click.click.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class KeyboardHeightProvider actual constructor() {
    private val _keyboardHeight = MutableStateFlow(0f)
    actual val keyboardHeight: StateFlow<Float> = _keyboardHeight.asStateFlow()

    private val _animationDurationMillis = MutableStateFlow(0)
    actual val animationDurationMillis: StateFlow<Int> = _animationDurationMillis.asStateFlow()

    private val _animationCurve = MutableStateFlow(0)
    actual val animationCurve: StateFlow<Int> = _animationCurve.asStateFlow()

    actual fun currentKeyboardHeightPoints(): Float = _keyboardHeight.value

    actual fun dispose() = Unit
}
