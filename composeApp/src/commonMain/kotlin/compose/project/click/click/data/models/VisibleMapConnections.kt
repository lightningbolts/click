package compose.project.click.click.data.models

/**
 * Connection pins shown on the map: hidden rows are omitted, duplicate 1:1 peers collapse.
 * Memory Map / [LocationPreferences.showOnMapEnabled] does not hide non-core pins.
 */
fun visibleMapConnections(
    connections: List<Connection>,
    hiddenIds: Set<String>,
    viewerId: String?,
): List<Connection> =
    collapseOneToOneConnectionsByPeer(
        connections.filter { it.id !in hiddenIds },
        viewerId,
    )
