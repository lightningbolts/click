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
    EVENTS("Events"),
    SOCIAL_VIBES("Social Vibes"),
    COMMUNITY_HUBS("Community Hubs"),
}

fun defaultMapLayerFilters(): Set<MapLayerFilter> =
    setOf(
        MapLayerFilter.MY_CONNECTIONS,
        MapLayerFilter.SOUNDTRACKS,
        MapLayerFilter.ALERTS_UTILITIES,
        MapLayerFilter.EVENTS,
        MapLayerFilter.SOCIAL_VIBES,
        MapLayerFilter.COMMUNITY_HUBS,
    )

fun layersWantHubFetch(layers: Set<MapLayerFilter>): Boolean =
    layers.contains(MapLayerFilter.ALL) || layers.contains(MapLayerFilter.COMMUNITY_HUBS)

fun layersWantBeaconFetch(layers: Set<MapLayerFilter>): Boolean =
    layers.contains(MapLayerFilter.ALL) ||
        layers.contains(MapLayerFilter.SOUNDTRACKS) ||
        layers.contains(MapLayerFilter.ALERTS_UTILITIES) ||
        layers.contains(MapLayerFilter.SOCIAL_VIBES) ||
        layers.contains(MapLayerFilter.EVENTS)
