@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/**
 * Shared interaction state for every chat surface (1:1, group, proximity hub, event hub).
 *
 * Keeping keyboard dismissal, timeline scroll ownership and interactive-back IME teardown here
 * prevents the individual chat screens from drifting into subtly different behavior.
 */
internal data class ChatThreadRuntime(
    val listState: LazyListState,
    val dismissKeyboardOnUserMessageScroll: NestedScrollConnection,
    val suppressKeyboardDismissWhileProgrammaticTimelineScroll: MutableState<Boolean>,
    val initialTimelineScrollDoneState: MutableState<Boolean>,
    val focusedSearchMessageIdState: MutableState<String?>,
)

@Composable
internal fun rememberChatThreadRuntime(
    threadKey: String,
    parentInteractiveBackSwipePx: MutableFloatState?,
): ChatThreadRuntime {
    val listState = remember(threadKey) { LazyListState() }
    val density = LocalDensity.current
    val focusManagerState = rememberUpdatedState(LocalFocusManager.current)
    val keyboardControllerState = rememberUpdatedState(LocalSoftwareKeyboardController.current)
    val suppressKeyboardDismissWhileProgrammaticTimelineScroll =
        remember(threadKey) { mutableStateOf(false) }
    val initialTimelineScrollDoneState = remember(threadKey) { mutableStateOf(false) }
    val focusedSearchMessageIdState = remember(threadKey) { mutableStateOf<String?>(null) }
    val keyboardDismissScrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val dismissKeyboardOnUserMessageScroll =
        remember(keyboardDismissScrollThresholdPx, threadKey) {
            chatDismissKeyboardAfterScrollConnection(
                thresholdPx = keyboardDismissScrollThresholdPx,
                isSuppressed = { suppressKeyboardDismissWhileProgrammaticTimelineScroll.value },
                onDismiss = { focusManagerState.value.clearFocus() },
            )
        }

    LaunchedEffect(parentInteractiveBackSwipePx, threadKey, density) {
        val swipe = parentInteractiveBackSwipePx ?: return@LaunchedEffect
        var imeClearedForInteractiveBackSwipe = false
        val commitPx = with(density) { 120.dp.toPx() }
        snapshotFlow { swipe.floatValue }.collect { offset ->
            when {
                offset > commitPx && !imeClearedForInteractiveBackSwipe -> {
                    imeClearedForInteractiveBackSwipe = true
                    keyboardControllerState.value?.hide()
                    focusManagerState.value.clearFocus()
                }
                offset <= 0f -> imeClearedForInteractiveBackSwipe = false
            }
        }
    }

    return remember(
        threadKey,
        listState,
        dismissKeyboardOnUserMessageScroll,
        suppressKeyboardDismissWhileProgrammaticTimelineScroll,
        initialTimelineScrollDoneState,
        focusedSearchMessageIdState,
    ) {
        ChatThreadRuntime(
            listState = listState,
            dismissKeyboardOnUserMessageScroll = dismissKeyboardOnUserMessageScroll,
            suppressKeyboardDismissWhileProgrammaticTimelineScroll =
            suppressKeyboardDismissWhileProgrammaticTimelineScroll,
            initialTimelineScrollDoneState = initialTimelineScrollDoneState,
            focusedSearchMessageIdState = focusedSearchMessageIdState,
        )
    }
}

/**
 * Shared newest-edge behavior for every chat backend.
 *
 * The backend-specific screen only supplies how to hydrate a requested target message; open and
 * inbound-follow semantics stay identical across 1:1, group and hub chats.
 */
@Composable
internal fun ChatThreadAutoFollowEffects(
    threadKey: String,
    hasMessages: Boolean,
    targetMessageId: String?,
    peerNewestMessageId: String?,
    runtime: ChatThreadRuntime,
    ensureTargetMessageLoaded: suspend (String) -> Boolean,
) {
    LaunchedEffect(threadKey, hasMessages, targetMessageId) {
        if (!hasMessages || runtime.initialTimelineScrollDoneState.value) return@LaunchedEffect
        if (!targetMessageId.isNullOrBlank()) {
            val found = ensureTargetMessageLoaded(targetMessageId)
            if (!found) {
                runtime.initialTimelineScrollDoneState.value = true
                scrollChatTimelineToLatest(
                    listState = runtime.listState,
                    suppressKeyboardDismiss =
                        runtime.suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                )
            }
            return@LaunchedEffect
        }
        runtime.initialTimelineScrollDoneState.value = true
        scrollChatTimelineToLatest(
            listState = runtime.listState,
            suppressKeyboardDismiss = runtime.suppressKeyboardDismissWhileProgrammaticTimelineScroll,
        )
    }

    LaunchedEffect(peerNewestMessageId) {
        if (peerNewestMessageId == null) return@LaunchedEffect
        if (
            chatTimelineShouldFollowInbound(
                firstVisibleItemIndex = runtime.listState.firstVisibleItemIndex,
                initialTimelineScrollDone = runtime.initialTimelineScrollDoneState.value,
            )
        ) {
            scrollChatTimelineToLatest(
                listState = runtime.listState,
                suppressKeyboardDismiss = runtime.suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                animated = chatTimelineFollowUsesAnimation(runtime.initialTimelineScrollDoneState.value),
            )
        }
    }
}
