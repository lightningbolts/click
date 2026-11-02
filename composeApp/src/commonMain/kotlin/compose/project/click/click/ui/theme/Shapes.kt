package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 4 dp spacing rhythm and screen layout constants.
 *
 * Mirrors iOS `ClickSpacing` in `Click/DesignSystem/Spacing.swift` (points map 1:1 to dp).
 */
object ClickSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal gutter between screen edges and root content. */
    val screenGutter = 16.dp

    /** Inner padding of a grouped surface. */
    val surfacePadding = 16.dp
}

/**
 * Corner radii by semantic role. Pick the role, not the number; buttons, chips and search
 * fields use [ClickShapes.capsule] rather than a radius.
 *
 * Mirrors iOS `ClickRadius` in `Click/DesignSystem/Spacing.swift`.
 */
object ClickRadius {
    /** Badges, thumbnails and compact auxiliary surfaces. */
    val compact = 12.dp

    /** Text fields and other rectangular inputs. */
    val field = 14.dp

    /** Chat message bubbles. */
    val messageBubble = 18.dp

    /** Grouped semantic regions (lists, settings groups, information blocks). */
    val surface = 26.dp

    /** Hero and signature surfaces. */
    val prominent = 30.dp
}

/**
 * Ready-made shapes for each [ClickRadius] role plus a capsule. Read through [LocalClickShapes]
 * (provided by [PlatformThemeProvider]) the same way [LocalPlatformStyle] is read.
 */
@Immutable
data class ClickShapes(
    val compact: RoundedCornerShape = RoundedCornerShape(ClickRadius.compact),
    val field: RoundedCornerShape = RoundedCornerShape(ClickRadius.field),
    val messageBubble: RoundedCornerShape = RoundedCornerShape(ClickRadius.messageBubble),
    val surface: RoundedCornerShape = RoundedCornerShape(ClickRadius.surface),
    val prominent: RoundedCornerShape = RoundedCornerShape(ClickRadius.prominent),
    /** Fully rounded ends; iOS `Capsule()`. */
    val capsule: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

val LocalClickShapes = staticCompositionLocalOf { ClickShapes() }
