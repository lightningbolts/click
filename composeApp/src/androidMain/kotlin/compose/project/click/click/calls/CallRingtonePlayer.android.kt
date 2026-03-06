package compose.project.click.click.calls

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

actual object CallRingtonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ringJob: Job? = null
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 85)

    actual fun startOutgoing() {
        startLoop(tone = ToneGenerator.TONE_PROP_BEEP2, durationMs = 220, pauseMs = 1200)
    }

    actual fun startIncoming() {
        startLoop(tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs = 700, pauseMs = 1500)
    }

    actual fun stop() {
        ringJob?.cancel()
        ringJob = null
        runCatching { toneGenerator.stopTone() }
    }

    private fun startLoop(tone: Int, durationMs: Int, pauseMs: Long) {
        stop()
        ringJob = scope.launch {
            while (true) {
                runCatching { toneGenerator.startTone(tone, durationMs) }
                delay(pauseMs)
            }
        }
    }
}