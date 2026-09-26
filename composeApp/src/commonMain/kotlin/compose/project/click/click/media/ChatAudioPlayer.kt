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

@Composable
expect fun rememberChatAudioPlayer(
    mediaUrl: String,
    /** When set, the player reads this local decrypted file instead of [mediaUrl]. */
    localFilePathForPlayback: String? = null,
): ChatAudioPlayer
