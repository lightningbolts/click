@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
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
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberChatAudioPlayer(
    mediaUrl: String,
    durationHintMs: Long,
    localFilePathForPlayback: String?,
): ChatAudioPlayer {
    val resolvedUrl = localFilePathForPlayback?.takeIf { it.isNotBlank() }?.let { path ->
        NSURL.fileURLWithPath(path).absoluteString ?: path
    } ?: mediaUrl
    val player = remember(resolvedUrl, durationHintMs) { IosChatAudioPlayer(resolvedUrl, durationHintMs) }
    DisposableEffect(resolvedUrl) {
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
    private var playbackEndObserver: Any? = null

    override val isPlaying: Boolean get() = isPlayingState.value

    override val positionMs: Long get() = positionMsState.floatValue.toLong()

    override val durationMs: Long get() = durationMsState.floatValue.toLong()

    init {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl != null) {
            val item = AVPlayerItem(uRL = nsUrl)
            avPlayer.replaceCurrentItemWithPlayerItem(item)
            playbackEndObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                isPlayingState.value = false
                avPlayer.seekToTime(CMTimeMakeWithSeconds(0.0, 1000)) { _ ->
                    refreshProgressFromPlayer()
                }
            }
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
        playbackEndObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        playbackEndObserver = null
        timeObserver?.let { avPlayer.removeTimeObserver(it) }
        timeObserver = null
        avPlayer.pause()
        avPlayer.replaceCurrentItemWithPlayerItem(null)
    }
}
