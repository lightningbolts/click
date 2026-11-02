@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.sharedDropArchiveSections
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.SharedDropsViewModel
import compose.project.click.click.viewmodel.sharedDropsNextTickMs
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/*
 * Home's "View all" (iOS `Click/Features/Home/SharedDropsArchiveView.swift`): every shared drop you
 * can see, newest first, in a three-column photo grid under day headers, older ones included. The
 * next page loads well before the end, and a tile opens the story viewer, which plays on through
 * the archive in grid order.
 */

private val ArchiveTileShape = RoundedCornerShape(12.dp)

/** Tiles from the end at which the next page starts loading (about two screens). */
private const val ARCHIVE_PREFETCH_TILES = 18

@Composable
fun SharedDropsArchiveScreen(
    viewModel: SharedDropsViewModel,
    onOpen: (SharedDrop) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val previews by viewModel.previews.collectAsState()
    val originals by viewModel.originals.collectAsState()
    // Pending tiles turn ready right on time.
    val now by produceState(Clock.System.now(), state.archive) {
        while (true) {
            value = Clock.System.now()
            delay(sharedDropsNextTickMs(state.archive, value))
        }
    }
    val zone = remember { TimeZone.currentSystemDefault() }
    val sections = remember(state.archive, now) { sharedDropArchiveSections(state.archive, now, zone) }
    val indexById = remember(state.archive) { state.archive.withIndex().associate { (i, d) -> d.id to i } }

    LaunchedEffect(Unit) { viewModel.loadArchive() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Click Drops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        sections.forEach { section ->
                            item(key = "h:${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    section.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
                                )
                            }
                            items(section.drops, key = { it.id }) { drop ->
                                LaunchedEffect(drop.id, drop.state(now) == ClickDropDevelopState.Developed) {
                                    viewModel.warmImages(drop)
                                    val index = indexById[drop.id] ?: return@LaunchedEffect
                                    if (index >= state.archive.size - ARCHIVE_PREFETCH_TILES) viewModel.loadMoreArchive()
                                }
                                ArchiveTile(drop, previews[drop.id], originals[drop.id], now, drop.id in state.developing) { onOpen(drop) }
                            }
                        }
                        if (state.archiveNextBefore != null && state.archiveLoaded) {
                            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    if (state.archiveMoreFailed) {
                                        TextButton(onClick = viewModel::loadMoreArchive) { Text("Couldn't load more. Retry") }
                                    } else {
                                        CircularProgressIndicator(Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                    ArchivePlaceholder(
                        loaded = state.archiveLoaded,
                        empty = state.archive.isEmpty(),
                        error = state.archiveError,
                        onRetry = viewModel::loadArchive,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivePlaceholder(
    loaded: Boolean,
    empty: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val content: (@Composable () -> Unit)? =
        when {
            !loaded && error != null -> {
                {
                    Text("Couldn't load your drops", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRetry) { Text("Try Again") }
                }
            }
            !loaded -> {
                { CircularProgressIndicator() }
            }
            empty -> {
                {
                    Text("No drops yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Drops you and your connections share show up here, older ones included.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> null
        }
    if (content != null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) { content() }
    }
}

@Composable
private fun ArchiveTile(
    drop: SharedDrop,
    preview: ByteArray?,
    original: ByteArray?,
    now: kotlinx.datetime.Instant,
    developing: Boolean,
    onClick: () -> Unit,
) {
    val state = drop.state(now)
    val name = if (drop.isMine) "You" else drop.user.firstName
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(ArchiveTileShape)
            .background(Color.DarkGray)
            .then(
                if (state ==
                    ClickDropDevelopState.Ready
                ) {
                    Modifier.border(BorderStroke(2.dp, PrimaryBlue), ArchiveTileShape)
                } else {
                    Modifier
                },
            ).clickable(onClick = onClick)
            .semantics { contentDescription = sharedDropTileLabel(name, state, now) + ". Opens it." },
    ) {
        SharedDropFace(drop, state, preview, original, Modifier.fillMaxSize())
        when (state) {
            is ClickDropDevelopState.Pending ->
                clickDropCountdownLabel(state, now)?.let { label ->
                    Text(
                        label.removePrefix("Develops in "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            ClickDropDevelopState.Ready ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .background(PrimaryBlue, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (developing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            ClickDropDevelopState.Developed -> Unit
        }
        ConnectionListUserAvatarFace(
            drop.user.name,
            null,
            drop.user.avatarUrl,
            drop.userId,
            Modifier.align(Alignment.BottomStart).padding(5.dp).size(22.dp),
            useCompactTypography = true,
        )
    }
}
