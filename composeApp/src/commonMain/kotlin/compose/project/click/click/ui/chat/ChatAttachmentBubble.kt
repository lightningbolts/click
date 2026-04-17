package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * File attachment bubble (Phase 2 — C6). Renders the envelope metadata and drives the
 * download → decrypt → integrity-verify → save pipeline via [onDownload], which is wired
 * to `ChatRepository.downloadAttachmentPlaintext` + `saveDecryptedAttachmentToDownloads`
 * by the caller (`ChatViewModel.downloadChatAttachment`).
 */
@Composable
fun ChatAttachmentBubble(
    envelope: AttachmentCrypto.Envelope,
    isSent: Boolean,
    onDownload: suspend () -> ChatAttachmentDownloadOutcome,
) {
    val scope = rememberCoroutineScope()
    var state: ChatAttachmentUiState by remember(envelope.path) {
        mutableStateOf(ChatAttachmentUiState.Idle)
    }

    val bubbleShape = if (isSent) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }
    val container = if (isSent) {
        PrimaryBlue.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    }
    val borderColor = if (isSent) PrimaryBlue.copy(alpha = 0.35f) else PrimaryBlue.copy(alpha = 0.18f)
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(bubbleShape)
            .background(container)
            .border(width = 1.dp, color = borderColor, shape = bubbleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForMime(envelope.mime),
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = envelope.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(formatBytes(envelope.size))
                        if (envelope.mime.isNotBlank()) {
                            append(" · ")
                            append(envelope.mime)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (val s = state) {
                    ChatAttachmentUiState.Idle, ChatAttachmentUiState.Running -> Unit
                    is ChatAttachmentUiState.Done -> {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Saved · integrity verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                    is ChatAttachmentUiState.Error -> {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            when (state) {
                ChatAttachmentUiState.Running -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                    )
                }
                else -> {
                    IconButton(
                        onClick = {
                            if (state is ChatAttachmentUiState.Running) return@IconButton
                            state = ChatAttachmentUiState.Running
                            scope.launch {
                                state = when (val result = onDownload()) {
                                    is ChatAttachmentDownloadOutcome.Success ->
                                        ChatAttachmentUiState.Done(result.savedPath)
                                    is ChatAttachmentDownloadOutcome.Failure ->
                                        ChatAttachmentUiState.Error(result.message)
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download attachment",
                            tint = PrimaryBlue,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(envelope.path) {
        state = ChatAttachmentUiState.Idle
    }
}

sealed class ChatAttachmentDownloadOutcome {
    data class Success(val savedPath: String) : ChatAttachmentDownloadOutcome()
    data class Failure(val message: String) : ChatAttachmentDownloadOutcome()
}

private sealed class ChatAttachmentUiState {
    object Idle : ChatAttachmentUiState()
    object Running : ChatAttachmentUiState()
    data class Done(val savedPath: String) : ChatAttachmentUiState()
    data class Error(val message: String) : ChatAttachmentUiState()
}

private fun iconForMime(mime: String): ImageVector {
    val m = mime.lowercase()
    return when {
        m.startsWith("image/") -> Icons.Outlined.Image
        m.startsWith("video/") -> Icons.Outlined.Movie
        m == "application/pdf" -> Icons.Outlined.PictureAsPdf
        m == "text/csv" || m == "application/csv" -> Icons.Outlined.TableChart
        m.startsWith("text/") -> Icons.Outlined.Article
        else -> Icons.Outlined.FilePresent
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val unit = 1024.0
    val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(0, 4)
    val suffixes = arrayOf("B", "KB", "MB", "GB", "TB")
    val scaled = bytes / unit.pow(exp.toDouble())
    val rounded = if (scaled >= 10.0 || abs(scaled - scaled.toLong()) < 0.05) {
        scaled.toLong().toString()
    } else {
        ((scaled * 10.0).toLong() / 10.0).toString()
    }
    return "$rounded ${suffixes[exp]}"
}
