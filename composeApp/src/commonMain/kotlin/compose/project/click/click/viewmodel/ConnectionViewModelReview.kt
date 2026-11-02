package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeResult // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/*
 * "Who's in this Click?" review sync and late joiners (07 §6 G4-4, G4-5).
 * iOS: `Click/Features/AddClick/TapConnectModel.swift` (`watchReview`, `watchForGroup`,
 * `finishAfterOtherConfirmed`) and `TapConnectView.swift` (`PeopleReviewView`).
 */

/** Covers the server's late-join window plus the late phone's review (iOS `groupWatchWindow`). */
internal const val REVIEW_WATCH_WINDOW_MS: Long = 60_000L

/** Shown when a 1:1 result grows into a group while it is on screen. */
internal const val GROUP_CREATED_NOTICE: String = "Group created"

private fun firstNameOf(name: String?): String =
    name
        ?.trim()
        ?.substringBefore(' ')
        ?.ifEmpty { null } ?: "someone"

/** "Sam", "Sam and Ada", "Sam, Ada, and Lee" (iOS `ListFormatter`). */
internal fun joinedFirstNames(names: List<String?>): String {
    val first = names.map(::firstNameOf)
    return when (first.size) {
        0 -> ""
        1 -> first[0]
        2 -> "${first[0]} and ${first[1]}"
        else -> first.dropLast(1).joinToString(", ") + ", and " + first.last()
    }
}

/**
 * Shown when another phone confirmed the group first and still included someone this person
 * removed (iOS `finishAfterOtherConfirmed`). Null when nobody removed is in the group.
 */
internal fun otherPhoneConfirmedNotice(stillIn: List<User>): String? {
    if (stillIn.isEmpty()) return null
    val verb = if (stillIn.size == 1) "is" else "are"
    return "Another phone confirmed this group first, so ${joinedFirstNames(stillIn.map { it.name })} $verb still in it. " +
        "You can leave the group from its settings."
}

/** Candidates the review doesn't show yet (someone who tapped a moment later), capped at the host limit. */
internal fun lateJoiners(
    known: List<String>,
    candidates: List<User>,
    currentUserId: String,
): List<User> {
    val seen = known.toSet()
    return candidates
        .filter { it.id.isNotBlank() && it.id != currentUserId && it.id !in seen }
        .distinctBy { it.id }
        .take((PROXIMITY_HOST_SELECTION_MAX_PEERS - known.size).coerceAtLeast(0))
}

/** The review with [added] people appended; late joiners start included, existing removals stay. */
internal fun ConnectionState.TaggingContext.withLateJoiners(
    added: List<UserProfile>,
    existingConnectionId: String?,
): ConnectionState.TaggingContext {
    val known = selectableUsers.ifEmpty { targetUsers }
    val ids = added.map { it.id }
    return copy(
        selectableUsers = known + added,
        targetUsers = (targetUsers + added).distinctBy { it.id },
        memberUserIds = (memberUserIds + ids).distinct().sorted(),
        selectedPeerIds = selectedPeerIds + ids,
        existingConnectionId = existingConnectionId,
        isGroup = true,
    )
}

/** True while [state] still shows the result for [connectionId] (a group watch may replace it). */
internal fun isShowingResult(
    state: ConnectionState,
    connectionId: String?,
): Boolean =
    when (state) {
        is ConnectionState.TaggingContext ->
            !state.requiresSelection && state.newConnections.firstOrNull()?.id == connectionId
        is ConnectionState.Success -> state.connection.id == connectionId
        else -> false
    }

private suspend fun ConnectionViewModel.freshJwt(): String? =
    runCatching { EnsureFreshAccessToken.get(repository.tokenStorage) } // pragma: allowlist secret
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Leaving the review means not joining: saves everyone as removed so another phone's confirm leaves this person out. */
internal fun ConnectionViewModel.leaveReview(tagging: ConnectionState.TaggingContext) {
    val pendingId = tagging.pendingHandshakeId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val everyone = tagging.selectableUsers.ifEmpty { tagging.targetUsers }.map { it.id }
    if (everyone.isEmpty()) return
    viewModelScope.launch {
        repository.recordProximitySelectionExclusions(pendingId, everyone.distinct().sorted())
    }
}

/** Another phone's confirm finished the tap: show its result, naming anyone removed who is still in it. */
internal fun ConnectionViewModel.finishAfterOtherConfirmed(
    outcome: BindProximityHandshakeOutcome,
    removed: Set<String>,
    currentUserId: String,
) {
    otherPhoneConfirmedNotice(outcome.matches.filter { it.id in removed })?.let { _transientNotice.tryEmit(it) }
    hostSelectionSync.reset()
    handleInstantProximityOutcome(outcome, currentUserId)
}

/**
 * G4-4: while people are reviewed, poll the stored tap so another phone's confirm moves this one
 * to the result, and a late joiner appears in the rows (iOS `watchReview`).
 */
internal fun ConnectionViewModel.startReviewWatch(
    pendingHandshakeId: String,
    currentUserId: String,
) {
    val pendingId = pendingHandshakeId.trim().takeIf { it.isNotEmpty() } ?: return
    reviewWatchJob?.cancel()
    reviewWatchJob =
        viewModelScope.launch {
            val deadline = nowMs() + REVIEW_WATCH_WINDOW_MS

            fun reviewing(): ConnectionState.TaggingContext? =
                (_connectionState.value as? ConnectionState.TaggingContext)
                    ?.takeIf { it.requiresSelection && it.pendingHandshakeId == pendingId }
            while (nowMs() < deadline) {
                delay(ConnectionViewModel.PENDING_MATCH_RECOVERY_DELAY_MS)
                reviewing() ?: return@launch
                val jwt = freshJwt() ?: continue
                val recovered =
                    withContext(Dispatchers.Default) {
                        repository.recoverPendingProximityHandshake(jwt, pendingId)
                    }.getOrNull() ?: continue
                val latest = reviewing() ?: return@launch
                when (recovered) {
                    is BindProximityHandshakeResult.InstantMatch -> {
                        val candidates = latest.selectableUsers.ifEmpty { latest.targetUsers }.map { it.id }
                        finishAfterOtherConfirmed(
                            outcome = recovered.outcome,
                            removed = candidates.filterNot { it in latest.selectedPeerIds }.toSet(),
                            currentUserId = currentUserId,
                        )
                        return@launch
                    }
                    is BindProximityHandshakeResult.AwaitingHostSelection -> {
                        val known = latest.selectableUsers.ifEmpty { latest.targetUsers }.map { it.id }
                        val added = lateJoiners(known, recovered.candidates, currentUserId)
                        if (added.isEmpty()) continue
                        PlatformHapticsPolicy.lightImpact()
                        _connectionState.value =
                            latest.withLateJoiners(
                                added = added.map { it.toUserProfile() },
                                existingConnectionId = recovered.existingConnectionId,
                            )
                    }
                    is BindProximityHandshakeResult.PendingServerMatch -> Unit
                }
            }
        }
}

/**
 * G4-5: a result can grow into a group when someone tapped a moment later. While the result is
 * on screen, poll this phone's tap and switch to the group as soon as the server reports it
 * (iOS `watchForGroup`). [deadlineMs] keeps the original window across switches.
 */
internal fun ConnectionViewModel.startGroupWatch(
    pendingHandshakeId: String?,
    shownConnectionId: String?,
    currentUserId: String,
    deadlineMs: Long = nowMs() + REVIEW_WATCH_WINDOW_MS,
) {
    val pendingId = pendingHandshakeId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val shown = shownConnectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    groupWatchJob?.cancel()
    groupWatchJob =
        viewModelScope.launch {
            while (nowMs() < deadlineMs) {
                delay(ConnectionViewModel.PENDING_MATCH_RECOVERY_DELAY_MS)
                if (!isShowingResult(_connectionState.value, shown)) return@launch
                val jwt = freshJwt() ?: continue
                val recovered =
                    withContext(Dispatchers.Default) {
                        repository.recoverPendingProximityHandshake(jwt, pendingId)
                    }.getOrNull() as? BindProximityHandshakeResult.InstantMatch ?: continue
                val outcome = recovered.outcome
                if (!outcome.isGroup || outcome.connectionId == null || outcome.connectionId == shown) continue
                if (!isShowingResult(_connectionState.value, shown)) return@launch
                _transientNotice.tryEmit(GROUP_CREATED_NOTICE)
                handleInstantProximityOutcome(
                    outcome = outcome.copy(pendingHandshakeId = outcome.pendingHandshakeId ?: pendingId),
                    currentUserId = currentUserId,
                    groupWatchDeadlineMs = deadlineMs,
                )
                AppDataManager.requestInboxReload()
                return@launch
            }
        }
}

/**
 * Connections a group Click's tags are written to (07 §6 G4-6; iOS `PostConnectModel.taggableConnectionIDs`):
 * the Click's own connections first, then this person's 1:1 connection with each other member, so the
 * tag also shows on each peer's timeline. Pair connections come from the local list; a missing pair is skipped.
 */
internal fun groupTagTargetIds(
    clickConnectionIds: List<String>,
    memberUserIds: List<String>,
    currentUserId: String,
    knownConnections: List<compose.project.click.click.data.models.Connection>,
): List<String> {
    val peers = memberUserIds.filter { it.isNotBlank() && it != currentUserId }.distinct()
    val pairs =
        peers.mapNotNull { peer ->
            knownConnections
                .firstOrNull { c ->
                    !c.isGroup && c.user_ids.size == 2 && currentUserId in c.user_ids && peer in c.user_ids
                }?.id
        }
    return (clickConnectionIds + pairs).filter { it.isNotBlank() }.distinct()
}
