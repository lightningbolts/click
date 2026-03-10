package compose.project.click.click.sensors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import compose.project.click.click.data.models.NoiseLevelCategory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioQualityLow
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_async
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class IosAmbientNoiseMonitor : AmbientNoiseMonitor {
    override val hasPermission: Boolean
        get() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    override suspend fun sampleNoiseReading(durationMs: Int): AmbientNoiseSample? {
        if (!ensureRecordPermission()) {
            return null
        }

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)

        val temporaryUrl = NSURL.fileURLWithPath(
            NSTemporaryDirectory().trimEnd('/') + "/click-ambient-noise.m4a"
        )
        val settings = mapOf<Any?, Any>(
            AVFormatIDKey to 1633772320u,
            AVSampleRateKey to 12_000.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to AVAudioQualityLow,
        )

        val recorder = AVAudioRecorder(uRL = temporaryUrl, settings = settings, error = null)
        recorder.meteringEnabled = true

        if (!recorder.prepareToRecord() || !recorder.record()) {
            return null
        }

        delay(durationMs.toLong())
        recorder.updateMeters()
        val averagePower = recorder.averagePowerForChannel(0u)
        recorder.stop()
        NSFileManager.defaultManager.removeItemAtURL(temporaryUrl, error = null)

        val approximateDb = (averagePower.toDouble() + 90.0).coerceIn(0.0, 100.0)

        return AmbientNoiseSample(
            category = when {
                averagePower < -50.0f -> NoiseLevelCategory.QUIET
                averagePower < -30.0f -> NoiseLevelCategory.MODERATE
                averagePower < -15.0f -> NoiseLevelCategory.LOUD
                else -> NoiseLevelCategory.VERY_LOUD
            },
            decibels = approximateDb
        )
    }

    private suspend fun ensureRecordPermission(): Boolean {
        val session = AVAudioSession.sharedInstance()
        return when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> true
            AVAudioSessionRecordPermissionDenied -> false
            else -> suspendCancellableCoroutine { continuation ->
                dispatch_async(dispatch_get_main_queue()) {
                    session.requestRecordPermission { granted: Boolean ->
                        continuation.resume(granted)
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberAmbientNoiseMonitor(): AmbientNoiseMonitor {
    return remember { IosAmbientNoiseMonitor() }
}