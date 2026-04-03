@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberChatAudioPlayer(mediaUrl: String, durationHintMs: Long): ChatAudioPlayer {
    val player = remember(mediaUrl, durationHintMs) { IosChatAudioPlayer(mediaUrl, durationHintMs) }
    DisposableEffect(mediaUrl) {
        onDispose { player.dispose() }
    }
    return player
}

private class IosChatAudioPlayer(
    url: String,
    durationHintMs: Long,
) : ChatAudioPlayer {
    private val avPlayer: AVPlayer = AVPlayer()
    private val isPlayingState = mutableStateOf(false)
    private val positionMsState = mutableFloatStateOf(0f)
    private val durationMsState = mutableFloatStateOf(
        durationHintMs.coerceAtLeast(0L).toFloat().takeIf { it > 0f } ?: 0f
    )
    private var timeObserver: Any? = null

    override val isPlaying: Boolean get() = isPlayingState.value

    override val positionMs: Long get() = positionMsState.floatValue.toLong()

    override val durationMs: Long get() = durationMsState.floatValue.toLong()

    init {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            val item = AVPlayerItem(uRL = nsUrl)
            avPlayer.replaceCurrentItemWithPlayerItem(item)
        }
        val interval = CMTimeMakeWithSeconds(0.12, 600)
        timeObserver = avPlayer.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = dispatch_get_main_queue(),
        ) { _ ->
            refreshProgressFromPlayer()
        }
    }

    private fun refreshProgressFromPlayer() {
        val t = avPlayer.currentTime()
        val sec = CMTimeGetSeconds(t)
        if (!sec.isNaN() && sec.isFinite()) {
            positionMsState.floatValue = (sec * 1000.0).toFloat().coerceAtLeast(0f)
        }
    }

    override fun togglePlayPause() {
        if (isPlayingState.value) {
            avPlayer.pause()
            isPlayingState.value = false
        } else {
            avPlayer.play()
            isPlayingState.value = true
        }
        refreshProgressFromPlayer()
    }

    override fun seekTo(positionMs: Long) {
        val dur = durationMsState.floatValue.toDouble()
        val targetSec = if (dur > 0) {
            positionMs.coerceIn(0L, dur.toLong()) / 1000.0
        } else {
            positionMs.coerceAtLeast(0L) / 1000.0
        }
        val time = CMTimeMakeWithSeconds(targetSec, 1000)
        avPlayer.seekToTime(time) { _ ->
            refreshProgressFromPlayer()
        }
    }

    override fun dispose() {
        timeObserver?.let { avPlayer.removeTimeObserver(it) }
        timeObserver = null
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
    }
}
