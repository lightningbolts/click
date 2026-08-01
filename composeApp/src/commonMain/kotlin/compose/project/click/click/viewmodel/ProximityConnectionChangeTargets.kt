package compose.project.click.click.viewmodel

/**
 * Peer + connection IDs that should be refreshed after a proximity/BLE terminal success.
 * Pure so unit tests can assert invalidation targets without spinning up ViewModels.
 */
fun proximityConnectionChangeTargets(
    peerUserIds: Collection<String>,
    connectionIds: Collection<String> = emptyList(),
    currentUserId: String? = null,
): ProximityConnectionChangeTargets {
    val peers = peerUserIds
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != currentUserId?.trim() }
        .distinct()
    val connections = connectionIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return ProximityConnectionChangeTargets(
        peerUserIds = peers,
        connectionIds = connections,
    )
}

data class ProximityConnectionChangeTargets(
    val peerUserIds: List<String>,
    val connectionIds: List<String>,
)
