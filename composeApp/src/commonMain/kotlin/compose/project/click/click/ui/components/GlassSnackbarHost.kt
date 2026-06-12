package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias GlassToastState = UnifiedToastState

@Composable
fun rememberGlassToastState(): GlassToastState = rememberUnifiedToastState()

/** Compact glass toast — delegates to [UnifiedToastHost]. */
@Composable
fun GlassToastHost(
    state: GlassToastState,
    modifier: Modifier = Modifier,
    opaque: Boolean = false,
) {
    UnifiedToastHost(state = state, modifier = modifier, opaque = opaque)
}

@Composable
fun GlassSnackbarHost(
    state: GlassToastState,
    modifier: Modifier = Modifier,
) = GlassToastHost(state = state, modifier = modifier)
