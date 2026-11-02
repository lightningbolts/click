@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.api.SharedDropsApi
import compose.project.click.click.data.models.ChatDropReply
import compose.project.click.click.ui.drops.rememberDecodedBitmap
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.writeChatMediaVaultFile
import compose.project.click.click.viewmodel.sharedDropOriginalKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Replied to your drop" above a story reply (iOS `Click/Features/Chat/DropReplyViews.swift`,
 * `DropReplyHeader`). The drop is referenced by ID only: its photo comes from this device's shared
 * drop cache, else once from the server (which checks access); a deleted or no-longer-shared drop
 * shows as unavailable, and that answer is remembered so scrolling doesn't ask again.
 */
@Composable
internal fun ChatDropReplyQuote(
    reply: ChatDropReply,
    mine: Boolean,
    maxWidth: Dp,
) {
    val photoBytes by produceState<DropReplyPhoto>(DropReplyPhoto.Loading, reply.dropId) {
        value = withContext(Dispatchers.Default) { loadDropReplyPhoto(reply.dropId) }
    }
    val photo = rememberDecodedBitmap((photoBytes as? DropReplyPhoto.Loaded)?.bytes, 256)
    val secondary = if (mine) Color.White.copy(alpha = 0.75f) else Color.Unspecified
    val label = reply.label(mine)
    Row(
        modifier =
            Modifier
                .widthIn(max = maxWidth)
                .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(6.dp)
                .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, null, Modifier.size(48.dp, 64.dp), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = chatBubbleReplyLabelStyle(), color = secondary)
            if (photoBytes == DropReplyPhoto.Missing) {
                Text("Drop no longer available", style = chatBubbleReplySnippetStyle(), color = secondary)
            }
        }
    }
}

private sealed interface DropReplyPhoto {
    data object Loading : DropReplyPhoto

    data object Missing : DropReplyPhoto

    class Loaded(
        val bytes: ByteArray,
    ) : DropReplyPhoto
}

/** Drops a reply points at that can't be loaded, so each bubble doesn't ask the server again. */
private val missingDropReplies = mutableSetOf<String>()
private val missingDropRepliesLock = Mutex()

/** The strip's disk copy, then (once) the server: developing again is idempotent and signs a fresh URL. */
private suspend fun loadDropReplyPhoto(dropId: String): DropReplyPhoto {
    val uid = AppDataManager.currentUser.value?.id ?: return DropReplyPhoto.Missing
    val key = sharedDropOriginalKey(uid, dropId)
    readChatMediaVaultBytes(key, "jpg")?.takeIf { it.isNotEmpty() }?.let { return DropReplyPhoto.Loaded(it) }
    if (missingDropRepliesLock.withLock { dropId in missingDropReplies }) return DropReplyPhoto.Missing
    return try {
        val api = SharedDropsApi()
        val url = api.develop(dropId).getOrNull()?.url
        val bytes = url?.let { api.download(it).getOrNull() }?.takeIf { it.isNotEmpty() }
        if (bytes == null) {
            missingDropRepliesLock.withLock { missingDropReplies += dropId }
            DropReplyPhoto.Missing
        } else {
            writeChatMediaVaultFile(key, bytes, "jpg")
            DropReplyPhoto.Loaded(bytes)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        DropReplyPhoto.Missing
    }
}
