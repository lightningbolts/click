package compose.project.click.click.calls

import android.content.Context
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
    private var toneGenerator: ToneGenerator? = null

    actual fun startOutgoing() {
        ensureRingAudioMode()
        startLoop(tone = ToneGenerator.TONE_PROP_BEEP2, durationMs = 220, pauseMs = 1200)
    }

    actual fun startIncoming() {
        ensureRingAudioMode()
        startLoop(tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, durationMs = 700, pauseMs = 1500)
    }

    /** VoIP leaves MODE_IN_COMMUNICATION; STREAM_RING tones are silent until mode is restored. */
    private fun ensureRingAudioMode() {
        runCatching {
            val audioManager = AndroidCallRuntime.appContext()
                ?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    actual fun stop() {
        ringJob?.cancel()
        ringJob = null
        releaseToneGenerator()
    }

    private fun startLoop(tone: Int, durationMs: Int, pauseMs: Long) {
        stop()
        // Recreate every start — a single ToneGenerator often stops working after stopTone()
        // or after a VoIP call changes the audio mode.
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_RING, 85) }.getOrNull()
            ?: runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85) }.getOrNull()
            ?: return
        toneGenerator = generator
        ringJob = scope.launch {
            try {
                while (true) {
                    runCatching { generator.startTone(tone, durationMs) }
                    delay(pauseMs)
                }
            } finally {
                // Only release if we still own this instance (stop() may have already released it).
                if (toneGenerator === generator) {
                    toneGenerator = null
                    runCatching { generator.stopTone() }
                    runCatching { generator.release() }
                }
            }
        }
    }

    private fun releaseToneGenerator() {
        val generator = toneGenerator ?: return
        toneGenerator = null
        runCatching { generator.stopTone() }
        runCatching { generator.release() }
    }
}
