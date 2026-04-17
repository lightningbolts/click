package compose.project.click.click.ui.chat

import androidx.compose.runtime.Composable

/**
 * Attachment actions from the chat composer (+ menu).
 * [onImagePicked] is used for gallery + camera JPEG; [onAudioPicked] for voice and file-based audio.
 * [onMediaAccessBlocked] surfaces permission denials and unreadable picks (snackbar/dialog from caller).
 */
data class ChatMediaPickerHandles(
    val openPhotoLibrary: () -> Unit,
    val openCamera: () -> Unit,
    val openVoiceRecorder: () -> Unit,
    /**
     * Open the platform document picker for arbitrary chat attachments (Phase 2 — C4).
     * Filters to [ChatAttachmentValidator.ALLOWED_MIME_TYPES] at the OS layer; we still
     * re-validate the result inside the ViewModel before encrypting & uploading.
     */
    val openFilePicker: () -> Unit,
)

@Composable
expect fun rememberChatMediaPickers(
    onImagePicked: (ByteArray, String) -> Unit,
    onAudioPicked: (ByteArray, String, Long?) -> Unit,
    onFilePicked: (PickedFile) -> Unit = {},
    onMediaAccessBlocked: (String) -> Unit = { _ -> },
): ChatMediaPickerHandles
