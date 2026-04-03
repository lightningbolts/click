package compose.project.click.click.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import compose.project.click.click.data.models.NoiseLevelCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class AndroidAmbientNoiseMonitor(
    private val context: Context
) : AmbientNoiseMonitor {

    override val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun sampleNoiseReading(durationMs: Int): AmbientNoiseSample? = withContext(Dispatchers.Default) {
        if (!hasPermission) return@withContext null

        val sampleRate = 16_000
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) return@withContext null

        val audioRecord = try {
            @Suppress("MissingPermission") // Guarded by hasPermission above
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
        } catch (error: Exception) {
            println("AndroidAmbientNoiseMonitor: Failed to create AudioRecord: ${error.message}")
            null
        } ?: return@withContext null

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return@withContext null
        }

        val buffer = ShortArray(minBufferSize)
        val readings = mutableListOf<Double>()
        val startedAt = System.currentTimeMillis()

        try {
            audioRecord.startRecording()

            while (System.currentTimeMillis() - startedAt < durationMs) {
                val readCount = audioRecord.read(buffer, 0, buffer.size)
                if (readCount <= 0) continue

                var sumSquares = 0.0
                for (index in 0 until readCount) {
                    val normalized = buffer[index] / Short.MAX_VALUE.toDouble()
                    sumSquares += normalized.pow(2)
                }

                val rms = sqrt(sumSquares / readCount.toDouble())
                if (rms > 0.0) {
                    val pseudoDb = (20.0 * log10(rms) + 90.0).coerceIn(0.0, 100.0)
                    readings += pseudoDb
                }
            }
        } catch (error: Exception) {
            println("AndroidAmbientNoiseMonitor: Failed to sample audio: ${error.message}")
            return@withContext null
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Exception) {
            }
            audioRecord.release()
        }

        val averageDb = readings.average().takeIf { !it.isNaN() } ?: return@withContext null
        AmbientNoiseSample(
            category = when {
                averageDb < 45.0 -> NoiseLevelCategory.QUIET
                averageDb < 65.0 -> NoiseLevelCategory.MODERATE
                averageDb < 80.0 -> NoiseLevelCategory.LOUD
                else -> NoiseLevelCategory.VERY_LOUD
            },
            decibels = averageDb
        )
    }
}

@Composable
actual fun rememberAmbientNoiseMonitor(): AmbientNoiseMonitor {
    val context = LocalContext.current
    return remember(context) { AndroidAmbientNoiseMonitor(context) }
}