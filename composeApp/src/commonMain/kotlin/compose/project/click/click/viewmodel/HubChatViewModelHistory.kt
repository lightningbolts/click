package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Event hub history (G3-27). Event hubs keep every message from creation until
 * `hub_venues.expires_at` (drops reveal + 24 h); after that the gatekeeper answers 410
 * HUB_EXPIRED and the chat is deleted. Community hubs keep their server-side 24 h window and
 * do not page.
 */

/** Merges an older page under the loaded timeline, de-duplicated by id and oldest first. */
internal fun mergeOlderHubPage(
    loaded: List<MessageWithUser>,
    older: List<MessageWithUser>,
): List<MessageWithUser> {
    if (older.isEmpty()) return loaded
    val loadedIds = loaded.mapTo(HashSet()) { it.message.id }
    val fresh = older.filter { it.message.id !in loadedIds }
    if (fresh.isEmpty()) return loaded
    return (fresh + loaded).sortedBy { it.message.timeCreated }
}

/** Loads the page of event-hub messages created before the oldest loaded one. */
internal suspend fun HubChatViewModel.loadOlderHubMessagesPage(): Boolean {
    if (!_isEventHub.value || participantDenied) return false
    if (_isLoadingOlderMessages.value || !_hasMoreOlderMessages.value) return false
    val oldest = _messages.value.minOfOrNull { it.message.timeCreated } ?: return false
    _isLoadingOlderMessages.value = true
    try {
        return withContext(Dispatchers.Default) {
            val token = requireFreshHubJwt()
            val page =
                chatApi
                    .fetchHubThread(
                        hubId,
                        token,
                        limit = HUB_INITIAL_MESSAGE_LIMIT.toInt(),
                        beforeCreatedAtMs = oldest,
                    ).getOrElse { error ->
                        if (HubChatViewModel.isHubExpired(error)) {
                            handleHubInteractionFailure(error, HubChatViewModel.HUB_EXPIRED_MESSAGE)
                        } else {
                            println("HubChatViewModel: older hub page failed: ${error.redactedRestMessage()}")
                        }
                        return@withContext false
                    }
            val rows = page.messages.map { it.toHubMessageRow() }.filter { it.hubId == hubId }
            if (hubSenderProfilesVisible) prefetchSenderUi(rows.map { it.userId })
            val merged = mergeOlderHubPage(_messages.value, rows.map { rowToMessageWithUser(it) })
            _messages.value = merged
            persistHubMessagesToDisk(merged)
            if (page.reactions.isNotEmpty()) mergeHubReactions(page.reactions.map { it.toMessageReaction() })
            if (page.messages.size < HUB_INITIAL_MESSAGE_LIMIT) _hasMoreOlderMessages.value = false
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("HubChatViewModel: older hub page failed: ${e.redactedRestMessage()}")
        return false
    } finally {
        _isLoadingOlderMessages.value = false
    }
}
