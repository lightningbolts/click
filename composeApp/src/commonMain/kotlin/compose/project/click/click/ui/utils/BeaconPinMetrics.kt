package compose.project.click.click.ui.utils // pragma: allowlist secret

/**
 * Shared map beacon pin metrics so Android / iOS (and web-aligned) hazard/SOS
 * pins stay compact and consistent — not oversized default platform markers.
 */
object BeaconPinMetrics {
    /** Circle radius for compact labeled / alert pins (matches prior Android labeled path). */
    const val CircleRadiusDp = 10f

    /** Glyph shown on hazard / SOS pins when no caption is available. */
    const val AlertGlyph = "⚠"
}
