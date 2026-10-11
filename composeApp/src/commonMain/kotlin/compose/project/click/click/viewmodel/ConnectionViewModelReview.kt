package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeResult // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Covers the server's late-join window plus the late phone's review (iOS `groupWatchWindow`). */
internal const val REVIEW_WATCH_WINDOW_MS: Long = 60_000L

/**
 * Shown when another phone confirmed the group first and still included someone this person
 * removed (iOS `finishAfterOtherConfirmed`).
 */
internal fun otherPhoneConfirmedNotice(stillIn: List<User>): String? {
    if (stillIn.isEmpty()) return null
    val names =
        stillIn.map {
            it.name
                ?.trim()
                ?.substringBefore(' ')
                ?.ifEmpty { null } ?: "someone"
        }
    val joined =
        when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }
    val verb = if (names.size == 1) "is" else "are"
    return "Another phone confirmed this group first, so $joined $verb still in it. You can leave the group from its settings."
}

/** Candidates the review doesn't show yet (someone who tapped a moment later), capped. */
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

private suspend fun ConnectionViewModel.freshJwt(): String? =
    runCatching { EnsureFreshAccessToken.get(repository.tokenStorage) }.getOrNull()?.takeIf { it.isNotBlank() } // pragma: allowlist secret

/** Another phone's confirm (or this phone's recovery) finished the tap: show its result. */
internal fun ConnectionViewModel.finishAfterOtherConfirmed(
    outcome: BindProximityHandshakeOutcome,
    currentUserId: String,
) {
    val removed = hostSelectionExcluded
    val stillIn = outcome.matches.filter { it.id in removed }
    otherPhoneConfirmedNotice(stillIn)?.let { _transientNotice.tryEmit(it) }
    hostSelectionExcluded = emptySet()
    handleInstantProximityOutcome(outcome, currentUserId)
}

/**
 * G4-4: while people are reviewed, poll the stored tap so another phone's confirm moves this one
 * to the result and a late joiner appears in the rows (iOS `watchReview`).
 */
internal fun ConnectionViewModel.startReviewWatch(
    pendingHandshakeId: String,
    currentUserId: String,
) {
    reviewWatchJob?.cancel()
    reviewWatchJob =
        viewModelScope.launch {
            val deadline =
                kotlinx.datetime.Clock.System
                    .now()
                    .toEpochMilliseconds() + REVIEW_WATCH_WINDOW_MS
            while (kotlinx.datetime.Clock.System
                    .now()
                    .toEpochMilliseconds() < deadline
            ) {
                delay(ConnectionViewModel.PENDING_MATCH_RECOVERY_DELAY_MS)
                val current = _connectionState.value as? ConnectionState.TaggingContext ?: return@launch
                if (!current.requiresSelection || current.pendingHandshakeId != pendingHandshakeId) return@launch
                val jwt = freshJwt() ?: continue
                val recovered =
                    withContext(Dispatchers.Default) {
                        repository.recoverPendingProximityHandshake(jwt, pendingHandshakeId)
                    }.getOrNull() ?: continue
                val latest = _connectionState.value as? ConnectionState.TaggingContext ?: return@launch
                if (!latest.requiresSelection || latest.pendingHandshakeId != pendingHandshakeId) return@launch
                when (recovered) {
                    is BindProximityHandshakeResult.InstantMatch -> {
                        finishAfterOtherConfirmed(recovered.outcome, currentUserId)
                        return@launch
                    }
                    is BindProximityHandshakeResult.AwaitingHostSelection -> {
                        val known = latest.selectableUsers.ifEmpty { latest.targetUsers }
                        val added = lateJoiners(known.map { it.id }, recovered.candidates, currentUserId)
                        if (added.isEmpty()) continue
                        val addedProfiles = added.map { it.toUserProfile() }
                        // Keep this person's current removals; late joiners start included.
                        val selected =
                            (hostSelectionCurrent ?: latest.selectedPeerIds) + addedProfiles.map { it.id }
                        hostSelectionCurrent = selected
                        _connectionState.value =
                            latest.copy(
                                selectableUsers = known + addedProfiles,
                                targetUsers = latest.targetUsers + addedProfiles,
                                memberUserIds = (latest.memberUserIds + addedProfiles.map { it.id }).distinct().sorted(),
                                selectedPeerIds = selected,
                                existingConnectionId = recovered.existingConnectionId,
                            )
                    }
                    is BindProximityHandshakeResult.PendingServerMatch -> Unit
                }
            }
        }
}

/**
 * G4-5: a 1:1 result can grow into a group when someone tapped a moment later. While the result
 * is on screen, poll the tap and switch to the group as soon as the server reports it.
 */
internal fun ConnectionViewModel.startGroupWatch(
    pendingHandshakeId: String?,
    shownConnectionId: String?,
    currentUserId: String,
) {
    val pendingId = pendingHandshakeId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    groupWatchJob?.cancel()
    groupWatchJob =
        viewModelScope.launch {
            var shown = shownConnectionId
            val deadline =
                kotlinx.datetime.Clock.System
                    .now()
                    .toEpochMilliseconds() + REVIEW_WATCH_WINDOW_MS
            while (kotlinx.datetime.Clock.System
                    .now()
                    .toEpochMilliseconds() < deadline
            ) {
                delay(ConnectionViewModel.PENDING_MATCH_RECOVERY_DELAY_MS)
                val state = _connectionState.value
                val stillShowing =
                    (state is ConnectionState.TaggingContext && !state.requiresSelection) || state is ConnectionState.Success
                if (!stillShowing) return@launch
                val jwt = freshJwt() ?: continue
                val recovered =
                    withContext(Dispatchers.Default) {
                        repository.recoverPendingProximityHandshake(jwt, pendingId)
                    }.getOrNull() as? BindProximityHandshakeResult.InstantMatch ?: continue
                val outcome = recovered.outcome
                if (!outcome.isGroup || outcome.connectionId == null || outcome.connectionId == shown) continue
                shown = outcome.connectionId
                _transientNotice.tryEmit("Group created")
                handleInstantProximityOutcome(outcome, currentUserId)
                AppDataManager.requestInboxReload()
            }
        }
}
