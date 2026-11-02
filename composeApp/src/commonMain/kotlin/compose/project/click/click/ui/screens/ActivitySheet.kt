@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.activity.ActivityGroup
import compose.project.click.click.data.activity.ActivityItemDto
import compose.project.click.click.data.activity.ActivityStore
import compose.project.click.click.data.activity.groupActivity
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.notifications.PushRoutes
import compose.project.click.click.notifications.openPushRouteInApp
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.util.formatWeekdayDate // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** The Home bell (iOS `ActivityBellButton`): a dot while anything is newer than the seen mark. */
@Composable
internal fun ActivityBellButton(onClick: () -> Unit) {
    val state by ActivityStore.state.collectAsState()
    val unseen = state.unseenCount
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = if (unseen > 0) "Activity, $unseen new" else "Activity" },
    ) {
        Box {
            Icon(if (unseen > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications, contentDescription = null)
            if (unseen > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(9.dp).align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
                ) {}
            }
        }
    }
}

/** Activity (iOS `ActivityView`): grouped New / Today / Yesterday / This week / Earlier. */
@Composable
internal fun ActivitySheet(
    onDismiss: () -> Unit,
    api: ApiClient = remember { ApiClient() },
) {
    val scope = rememberCoroutineScope()
    val state by ActivityStore.state.collectAsState()
    var answering by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { ActivityStore.refresh(api) }
    // Shown means seen: clear the dot once the newest page is on screen.
    LaunchedEffect(state.loadedOnce, state.items.firstOrNull()?.id) {
        if (state.loadedOnce) ActivityStore.markSeen(api)
    }
    val now = Clock.System.now().toEpochMilliseconds()
    // Group by the seen mark from when the sheet opened, so "New" doesn't vanish the moment it's marked.
    val openedSeenAt = remember(state.loadedOnce) { state.seenAt }
    val groups = remember(state.items, openedSeenAt) { groupActivity(state.items, openedSeenAt, now) }

    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when {
                !state.loadedOnce && state.loading ->
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                state.failed ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load your activity", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { scope.launch { ActivityStore.refresh(api) } }) { Text("Try Again") }
                    }
                state.items.isEmpty() ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Nothing yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reactions, RSVPs, new Clicks and friend requests show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
                        groups.forEach { (group, items) ->
                            item(key = "h-${group.name}") { ActivityGroupHeader(group) }
                            items(items, key = { it.id }) { item ->
                                val requestId = item.requestConnectionId?.takeIf { it in state.pendingRequests }
                                ActivityRow(
                                    item = item,
                                    nowEpochMs = now,
                                    pendingRequestId = requestId,
                                    answering = requestId != null && requestId in answering,
                                    onOpen = {
                                        if (openPushRouteInApp(PushRoutes.route(item.data + ("type" to item.type)))) onDismiss()
                                    },
                                    onAnswer = { accept ->
                                        val id = requestId ?: return@ActivityRow
                                        answering = answering + id
                                        scope.launch {
                                            api.respondPriorConnection(id, if (accept) "accept" else "decline").onSuccess {
                                                ActivityStore.requestAnswered(id)
                                            }
                                            answering = answering - id
                                        }
                                    },
                                )
                            }
                        }
                        if (state.nextBefore != null) {
                            item(key = "more") {
                                TextButton(
                                    onClick = { scope.launch { ActivityStore.loadMore(api) } },
                                    enabled = !state.loading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (state.loading) "Loading…" else "Show older") }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ActivityGroupHeader(group: ActivityGroup) {
    Text(
        group.title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActivityRow(
    item: ActivityItemDto,
    nowEpochMs: Long,
    pendingRequestId: String?,
    answering: Boolean,
    onOpen: () -> Unit,
    onAnswer: (accept: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val actor = item.actor
        if (actor != null) {
            ConnectionListUserAvatarFace(
                displayName = actor.name,
                email = null,
                avatarUrl = actor.avatarUrl,
                userId = actor.id,
                modifier = Modifier.size(40.dp),
                useCompactTypography = true,
            )
        } else {
            Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(40.dp).padding(8.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (item.title.isNotBlank()) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.body.isNotBlank()) {
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.createdAtMs?.let {
                Text(
                    activityTimeLabel(it, nowEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pendingRequestId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = { onAnswer(true) }, enabled = !answering) { Text("Accept") }
                    OutlinedButton(onClick = { onAnswer(false) }, enabled = !answering) { Text("Ignore") }
                }
            }
        }
    }
}

/** "5m", "3h", then the date. */
internal fun activityTimeLabel(
    epochMs: Long,
    nowEpochMs: Long,
): String {
    val minutes = ((nowEpochMs - epochMs).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> formatWeekdayDate(epochMs)
    }
}
