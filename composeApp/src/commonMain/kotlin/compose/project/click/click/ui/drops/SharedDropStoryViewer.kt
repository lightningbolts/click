@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.platform.rememberReduceMotionEnabled
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace
import compose.project.click.click.viewmodel.SharedDropsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The shared-drops story viewer (sheet 07 G2-4, G2-4+, G2-6; iOS `SharedDropStoryViewer.swift`):
 * progress segments, tap left/right, hold to pause, swipe down to close, a horizontal pager between
 * people (the cube transition is P2). Every page reserves the same footer — the reaction tray over a
 * one-line reply — so the photo never shifts. No likes, views or counts.
 */

private const val STORY_DROP_MS = 5_000

@Composable
fun SharedDropStoryViewer(
    groups: List<SharedDropGroup>,
    startGroup: Int,
    startDropId: String?,
    viewModel: SharedDropsViewModel,
    onDismiss: () -> Unit,
) {
    if (groups.isEmpty()) return
    val pager = rememberPagerState(initialPage = startGroup.coerceIn(0, groups.lastIndex)) { groups.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), key = { groups[it].userId }) { page ->
            StoryPage(
                group = groups[page],
                isCurrent = pager.currentPage == page,
                startDropId = if (page == startGroup) startDropId else null,
                viewModel = viewModel,
                onNextGroup = { if (page < groups.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) } else onDismiss() },
                onPreviousGroup = { if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } },
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun StoryPage(
    group: SharedDropGroup,
    isCurrent: Boolean,
    startDropId: String?,
    viewModel: SharedDropsViewModel,
    onNextGroup: () -> Unit,
    onPreviousGroup: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val originals by viewModel.originals.collectAsState()
    val previews by viewModel.previews.collectAsState()
    // The order is snapshotted when the page opens, so watching never reshuffles what plays next.
    val ids = remember(group.userId) { group.viewable().map { it.id } }
    if (ids.isEmpty()) return
    var index by remember(group.userId) {
        mutableIntStateOf(ids.indexOf(startDropId ?: group.start()?.id).coerceAtLeast(0))
    }
    val drop = (state.drops + state.archive).firstOrNull { it.id == ids[index] } ?: group.drops.first { it.id == ids[index] }
    val dropState = drop.state()
    val original = originals[drop.id]
    val reduceMotion = rememberReduceMotionEnabled()
    val focus = LocalFocusManager.current

    var holding by remember { mutableStateOf(false) }
    var presenting by remember { mutableStateOf(false) }
    var replyFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dragY by remember { mutableStateOf(0f) }
    var reply by remember(drop.id) { mutableStateOf("") }
    val progress = remember(drop.id) { Animatable(0f) }
    // Drops developed while you watch bloom in once.
    val developsHere = remember(drop.id) { dropState == ClickDropDevelopState.Ready }
    var bloomed by remember(drop.id) { mutableStateOf(false) }

    fun next() {
        if (index < ids.lastIndex) index++ else onNextGroup()
    }

    fun previous() {
        if (index > 0) index-- else onPreviousGroup()
    }

    LaunchedEffect(drop.id, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        viewModel.warmImages(drop)
        if (dropState == ClickDropDevelopState.Ready) viewModel.develop(drop)
        if (drop.reactions == null && dropState == ClickDropDevelopState.Developed) viewModel.loadReactions(drop.id)
    }
    // The story timer runs only while the photo is up and nothing is holding it.
    val paused = holding || presenting || replyFocused || menuOpen || confirmDelete || !isCurrent || original == null
    LaunchedEffect(drop.id, paused) {
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * STORY_DROP_MS).roundToInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    val photo = rememberDecodedBitmap(if (dropState == ClickDropDevelopState.Developed) original else null, 1600)
    val teaser = rememberDecodedBitmap(previews[drop.id], 480)
    val density = LocalDensity.current

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Progress segments.
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ids.forEachIndexed { i, _ ->
                val fill = if (i < index) 1f else if (i == index) progress.value else 0f
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ConnectionListUserAvatarFace(drop.user.name, null, drop.user.avatarUrl, drop.userId, Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (drop.isMine) "You" else drop.user.firstName, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (drop.isMine) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                    } else {
                        DropdownMenuItem(text = { Text("Report") }, onClick = {
                            menuOpen = false
                            viewModel.report(drop, "Reported from the story viewer")
                        })
                    }
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.DarkGray)
                .pointerInput(drop.id) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            tryAwaitRelease()
                            holding = false
                        },
                        onTap = { offset ->
                            if (replyFocused) focus.clearFocus() else if (offset.x < size.width / 3f) previous() else next()
                        },
                    )
                }.pointerInput(drop.id) {
                    detectVerticalDragGestures(
                        onDragStart = { holding = true },
                        onDragEnd = {
                            holding = false
                            if (dragY > with(density) { 140.dp.toPx() }) onClose() else dragY = 0f
                        },
                        onDragCancel = {
                            holding = false
                            dragY = 0f
                        },
                    ) { _, amount -> if (!replyFocused) dragY = (dragY + amount).coerceAtLeast(0f) }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null && developsHere && !bloomed ->
                    ClickDropDevelopEffect(photo, reduceMotion, Modifier.fillMaxSize()) { bloomed = true }
                photo != null -> Image(photo, "Click Drop from ${drop.user.firstName}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                teaser != null -> ClickDropPixelatedImage(teaser, Modifier.fillMaxSize())
            }
            if (photo == null) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
            val caption = drop.caption?.takeIf { dropState == ClickDropDevelopState.Developed || drop.isMine }
            if (caption != null) {
                Text(
                    caption,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        // The same footer on every page: the tray over a one-line reply (or your reactors).
        ReactionBar(
            state = drop.reactions,
            isOwner = drop.isMine,
            onReact = { viewModel.react(drop, it) },
            reactable = dropState == ClickDropDevelopState.Developed,
            composing = replyFocused,
            onPresentingChange = { presenting = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (drop.connectionId != null) {
                val send = {
                    viewModel.sendReply(drop, reply)
                    reply = ""
                    focus.clearFocus()
                }
                TextField(
                    value = reply,
                    onValueChange = { reply = it.replace("\n", "") },
                    singleLine = true,
                    enabled = dropState == ClickDropDevelopState.Developed,
                    placeholder = { Text("Reply to ${drop.user.firstName}…", color = Color.White.copy(alpha = 0.6f)) },
                    trailingIcon = {
                        if (reply.isNotBlank()) IconButton(onClick = send) { Icon(Icons.AutoMirrored.Filled.Send, "Send reply", tint = Color.White) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    shape = RoundedCornerShape(50),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { replyFocused = it.isFocused },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this drop?") },
            text = { Text("It's removed for everyone it was shared with.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(drop)
                    next()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The single drop a tile, the archive or a push opens on: its group and its index in the strip. */
fun storyStart(
    groups: List<SharedDropGroup>,
    dropId: String,
): Pair<Int, String>? {
    val index = groups.indexOfFirst { g -> g.drops.any { it.id == dropId } }
    return if (index < 0) null else index to dropId
}

internal fun SharedDrop.isViewable(): Boolean = !state().isPending
