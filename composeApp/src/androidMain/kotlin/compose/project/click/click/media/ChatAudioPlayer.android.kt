package compose.project.click.click.media

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
actual fun rememberChatAudioPlayer(
    mediaUrl: String,
    durationHintMs: Long,
    localFilePathForPlayback: String?,
): ChatAudioPlayer {
    val src = localFilePathForPlayback?.takeIf { it.isNotBlank() } ?: mediaUrl
    val player = remember(mediaUrl, localFilePathForPlayback) { AndroidChatAudioPlayer(src) }
    DisposableEffect(mediaUrl, localFilePathForPlayback) {
        onDispose { player.dispose() }
    }
    return player
}

private class AndroidChatAudioPlayer(private val url: String) : ChatAudioPlayer {
    private val positionPulse = mutableStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            positionPulse.value = positionPulse.value + 1
            val mp = mediaPlayer
            val playing = mp?.isPlaying == true
            if (!playing && isPlayingState.value) {
                isPlayingState.value = false
            }
            if (playing) {
                handler.postDelayed(this, 250)
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private val isPlayingState = mutableStateOf(false)

    override val isPlaying: Boolean get() = isPlayingState.value

    override val positionMs: Long
        get() {
            positionPulse.value
            return mediaPlayer?.currentPosition?.toLong() ?: 0L
        }

    override val durationMs: Long
        get() {
            positionPulse.value
            return mediaPlayer?.duration?.takeIf { it > 0 }?.toLong() ?: 0L
        }

    init {
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                prepared = true
                positionPulse.value = positionPulse.value + 1
            }
            mp.setOnCompletionListener {
                isPlayingState.value = false
                it.seekTo(0)
                handler.removeCallbacks(tick)
                positionPulse.value = positionPulse.value + 1
            }
            mp.prepareAsync()
            mediaPlayer = mp
        }
    }

    override fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        if (mp.isPlaying) {
            mp.pause()
            isPlayingState.value = false
            handler.removeCallbacks(tick)
        } else {
            mp.start()
            isPlayingState.value = true
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        val d = mp.duration.takeIf { it > 0 } ?: return
        val clamped = positionMs.coerceIn(0L, d.toLong())
        mp.seekTo(clamped.toInt())
        positionPulse.value = positionPulse.value + 1
    }

    override fun dispose() {
        handler.removeCallbacks(tick)
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}
