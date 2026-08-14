package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Shared accent tokens for **UI chrome** — buttons, nav, highlights, icons. Purple stays the primary
 * CTA; blue is the secondary accent used ~35–40% of the time so the app reads 60/40–65/35
 * purple/blue rather than purple-only.
 *
 * This ratio is intentionally independent of [generateCardVisual], which paints *content* identity
 * from the wider [CardHueFamily] palette. Do not re-couple the two bucket tables: chrome must stay
 * on-brand while per-item cards must stay distinguishable.
 */
object ClickAccent {
    val Purple: Color = PrimaryBlue
    val Blue: Color = SecondaryAccent

    /** 5 of 8 stable slots resolve purple (62.5%). Owned here, not by the card-visual generator. */
    const val PURPLE_BUCKETS: Int = 5
    const val TOTAL_BUCKETS: Int = 8

    fun colorForStableId(id: String): Color {
        val unsigned = fnv1a32(id.ifBlank { "click" }).toUInt()
        return if ((unsigned % TOTAL_BUCKETS.toUInt()).toInt() < PURPLE_BUCKETS) Purple else Blue
    }

    fun colorForSlot(slot: Int): Color {
        val bucket = slot.mod(TOTAL_BUCKETS)
        return if (bucket < PURPLE_BUCKETS) Purple else Blue
    }
}

enum class AccentRole {
    /** Primary CTAs — always purple. */
    Cta,

    /** Secondary filled actions, chips, and map/event emphasis — blue. */
    Emphasis,

    /** Icons that are decorative / navigational rather than the primary CTA. */
    Icon,

    /** Selected / highlighted chrome that is not the main button. */
    Highlight,
}

@Composable
@ReadOnlyComposable
fun accentColor(role: AccentRole): Color =
    when (role) {
        AccentRole.Cta -> ClickAccent.Purple
        AccentRole.Emphasis, AccentRole.Highlight -> ClickAccent.Blue
        AccentRole.Icon -> ClickAccent.Blue
    }
