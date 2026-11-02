package compose.project.click.click.collaboration

import compose.project.click.click.data.flags.FeatureFlag
import compose.project.click.click.data.flags.FeatureFlags
import compose.project.click.click.data.flags.FeatureFlagsSnapshot
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The Click Drop develop state machine for chat, event and shared drops. Mirrors click-web
 * `lib/drops/developState.ts` and iOS `Click/Core/Media/ClickDropDevelop.swift`.
 *
 *   Pending   → before reveal: pixelated, countdown visible.
 *   Ready     → reveal passed, this viewer hasn't developed it. Quiet: no badge.
 *   Developed → the viewer tapped, or was watching when the timer hit zero. Permanent per viewer.
 */
sealed interface ClickDropDevelopState {
    /** [revealAt] is null when unknown (treated as never). */
    data class Pending(
        val revealAt: Instant?,
    ) : ClickDropDevelopState

    data object Ready : ClickDropDevelopState

    data object Developed : ClickDropDevelopState

    val isPending: Boolean get() = this is Pending

    companion object {
        fun resolve(
            revealAt: Instant?,
            developedAt: Instant?,
            now: Instant = Clock.System.now(),
        ): ClickDropDevelopState {
            if (revealAt == null || now < revealAt) return Pending(revealAt)
            return if (developedAt == null) Ready else Developed
        }
    }
}

internal fun parseInstantOrNull(raw: String?): Instant? =
    raw
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Server seed and bounds for `shared_drops.config.develop_hours` (click-web `sharedDropsConfigFrom`). */
internal const val SHARED_DROP_DEFAULT_DEVELOP_HOURS = 1
private const val SHARED_DROP_MAX_DEVELOP_HOURS = 72

/**
 * Shared drops develop `shared_drops.config.develop_hours` after posting (1 while the flags haven't
 * loaded: the server seed). The server stamps `reveal_at`, so clients only need this for copy.
 */
fun sharedDropDevelopHours(flags: FeatureFlagsSnapshot = FeatureFlags.state.value): Int =
    flags
        .configNumber(FeatureFlag.SHARED_DROPS, "develop_hours", SHARED_DROP_DEFAULT_DEVELOP_HOURS.toDouble())
        .toInt()
        .coerceIn(SHARED_DROP_DEFAULT_DEVELOP_HOURS, SHARED_DROP_MAX_DEVELOP_HOURS)
