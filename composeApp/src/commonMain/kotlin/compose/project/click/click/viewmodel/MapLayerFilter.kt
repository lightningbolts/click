package compose.project.click.click.viewmodel // pragma: allowlist secret

/**
 * Map overlay layers (connections + community beacons). Multiple selections combine (union).
 * [ALL] is a shortcut to show every layer.
 */
enum class MapLayerFilter(val label: String) {
    ALL("All"),
    MY_CONNECTIONS("My Connections"),
    SOUNDTRACKS("Soundtracks"),
    ALERTS_UTILITIES("Alerts & Utilities"),
    SOCIAL_VIBES("Social Vibes"),
}

fun defaultMapLayerFilters(): Set<MapLayerFilter> =
    setOf(
        MapLayerFilter.MY_CONNECTIONS,
        MapLayerFilter.SOUNDTRACKS,
        MapLayerFilter.ALERTS_UTILITIES,
        MapLayerFilter.SOCIAL_VIBES,
    )
