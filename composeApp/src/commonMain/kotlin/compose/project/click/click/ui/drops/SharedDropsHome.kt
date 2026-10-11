@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.notifications.ChatDeepLinkManager
import compose.project.click.click.viewmodel.SharedDropsViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Shared drops on Home (sheet 07 G2-3..G2-9), behind `shared_drops`: the strip, the share flow,
 * the story viewer, "View all" and the release push's deep link.
 */

private data class StoryRequest(
    val groups: List<SharedDropGroup>,
    val groupIndex: Int,
    val dropId: String?,
)

@Composable
fun SharedDropsHomeSection(
    viewModel: SharedDropsViewModel,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val previews by viewModel.previews.collectAsState()
    val originals by viewModel.originals.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    var composing by rememberSaveable { mutableStateOf(false) }
    var showingArchive by rememberSaveable { mutableStateOf(false) }
    var story by remember { mutableStateOf<StoryRequest?>(null) }
    // Re-resolves states each minute and the moment the next drop reveals.
    val now by produceState(Clock.System.now(), state.drops) {
        while (true) {
            value = Clock.System.now()
            val nextReveal = state.drops.mapNotNull { d -> d.revealAt?.takeIf { it > value } }.minOrNull()
            delay(nextReveal?.let { (it - value).inWholeMilliseconds.coerceIn(500, 60_000) + 250 } ?: 60_000)
        }
    }
    val groups = remember(state.drops, now) { SharedDropGroup.group(state.drops, now) }

    LaunchedEffect(currentUser?.id) { if (currentUser?.id != null) viewModel.refresh() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            onToast(it)
            viewModel.clearNotice()
        }
    }
    // The release push opens that drop's story once the strip has it.
    val pendingDropId by ChatDeepLinkManager.pendingSharedDropId.collectAsState()
    LaunchedEffect(pendingDropId, state.drops) {
        val id = pendingDropId ?: return@LaunchedEffect
        if (state.drops.isEmpty()) {
            viewModel.refresh()
            return@LaunchedEffect
        }
        ChatDeepLinkManager.consumeSharedDrop()
        val drop = state.drops.firstOrNull { it.id == id }?.takeIf { !it.state().isPending } ?: return@LaunchedEffect
        val index = groups.indexOfFirst { g -> g.drops.any { it.id == drop.id } }
        if (index >= 0) story = StoryRequest(groups, index, drop.id)
    }

    SharedDropsStrip(
        groups = groups,
        previews = previews,
        originals = originals,
        now = now,
        onShare = { composing = true },
        onOpen = { group ->
            if (group.viewable(now).isEmpty()) {
                clickDropCountdownLabel(group.newest.state(now), now)?.let(onToast)
            } else {
                story = StoryRequest(groups, groups.indexOf(group), null)
            }
        },
        onViewAll = if (state.drops.isNotEmpty() || state.archive.isNotEmpty()) ({ showingArchive = true }) else null,
        modifier = modifier,
    )

    if (composing) {
        SharedDropComposer(
            onShare = { jpeg, audience, caption ->
                composing = false
                viewModel.share(jpeg, audience, caption)
            },
            onDismiss = { composing = false },
        )
    }
    state.postError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPostError,
            title = { Text("Couldn't share") },
            text = { Text(error) },
            confirmButton = {
                if (state.pendingPost != null) TextButton(onClick = viewModel::retryShare) { Text("Retry") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPostError) { Text("OK") } },
        )
    }
    story?.let { request ->
        SharedDropStoryViewer(
            groups = request.groups,
            startGroup = request.groupIndex,
            startDropId = request.dropId,
            viewModel = viewModel,
            onDismiss = { story = null },
        )
    }
    if (showingArchive) {
        SharedDropsArchive(
            viewModel = viewModel,
            onOpen = { drop -> story = StoryRequest(listOf(SharedDropGroup(drop.userId, listOf(drop))), 0, drop.id) },
            onDismiss = { showingArchive = false },
        )
    }
}

/** "View all": every drop you can see, a paged grid with day headers. */
@Composable
private fun SharedDropsArchive(
    viewModel: SharedDropsViewModel,
    onOpen: (SharedDrop) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val previews by viewModel.previews.collectAsState()
    val originals by viewModel.originals.collectAsState()
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) { viewModel.loadMoreArchive(reset = true) }
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 6 } }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) viewModel.loadMoreArchive() } }
    val byDay = remember(state.archive) { state.archive.groupBy { dayLabel(it.createdAt) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Click Drops", style = MaterialTheme.typography.titleMedium)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = grid,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                byDay.forEach { (day, drops) ->
                    item(key = "day-$day", span = { GridItemSpan(maxLineSpan) }) {
                        Text(day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(drops, key = { it.id }) { drop ->
                        val dropState = drop.state()
                        Box(
                            Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !dropState.isPending) { onOpen(drop) },
                        ) {
                            SharedDropFace(drop, dropState, previews[drop.id], originals[drop.id], Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(createdAt: String?): String {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "Earlier"
    val zone = TimeZone.currentSystemDefault()
    val date = instant.toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    return when {
        date == today -> "Today"
        date.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
        else -> "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}" + if (date.year != today.year) ", ${date.year}" else ""
    }
}
