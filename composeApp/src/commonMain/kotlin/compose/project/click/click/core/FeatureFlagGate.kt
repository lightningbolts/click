package compose.project.click.click.core

/**
 * Minimal server-flag gate (`GET /api/me/features`). Unknown or unloaded flags are off. The
 * FeatureFlags store wires [isEnabled] to its live values.
 */
object FeatureFlagGate {
    var isEnabled: (String) -> Boolean = { false }
}
