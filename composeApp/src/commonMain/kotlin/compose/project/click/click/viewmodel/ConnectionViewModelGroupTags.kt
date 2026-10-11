package compose.project.click.click.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Outlives the post-connect screen so a best-effort sensor patch (noise) runs to completion
 * after the user taps Done (iOS `recordSensorContext` runs in its own task).
 */
internal val ConnectionBackgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Shown on the tag sheet when a group's pair connections can't be resolved (the user can retry). */
internal const val GROUP_TAG_TARGETS_FAILED_MESSAGE: String =
    "Tags weren't saved: couldn't reach everyone's connection in this group. Try again."

/**
 * Where a group Click's tags and sensor context go (G0-15; iOS `PostConnectModel.taggableConnectionIDs`):
 * the Click's own connections, plus the viewer's pair connection with each member, since that is
 * the timeline each person's profile shows. Order kept, duplicates dropped.
 */
internal fun groupTagTargetIds(
    directIds: List<String>,
    pairIds: List<String>,
): List<String> = (directIds + pairIds).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** `mergePatchLatestEncounter` failures meaning the row predates this tap (merged into an earlier hangout). */
internal fun isNoActiveEncounterFailure(error: Throwable?): Boolean {
    val message = error?.message.orEmpty()
    return message.contains("No active encounter row", ignoreCase = true) ||
        message.contains("No encounter row", ignoreCase = true)
}

/**
 * The viewer's pair connection IDs with every other member, resolved once per member set and
 * cached only on success: a failed lookup throws and is retried next time (never cached).
 */
internal suspend fun ConnectionViewModel.resolveGroupPairConnectionIds(
    viewerId: String,
    memberIds: List<String>,
    lookup: suspend (viewerId: String, peerIds: List<String>) -> Map<String, String> = { v, p -> repository.fetchPairConnectionIds(v, p) },
): List<String> {
    val peers = memberIds.filter { it.isNotBlank() && it != viewerId }.distinct().sorted()
    val key = (peers + viewerId).sorted().joinToString(",")
    groupTagPairCache[key]?.let { return it }
    val pairs = lookup(viewerId, peers)
    val ids = peers.mapNotNull { pairs[it] }
    groupTagPairCache[key] = ids
    return ids
}
