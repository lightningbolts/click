@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.toChatDisplayImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/*
 * The Home shared-drops strip (sheet 07 G2-3, G2-6, G2-9; iOS `SharedDropsStrip.swift`). A bounded
 * strip, never a feed: one tile per person (yours, then unwatched, then the rest) after a share tile.
 * Tiles paint from the disk cache, and keyed items keep their state while scrolling.
 */

private val TileWidth = 84.dp
private val TileHeight = 112.dp
private val TileShape = RoundedCornerShape(16.dp)

/** Decodes [bytes] off the main thread, once per byte array. */
@Composable
fun rememberDecodedBitmap(
    bytes: ByteArray?,
    maxEdgePx: Int = 720,
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let { withContext(Dispatchers.Default) { runCatching { it.toChatDisplayImageBitmap(maxEdgePx) }.getOrNull() } }
    }
    return bitmap
}

@Composable
fun SharedDropsStrip(
    groups: List<SharedDropGroup>,
    previews: Map<String, ByteArray>,
    originals: Map<String, ByteArray>,
    now: Instant,
    onShare: () -> Unit,
    onOpen: (SharedDropGroup) -> Unit,
    onViewAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onViewAll != null) Modifier.clickable(onClick = onViewAll) else Modifier)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Click Drops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onViewAll != null) {
                Text("View all", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            item(key = "share") { ShareTile(onShare) }
            items(groups, key = { it.userId }) { group ->
                PersonTile(group, previews, originals, now) { onOpen(group) }
            }
        }
    }
}

@Composable
private fun ShareTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(TileWidth, TileHeight),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(PrimaryBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Share a drop", style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun PersonTile(
    group: SharedDropGroup,
    previews: Map<String, ByteArray>,
    originals: Map<String, ByteArray>,
    now: Instant,
    onClick: () -> Unit,
) {
    val cover = group.cover(now)
    val state = cover.state(now)
    val unwatched = group.hasUnwatched(now)
    val name = if (group.isMine) "You" else cover.user.firstName
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(TileWidth)) {
        Box(
            Modifier
                .size(TileWidth, TileHeight)
                .clip(TileShape)
                .then(if (unwatched) Modifier.border(BorderStroke(2.5.dp, PrimaryBlue), TileShape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .semantics { contentDescription = sharedDropTileLabel(name, state, now) },
            contentAlignment = Alignment.Center,
        ) {
            SharedDropFace(cover, state, previews[cover.id], originals[cover.id], Modifier.fillMaxSize())
            clickDropCountdownLabel(state, now)?.let { label ->
                Text(
                    label.removePrefix("Develops "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One drop's face: the original once developed, else its pixelated teaser. */
@Composable
fun SharedDropFace(
    drop: SharedDrop,
    state: ClickDropDevelopState,
    preview: ByteArray?,
    original: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val photo = rememberDecodedBitmap(if (state == ClickDropDevelopState.Developed) original else null)
    val teaser = rememberDecodedBitmap(preview, 480)
    when {
        photo != null -> Image(photo, "Click Drop from ${drop.user.firstName}", modifier, contentScale = ContentScale.Crop)
        teaser != null -> ClickDropPixelatedImage(teaser, modifier)
        else -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

internal fun sharedDropTileLabel(
    name: String,
    state: ClickDropDevelopState,
    now: Instant = Clock.System.now(),
): String =
    when (state) {
        is ClickDropDevelopState.Pending -> "$name's drop. ${clickDropCountdownLabel(state, now)}"
        ClickDropDevelopState.Ready -> "$name's drop, ready to develop"
        ClickDropDevelopState.Developed -> "$name's drop"
    }
