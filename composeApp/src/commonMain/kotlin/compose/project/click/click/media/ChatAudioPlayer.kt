package compose.project.click.click.media

import androidx.compose.runtime.Composable

interface ChatAudioPlayer {
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    fun togglePlayPause()
    fun dispose()
}

@Composable
expect fun rememberChatAudioPlayer(mediaUrl: String): ChatAudioPlayer
