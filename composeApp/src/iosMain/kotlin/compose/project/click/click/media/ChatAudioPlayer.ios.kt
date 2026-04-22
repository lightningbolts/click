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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSFileManager
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
    val remote = mediaUrl.trim()
    val local = localFilePathForPlayback?.trim()?.takeIf { it.isNotEmpty() }
    val player = remember(remote, local, durationHintMs) { IosChatAudioPlayer(remote, local, durationHintMs) }
    DisposableEffect(remote, local) {
        onDispose { player.dispose() }
    }
    return player
}

private class IosChatAudioPlayer(
    private val remoteUrl: String,
    private val localFilePath: String?,
    durationHintMs: Long,
) : ChatAudioPlayer {
    private val avPlayer: AVPlayer
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
        prepareAudioSessionForPlayback()
        avPlayer = AVPlayer()
        val nsUrl = resolvePlaybackNsUrl(localFilePath, remoteUrl)
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
        val item = avPlayer.currentItem
        if (item != null && item.error != null) {
            if (isPlayingState.value) {
                isPlayingState.value = false
            }
            avPlayer.pause()
            return
        }
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
            if (prepareAudioSessionForPlayback()) {
                avPlayer.play()
                isPlayingState.value = true
            }
        }
        refreshProgressFromPlayer()
    }

    private fun prepareAudioSessionForPlayback(): Boolean {
        return try {
            val session = AVAudioSession.sharedInstance()
            // Playback category only: `defaultToSpeaker` is invalid here (requires playAndRecord).
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
            true
        } catch (_: Throwable) {
            false
        }
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

private fun resolvePlaybackNsUrl(localPath: String?, remote: String): NSURL? {
    val trimmedLocal = localPath?.trim()?.takeIf { it.isNotEmpty() }
    if (!trimmedLocal.isNullOrEmpty()) {
        val fsPath = when {
            trimmedLocal.startsWith("file://") -> trimmedLocal.removePrefix("file://")
            else -> trimmedLocal
        }
        if (NSFileManager.defaultManager.fileExistsAtPath(fsPath)) {
            return NSURL.fileURLWithPath(fsPath)
        }
    }
    val r = remote.trim()
    if (r.isNotEmpty()) {
        return nsUrlFromHttpString(r)
    }
    return null
}

private fun nsUrlFromHttpString(url: String): NSURL? {
    val t = url.trim()
    if (t.isEmpty()) return null
    NSURL.URLWithString(t)?.let { return it }
    // Foundation's `NSString.stringByAddingPercentEncoding...` / `NSCharacterSet` are not
    // consistently exported in Kotlin/Native; re-encode only characters that typically make
    // `NSURL.URLWithString` return null (spaces, controls, non-ASCII), preserving valid %HH.
    val encoded = percentEncodeHttpUrlStringPreservingExistingPctEncoding(t)
    return NSURL.URLWithString(encoded)
}

private fun percentEncodeHttpUrlStringPreservingExistingPctEncoding(s: String): String {
    val hex = "0123456789ABCDEF"
    return buildString(s.length + 16) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && s[i + 1].isHexDigit() && s[i + 2].isHexDigit()) {
                append(c).append(s[i + 1]).append(s[i + 2])
                i += 3
                continue
            }
            val mustEncode = c.isISOControl() ||
                c == ' ' ||
                c.code > 127 ||
                c == '|' ||
                c == '"' ||
                c == '<' ||
                c == '>' ||
                c == '{' ||
                c == '}' ||
                c == '\\' ||
                c == '`'
            if (!mustEncode) {
                append(c)
                i++
                continue
            }
            for (b in c.toString().encodeToByteArray()) {
                append('%')
                append(hex[b.toInt() shr 4 and 15])
                append(hex[b.toInt() and 15])
            }
            i++
        }
    }
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'
