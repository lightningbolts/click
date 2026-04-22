package compose.project.click.click.media

import androidx.compose.runtime.Composable

interface ChatAudioPlayer {
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun dispose()
}

/**
 * @param durationHintMs optional length from message metadata; used on iOS when the player
 * does not expose duration immediately (slider and labels still work).
 */
@Composable
expect fun rememberChatAudioPlayer(
    mediaUrl: String,
    durationHintMs: Long = 0L,
    /** When set, the player reads this local decrypted file instead of [mediaUrl]. */
    localFilePathForPlayback: String? = null,
): ChatAudioPlayer
