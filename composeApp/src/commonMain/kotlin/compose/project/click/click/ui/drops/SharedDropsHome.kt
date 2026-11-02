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
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.viewmodel.SharedDropsViewModel
import compose.project.click.click.viewmodel.sharedDropsNextTickMs
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/*
 * Shared drops on Home, behind `shared_drops` (iOS `Click/Features/Home/SharedDropsStrip.swift` and
 * `SharedDropStorySource.swift`): the strip, the share flow and the story viewer.
 */

private data class StoryRequest(
    val groups: List<SharedDropGroup>,
    val groupIndex: Int,
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
    // Re-resolves states each minute and the moment the next drop reveals.
    val now by produceState(Clock.System.now(), state.drops) {
        while (true) {
            value = Clock.System.now()
            delay(sharedDropsNextTickMs(state.drops, value))
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
        posting = state.posting,
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
            startDropId = null,
            viewModel = viewModel,
            onDismiss = { story = null },
        )
    }
}
