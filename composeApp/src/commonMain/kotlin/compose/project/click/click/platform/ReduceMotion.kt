package compose.project.click.click.platform

import androidx.compose.runtime.Composable

/** Platform accessibility preference used to replace large spatial motion with short fades. */
@Composable
expect fun rememberReduceMotionEnabled(): Boolean

/** iOS Reduce Transparency; false on platforms without an equivalent setting. */
@Composable
expect fun rememberReduceTransparencyEnabled(): Boolean
