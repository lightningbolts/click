@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.collaboration.graphemeCount
import compose.project.click.click.data.api.ReactionsState
import compose.project.click.click.data.api.Reactor
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace

/*
 * Reactions on shared drops and soundtracks (G1-7, G2-4+; iOS `ReactionBar.swift`). Tap an emoji to
 * react, tap it again to take it back; "+" picks any other, which takes the last slot. Owners see
 * who reacted instead. The bar keeps one height with or without reactions, so the photo above it
 * never shifts.
 */

val ReactionTrayHeight = 50.dp

/**
 * @param reactable false until there's something to react to (a drop still developing): shown, not tappable.
 * @param composing while a reply is typed, the tray and faces step aside so only [accessory] shows.
 * @param onPresentingChange true while the picker or the reactors list is up, so a story can hold still.
 */
@Composable
fun ReactionBar(
    state: ReactionsState?,
    isOwner: Boolean,
    onReact: (String?) -> Unit,
    modifier: Modifier = Modifier,
    reactable: Boolean = true,
    composing: Boolean = false,
    onPresentingChange: (Boolean) -> Unit = {},
    accessory: @Composable () -> Unit = {},
) {
    var picking by remember { mutableStateOf(false) }
    var showingReactors by remember { mutableStateOf(false) }
    LaunchedEffect(picking, showingReactors) { onPresentingChange(picking || showingReactors) }
    val reactions = state?.reactions.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state?.isOwner ?: isOwner) {
            if (reactions.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(ReactionTrayHeight).alpha(if (state == null) 0f else 1f)) {
                    Icon(Icons.Filled.FavoriteBorder, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("No reactions yet", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                ReactorList(reactions)
            }
        } else {
            val trayAlpha by animateFloatAsState(if (composing) 0f else if (reactable) 1f else 0.45f)
            ReactionTray(
                mine = state?.mine,
                enabled = reactable && !composing,
                onPick = onReact,
                onMore = { picking = true },
                modifier = Modifier.alpha(trayAlpha),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { accessory() }
                if (reactions.isNotEmpty() && !composing) {
                    ReactorFaces(reactions, enabled = reactable) { showingReactors = true }
                }
            }
        }
    }
    if (picking) {
        EmojiPickerDialog(
            onPick = {
                picking = false
                onReact(it)
            },
            onDismiss = { picking = false },
        )
    }
    if (showingReactors) ReactorsListSheet(reactions) { showingReactors = false }
}

/** One glass tray of plain emoji; only your pick sits on a tinted disc. */
@Composable
private fun ReactionTray(
    mine: String?,
    enabled: Boolean,
    onPick: (String?) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val custom = mine?.takeIf { it !in ReactionsState.palette }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ReactionTrayHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReactionsState.palette.forEach { emoji ->
            val chosen = mine == emoji
            Slot(emoji, chosen, enabled, Modifier.weight(1f)) { onPick(if (chosen) null else emoji) }
        }
        if (custom != null) {
            Slot(custom, chosen = true, enabled = enabled, modifier = Modifier.weight(1f)) { onPick(null) }
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onMore)
                    .semantics { contentDescription = "More emoji" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun Slot(
    emoji: String,
    chosen: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (chosen) 1.1f else 1f)
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (chosen) {
                        Modifier
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    } else {
                        Modifier
                    },
                ).clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = "React $emoji"
                    selected = chosen
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 24.sp, modifier = Modifier.scale(scale))
    }
}

/** Who else reacted: a few overlapping 30 dp faces with their emoji; tapping opens the list. */
@Composable
private fun ReactorFaces(
    reactions: List<Reactor>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val faces = reactions.take(3)
    val emoji = reactions.map { it.emoji }.distinct().take(3).joinToString("")
    Row(
        modifier =
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(start = 4.dp, end = 10.dp)
                .semantics { contentDescription = "Reactions: " + reactions.joinToString(", ") { "${it.name} ${it.emoji}" } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            faces.forEach { r ->
                ConnectionListUserAvatarFace(
                    displayName = r.name,
                    email = null,
                    avatarUrl = r.avatarUrl,
                    userId = r.userId,
                    modifier = Modifier.size(30.dp),
                    useCompactTypography = true,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(emoji, fontSize = 15.sp)
    }
}

/** On your own drop: each person who reacted, a face with their emoji and a first name. */
@Composable
private fun ReactorList(reactions: List<Reactor>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(reactions, key = { it.userId }) { r ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Box {
                    ConnectionListUserAvatarFace(r.name, null, r.avatarUrl, r.userId, Modifier.size(44.dp))
                    Text(r.emoji, fontSize = 17.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(5.dp, 4.dp))
                }
                Spacer(Modifier.height(5.dp))
                Text(r.firstName, color = Color.White.copy(alpha = 0.75f), maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}

/** Everyone who reacted, newest first (face, first name, emoji). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactorsListSheet(
    reactions: List<Reactor>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Reactions", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(reactions, key = { it.userId }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConnectionListUserAvatarFace(r.name, null, r.avatarUrl, r.userId, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(r.firstName, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(r.emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

/** Any single emoji, typed from the system keyboard's emoji pane. */
@Composable
private fun EmojiPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val one = text.trim().takeIf { it.isNotEmpty() && graphemeCount(it) == 1 && it.none(Char::isLetterOrDigit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("React with any emoji") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.takeLast(16) },
                singleLine = true,
                placeholder = { Text("One emoji") },
            )
        },
        confirmButton = { TextButton(onClick = { one?.let(onPick) }, enabled = one != null) { Text("React") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
