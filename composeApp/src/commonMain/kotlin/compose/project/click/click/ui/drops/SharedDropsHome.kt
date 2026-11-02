@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.collaboration.archiveStoryGroup
import compose.project.click.click.collaboration.openableGroupIndex
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.deeplink.SharedDropOpenRequests
import compose.project.click.click.viewmodel.SharedDropsViewModel
import compose.project.click.click.viewmodel.sharedDropsNextTickMs
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/*
 * Shared drops on Home, behind `shared_drops` (iOS `Click/Features/Home/SharedDropsStrip.swift` and
 * `SharedDropStorySource.swift`): the strip, the share flow, "View all" and the story viewer. A
 * `shared_drop_released` push (or a reaction push) opens that drop's story once it's in the strip.
 */

private data class StoryRequest(
    val groups: List<SharedDropGroup>,
    val groupIndex: Int,
    val startDropId: String? = null,
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
    var story by remember { mutableStateOf<StoryRequest?>(null) }
    var showingArchive by rememberSaveable { mutableStateOf(false) }
    val pendingOpen by SharedDropOpenRequests.pending.collectAsState()
    // Re-resolves states each minute and the moment the next drop reveals.
    val now by produceState(Clock.System.now(), state.drops) {
        while (true) {
            value = Clock.System.now()
            delay(sharedDropsNextTickMs(state.drops, value))
        }
    }
    val groups = remember(state.drops, now) { SharedDropGroup.group(state.drops, now) }

    LaunchedEffect(currentUser?.id) { if (currentUser?.id != null) viewModel.refresh() }
    // A push for a drop that isn't in the strip yet: fetch the live strip, which has it.
    LaunchedEffect(pendingOpen) { if (pendingOpen != null && currentUser?.id != null) viewModel.refresh() }
    LaunchedEffect(pendingOpen, state.drops, story) {
        val id = pendingOpen ?: return@LaunchedEffect
        if (story != null) return@LaunchedEffect
        val request = storyRequestFor(id, groups, now) ?: return@LaunchedEffect
        SharedDropOpenRequests.consume(id)
        story = request
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            onToast(it)
            viewModel.clearNotice()
        }
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
                story = StoryRequest(groups, groups.indexOf(group))
            }
        },
        modifier = modifier,
        onViewAll = { showingArchive = true }.takeIf { state.drops.isNotEmpty() || state.archive.isNotEmpty() },
        posting = state.posting,
    )

    if (showingArchive) {
        SharedDropsArchiveScreen(
            viewModel = viewModel,
            onOpen = { drop ->
                val opened = Clock.System.now()
                if (drop.state(opened).isPending) {
                    clickDropCountdownLabel(drop.state(opened), opened)?.let(onToast)
                } else {
                    story = StoryRequest(listOf(archiveStoryGroup(state.archive)), 0, drop.id)
                }
            },
            onDismiss = { showingArchive = false },
        )
    }

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
            startDropId = request.startDropId,
            viewModel = viewModel,
            onDismiss = { story = null },
        )
    }
}

private fun storyRequestFor(
    dropId: String,
    groups: List<SharedDropGroup>,
    now: kotlinx.datetime.Instant,
): StoryRequest? = openableGroupIndex(dropId, groups, now)?.let { StoryRequest(groups, it, dropId) }
