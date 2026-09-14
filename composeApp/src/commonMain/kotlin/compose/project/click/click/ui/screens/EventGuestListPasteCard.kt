package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Compatibility shim while the event-detail surface is being decomposed for the recovery pass.
 *
 * Guest-list seeding is a web-only organizer workflow. Mobile intentionally renders nothing here;
 * the old paste-email UI and its API calls were removed from EventBeaconDetailParts.
 *
 * Remove this shim when EventBeaconDetail no longer references the legacy slot.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun EventGuestListPasteCard(
    beaconId: String,
    border: Color,
    cardSurface: Color,
) = Unit
