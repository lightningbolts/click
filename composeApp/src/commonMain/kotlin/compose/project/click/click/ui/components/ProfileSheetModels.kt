@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import kotlinx.serialization.json.JsonElement

/** Immutable snapshot the sheet renders. Callers rebuild this when underlying data changes. */
data class ProfileSheetState(
    val displayName: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val statusBadge: ProfileSheetBadge? = null,
    val canNudge: Boolean = true,
    val timeline: List<ProfileSheetTimelineItem> = emptyList(),
    val media: List<ProfileSheetMedia> = emptyList(),
    val links: List<ProfileSheetLink> = emptyList(),
    val files: List<ProfileSheetFile> = emptyList(),
    /** Peer user id — when non-blank, Timeline subtab hydrates interests / encounters. */
    val userId: String? = null,
    /** Email for connection-list-matching avatar initials when [avatarUrl] is blank. */
    val email: String? = null,
    /** Viewer user id — needed to compute shared interests + mutual connection. */
    val viewerUserId: String? = null,
    /** Optional connection/chat id retained for callers that want contextual actions. */
    val connectionId: String? = null,
    val isGroup: Boolean = false,
    val groupMembers: List<User> = emptyList(),
    val groupCreatorId: String? = null,
    val onAddMember: (() -> Unit)? = null,
    val onRemoveMember: ((String) -> Unit)? = null,
    val onMemberClick: ((String) -> Unit)? = null,
    val onGroupAvatarUrlChanged: ((String) -> Unit)? = null,
    /**
     * All locally-decrypted chat messages with type metadata. Used to populate
     * the Media / Files / Links tabs from the local E2EE cache instead of making
     * a server round-trip (message content is encrypted on the wire).
     */
    val localMessages: List<ProfileSheetLocalMessage> = emptyList(),
)

/**
 * A locally-decrypted chat message carrying its [messageType] so the profile sheet
 * can populate Media / Files / Links tabs entirely from the local E2EE cache
 * without making a server round-trip (message content is end-to-end encrypted on
 * the wire, so the BFF cannot parse it).
 */
data class ProfileSheetLocalMessage(
    val id: String,
    val content: String,
    val messageType: String,
    val timestamp: String,
    val metadata: JsonElement? = null,
    /** Epoch millis for newest-first media ordering. */
    val sortEpochMs: Long = 0L,
)

data class ProfileSheetBadge(
    val label: String,
    /** 0xAARRGGBB packed color — rendered via [Color]`(value)`. */
    val tint: Color,
)

data class ProfileSheetTimelineItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val timestamp: String,
)

data class ProfileSheetMedia(
    val id: String,
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val mimeType: String? = null,
    val isEncrypted: Boolean = false,
    val mediaType: ProfileSheetMediaType = ProfileSheetMediaType.Image,
    val captionedAt: String? = null,
    /** Epoch millis for newest-first ordering (from message [time_created]). */
    val sortEpochMs: Long = 0L,
    /** Voice-note length from message metadata when available. */
    val durationSeconds: Int? = null,
    val isDisposableRoll: Boolean = false,
    val collaborationTtlIso: String? = null,
)

enum class ProfileSheetMediaType {
    Image,
    Audio,
}

private const val PROFILE_MEDIA_CACHE_MAX_ENTRIES = 180
private const val PROFILE_SIGNED_URL_CACHE_MAX_ENTRIES = 360

internal val profileMediaBitmapCache: LruMemoryCache<String, ImageBitmap> =
    LruMemoryCache(PROFILE_MEDIA_CACHE_MAX_ENTRIES)
internal val profileMediaAudioPathCache: LruMemoryCache<String, String> =
    LruMemoryCache(PROFILE_MEDIA_CACHE_MAX_ENTRIES)
internal val profileSignedUrlCache: LruMemoryCache<String, String> =
    LruMemoryCache(PROFILE_SIGNED_URL_CACHE_MAX_ENTRIES)

data class ProfileSheetLink(
    val id: String,
    val url: String,
    val title: String?,
    val timestamp: String,
)

data class ProfileSheetFile(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val timestamp: String,
    /** Signed/public URL fallback used when tuple decryption fields are unavailable. */
    val downloadUrl: String? = null,
    /** `chat-attachments` object path for encrypted file downloads. */
    val attachmentPath: String? = null,
    /** Base64 32-byte per-file master key from the `ccx:v1` envelope. */
    val attachmentKeyBase64: String? = null,
    /** Base64 SHA-256 checksum for plaintext integrity verification. */
    val attachmentSha256Base64: String? = null,
)

enum class ProfileSheetTab(
    val label: String,
    val icon: ImageVector,
) {
    Timeline("Timeline", Icons.Outlined.History),
    Media("Media", Icons.Outlined.Image),
    Links("Links", Icons.Outlined.Link),
    Files("Files", Icons.Outlined.AttachFile),
    Beacons("Beacons", Icons.Outlined.Place),
    Members("Members", Icons.Outlined.People),
}
