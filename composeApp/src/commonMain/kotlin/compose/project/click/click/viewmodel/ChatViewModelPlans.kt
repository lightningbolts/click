package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.HangoutPlan // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.PlanResponses // pragma: allowlist secret
import compose.project.click.click.data.models.PlanRsvp // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.planOrNull // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminder // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminderScheduler // pragma: allowlist secret
import compose.project.click.click.notifications.LocalReminderTarget // pragma: allowlist secret
import compose.project.click.click.notifications.PlanReminderTiming // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

/*
 * Chat plans (iOS `ConversationModel.sendPlan` / `rsvp`, `PlanReminders`). A plan is an ordinary
 * E2EE text message whose `metadata.plan` drives the card; RSVPs are ✅ / ❌ reactions.
 * Direct and group chats only (hubs have no plans, as on iOS).
 */

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Display name for the open chat: the group name, or the other person's first name. */
internal fun ChatViewModel.openChatDisplayName(): String {
    val details = (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails ?: return "your Click"
    details.groupClique
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return details.otherUser.name
        ?.trim()
        ?.substringBefore(' ')
        ?.takeIf { it.isNotEmpty() } ?: "your Click"
}

internal fun ChatViewModel.sendPlanImpl(plan: HangoutPlan) {
    _plannerOpen.value = false
    val userId = _currentUserId.value ?: return
    val successState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = currentConnectionId ?: return
    val summary = plan.summary()
    val metadata = buildJsonObject { put("plan", plan.toWire()) }
    viewModelScope.launch {
        _isMessageSubmitInProgress.value = true
        val localMs = nowMs()
        val tempId = "temp-plan-$localMs-${Random.nextLong()}"
        val me =
            AppDataManager.currentUser.value?.takeIf { it.id == userId }
                ?: User(id = userId, name = "You", createdAt = 0L)
        try {
            appendOutgoingOptimistic(
                Message(
                    id = tempId,
                    user_id = userId,
                    content = summary,
                    timeCreated = localMs,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadata,
                    localSentAt = localMs,
                    deliveryState = MessageDeliveryState.PENDING,
                ),
                me,
            )
            val apiChatId =
                resolveOrCreateApiChatId(connectionId) ?: run {
                    markOptimisticSendFailed(tempId)
                    _messageSendError.value = "Couldn't send the plan — unable to start chat"
                    return@launch
                }
            val sendConnectionId =
                successState.chatDetails.chat.connectionId
                    ?.takeIf { it.isNotBlank() && it != apiChatId }
                    ?: connectionId.takeIf { successState.chatDetails.groupClique == null }
            val sent =
                chatRepository.sendMessage(
                    chatId = apiChatId,
                    userId = userId,
                    content = summary,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadata,
                    clientLocalSentAtMs = localMs,
                    connectionId = sendConnectionId,
                )
            if (sent == null) {
                markOptimisticSendFailed(tempId)
                _messageSendError.value = "Couldn't send the plan"
                return@launch
            }
            applyInsertedMessage(sent, resolveMessageUser(userId, apiChatId) ?: me, userId, optimisticTempId = tempId)
            // The planner is going by default (iOS auto-RSVP).
            setPlanRsvpImpl(sent.id, PlanRsvp.GOING)
        } catch (e: Exception) {
            markOptimisticSendFailed(tempId)
            _messageSendError.value = "Couldn't send the plan — ${e.redactedRestMessage().ifBlank { "error" }}"
        } finally {
            _isMessageSubmitInProgress.value = false
        }
    }
}

/**
 * Sets (or with [rsvp] null, clears) the viewer's RSVP. Going and Can't are mutually exclusive:
 * the opposite reaction is removed first. Updates the local reminder.
 */
internal fun ChatViewModel.setPlanRsvpImpl(
    messageId: String,
    rsvp: PlanRsvp?,
) {
    val userId = _currentUserId.value ?: return
    if (messageId.startsWith("temp-")) return
    val wanted =
        when (rsvp) {
            PlanRsvp.GOING -> HangoutPlan.GOING
            PlanRsvp.DECLINED -> HangoutPlan.DECLINED
            null -> null
        }
    val existing = _messageReactions.value[messageId].orEmpty()
    val mine = existing.filter { it.userId == userId && (it.reactionType == HangoutPlan.GOING || it.reactionType == HangoutPlan.DECLINED) }
    val toRemove = mine.filter { it.reactionType != wanted }.map { it.reactionType }.distinct()
    val toAdd = wanted?.takeIf { w -> mine.none { it.reactionType == w } }

    val updated = existing.filterNot { it.userId == userId && it.reactionType in toRemove }.toMutableList()
    if (toAdd != null) {
        updated +=
            MessageReaction(
                id = "temp-$messageId-$toAdd",
                messageId = messageId,
                userId = userId,
                reactionType = toAdd,
                createdAt = nowMs(),
            )
    }
    _messageReactions.value = _messageReactions.value + (messageId to updated.toList())
    viewModelScope.launch {
        toRemove.forEach { chatRepository.removeReaction(messageId, userId, it) }
        toAdd?.let { chatRepository.addReaction(messageId, userId, it) }
    }
    syncPlanReminderImpl(messageId)
}

/** Schedules or cancels the local reminder for one plan message from the current RSVP state. */
internal fun ChatViewModel.syncPlanReminderImpl(messageId: String) {
    val userId = _currentUserId.value ?: return
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val message = state.messages.firstOrNull { it.message.id == messageId }?.message ?: return
    val plan = message.planOrNull() ?: return
    val reminderId = PlanReminderTiming.reminderId(messageId)
    val going = PlanResponses.from(_messageReactions.value[messageId].orEmpty()).rsvpOf(userId) == PlanRsvp.GOING
    val fireAt = if (going) PlanReminderTiming.fireAt(plan.startsAtEpochMs, nowMs()) else null
    if (fireAt == null) {
        LocalReminderScheduler.cancel(reminderId)
        return
    }
    val chatName = openChatDisplayName()
    val body =
        buildString {
            append("Starts at ${formatClockTime(plan.startsAtEpochMs)}")
            plan.placeName?.let { append(" · $it") }
            append(" with $chatName")
        }
    LocalReminderScheduler.schedule(
        LocalReminder(
            id = reminderId,
            fireAtEpochMs = fireAt,
            title = plan.title,
            body = body,
            target =
                LocalReminderTarget.Chat(
                    chatId =
                        state.chatDetails.chat.id
                            .orEmpty(),
                    connectionId = if (state.chatDetails.groupClique == null) state.chatDetails.connection.id else "",
                ),
        ),
    )
}

/** Re-applies reminders for every upcoming plan in the open chat (idempotent: same id replaces). */
internal fun ChatViewModel.syncAllPlanRemindersImpl() {
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val now = nowMs()
    state.messages
        .filter { it.message.planOrNull()?.isOver(now) == false }
        .forEach { syncPlanReminderImpl(it.message.id) }
}
