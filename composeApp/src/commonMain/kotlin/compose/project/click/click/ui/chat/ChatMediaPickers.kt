package compose.project.click.click.ui.chat

import androidx.compose.runtime.Composable

/**
 * Attachment actions from the chat composer (+ menu).
 * [onImagePicked] is used for gallery + camera JPEG; [onAudioPicked] for voice and file-based audio.
 */
data class ChatMediaPickerHandles(
    val openPhotoLibrary: () -> Unit,
    val openCamera: () -> Unit,
    val openVoiceRecorder: () -> Unit,
)

@Composable
expect fun rememberChatMediaPickers(
    onImagePicked: (ByteArray, String) -> Unit,
    onAudioPicked: (ByteArray, String, Long?) -> Unit,
): ChatMediaPickerHandles
