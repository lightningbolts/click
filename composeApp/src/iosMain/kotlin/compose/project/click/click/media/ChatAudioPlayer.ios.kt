@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

@Composable
actual fun rememberChatAudioPlayer(mediaUrl: String): ChatAudioPlayer {
    val player = remember(mediaUrl) { IosChatAudioPlayer(mediaUrl) }
    DisposableEffect(mediaUrl) {
        onDispose { player.dispose() }
    }
    return player
}

private class IosChatAudioPlayer(url: String) : ChatAudioPlayer {
    private val avPlayer: AVPlayer = AVPlayer()
    private val isPlayingState = mutableStateOf(false)
    override val isPlaying: Boolean get() = isPlayingState.value

    init {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            val item = AVPlayerItem(uRL = nsUrl)
            avPlayer.replaceCurrentItemWithPlayerItem(item)
        }
    }

    override val positionMs: Long get() = 0L

    override val durationMs: Long get() = 0L

    override fun togglePlayPause() {
        if (isPlayingState.value) {
            avPlayer.pause()
            isPlayingState.value = false
        } else {
            avPlayer.play()
            isPlayingState.value = true
        }
    }

    override fun dispose() {
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
    }
}
