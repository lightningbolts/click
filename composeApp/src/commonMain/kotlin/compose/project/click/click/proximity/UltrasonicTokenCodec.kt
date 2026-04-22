package compose.project.click.click.proximity

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Base tone for the mandatory 18.5 kHz proximity chirp. */
const val HANDSHAKE_CARRIER_HZ: Double = 18_500.0

private const val SAMPLE_RATE = 44_100
private const val DIGIT_STEP_HZ = 120.0
private const val TONE_MS = 55
private const val GAP_MS = 22
private const val CHIRP_MS = 140

private fun goertzelMagnitudeSq(samples: ShortArray, offset: Int, len: Int, targetHz: Double): Double {
    if (len < 8) return 0.0
    val k = ((0.5 + (len * targetHz) / SAMPLE_RATE).toInt()).coerceAtLeast(1)
    val omega = (2.0 * PI * k) / len
    val sinW = sin(omega)
    val cosW = cos(omega)
    var coeff = 2.0 * cosW
    var s = 0.0
    var s2 = 0.0
    for (i in 0 until len) {
        val x = samples[offset + i] / 32768.0
        s = x + coeff * s - s2
        s2 = s
    }
    val real = s - s2 * cosW
    val imag = s2 * sinW
    return real * real + imag * imag
}

private fun digitFrequency(digit: Int): Double =
    HANDSHAKE_CARRIER_HZ + digit.coerceIn(0, 9) * DIGIT_STEP_HZ

private fun appendSinePcm(dst: MutableList<Short>, freqHz: Double, durationMs: Int, amplitude: Double = 0.55) {
    val samples = (SAMPLE_RATE * durationMs / 1000.0).roundToInt().coerceAtLeast(1)
    for (i in 0 until samples) {
        val t = i / SAMPLE_RATE.toDouble()
        val v = (amplitude * 32767.0 * sin(2.0 * PI * freqHz * t)).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        dst.add(v.toShort())
    }
}

internal fun buildHandshakeAudioPcm(token: String): ShortArray {
    val n = normalizeHandshakeToken(token) ?: return shortArrayOf()
    val out = ArrayList<Short>(8192)
    appendSinePcm(out, HANDSHAKE_CARRIER_HZ, CHIRP_MS, amplitude = 0.65)
    appendSilence(out, GAP_MS)
    for (ch in n) {
        val d = ch.digitToInt()
        appendSinePcm(out, digitFrequency(d), TONE_MS, amplitude = 0.6)
        appendSilence(out, GAP_MS)
    }
    return out.toShortArray()
}

private fun appendSilence(dst: MutableList<Short>, durationMs: Int) {
    val samples = (SAMPLE_RATE * durationMs / 1000.0).roundToInt().coerceAtLeast(1)
    repeat(samples) { dst.add(0) }
}

/**
 * Best-effort decode of a 4-digit token from microphone PCM (mono).
 * Returns null when no confident digit sequence is found.
 */
/**
 * Decodes every distinct 4-digit handshake token found in a longer recording by sliding a decode
 * window. Used when several peers play ultrasonic tokens back-to-back in one capture window.
 */
internal fun decodeAllHandshakeTokensFromPcmMono(samples: ShortArray): List<String> {
    if (samples.size < SAMPLE_RATE / 4) return emptyList()
    val chunkSamples = (SAMPLE_RATE * 0.55).roundToInt().coerceIn(8000, samples.size)
    val hop = (chunkSamples / 2).coerceAtLeast(SAMPLE_RATE / 20)
    val found = linkedSetOf<String>()
    var start = 0
    while (start + chunkSamples <= samples.size) {
        val chunk = samples.copyOfRange(start, start + chunkSamples)
        decodeTokenFromPcmMono(chunk)?.let { found.add(it) }
        start += hop
    }
    decodeTokenFromPcmMono(samples)?.let { found.add(it) }
    return found.sorted()
}

internal fun decodeTokenFromPcmMono(samples: ShortArray): String? {
    if (samples.size < SAMPLE_RATE / 4) return null
    val window = (SAMPLE_RATE * 0.09).roundToInt().coerceIn(256, 4096)
    val step = window / 3
    val digits = ArrayList<Int>(8)
    var lastDigit: Int? = null
    var repeat = 0
    var i = 0
    while (i + window <= samples.size) {
        var bestD = 0
        var bestMag = 0.0
        for (d in 0..9) {
            val mag = goertzelMagnitudeSq(samples, i, window, digitFrequency(d))
            if (mag > bestMag) {
                bestMag = mag
                bestD = d
            }
        }
        val noise = (0..9).map { goertzelMagnitudeSq(samples, i, window, HANDSHAKE_CARRIER_HZ + it * DIGIT_STEP_HZ + 60.0) }
            .sortedDescending()
            .getOrNull(2) ?: 0.0
        val threshold = noise * 6.0 + 1e-6
        if (bestMag > threshold && bestMag > 5e-5) {
            if (bestD == lastDigit) {
                repeat++
            } else {
                if (lastDigit != null && repeat >= 2) {
                    digits.add(lastDigit!!)
                }
                lastDigit = bestD
                repeat = 1
            }
        } else {
            if (lastDigit != null && repeat >= 2) {
                digits.add(lastDigit!!)
            }
            lastDigit = null
            repeat = 0
        }
        i += step
    }
    if (lastDigit != null && repeat >= 2) {
        digits.add(lastDigit!!)
    }
    if (digits.size < 4) return null
    val tail = digits.takeLast(4).joinToString("") { it.toString() }
    return normalizeHandshakeToken(tail)
}

internal fun pcmRms(samples: ShortArray): Double {
    if (samples.isEmpty()) return 0.0
    var s = 0.0
    for (x in samples) {
        val n = x / 32768.0
        s += n * n
    }
    return sqrt(s / samples.size)
}
