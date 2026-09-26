package compose.project.click.click.proximity

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Base tone for the mandatory 18.5 kHz proximity chirp. Audible development carriers must never ship. */
const val HANDSHAKE_CARRIER_HZ: Double = 18_500.0

private const val SAMPLE_RATE = 44_100
private const val DIGIT_STEP_HZ = 120.0
private const val TONE_MS = 55
private const val GAP_MS = 22
private const val CHIRP_MS = 140

/**
 * Normalized power of one frequency over `samples[offset until offset + len]` (standard Goertzel:
 * `s0 = x + coeff*s1 - s2; s2 = s1; s1 = s0`). Matches iOS `ProximityCodec.goertzel`.
 */
private fun goertzelPower(
    samples: ShortArray,
    offset: Int,
    len: Int,
    targetHz: Double,
): Double {
    if (len < 8) return 0.0
    val omega = 2.0 * PI * targetHz / SAMPLE_RATE
    val coeff = 2.0 * cos(omega)
    var s1 = 0.0
    var s2 = 0.0
    for (i in 0 until len) {
        val s0 = samples[offset + i] / 32768.0 + coeff * s1 - s2
        s2 = s1
        s1 = s0
    }
    val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
    return power / (len.toDouble() * len.toDouble())
}

private fun digitFrequency(digit: Int): Double = HANDSHAKE_CARRIER_HZ + digit.coerceIn(0, 9) * DIGIT_STEP_HZ

private fun appendSinePcm(
    dst: MutableList<Short>,
    freqHz: Double,
    durationMs: Int,
    amplitude: Double = 0.55,
) {
    val samples = (SAMPLE_RATE * durationMs / 1000.0).roundToInt().coerceAtLeast(1)
    for (i in 0 until samples) {
        val t = i / SAMPLE_RATE.toDouble()
        val v =
            (amplitude * 32767.0 * sin(2.0 * PI * freqHz * t))
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        dst.add(v.toShort())
    }
}

internal fun buildHandshakeAudioPcm(token: String): ShortArray {
    val n = normalizeHandshakeToken(token) ?: return shortArrayOf()
    val out = ArrayList<Short>(8192)
    appendSinePcm(out, HANDSHAKE_CARRIER_HZ, CHIRP_MS, amplitude = 0.95)
    appendSilence(out, GAP_MS)
    for (ch in n) {
        val d = ch.digitToInt()
        appendSinePcm(out, digitFrequency(d), TONE_MS, amplitude = 0.6)
        appendSilence(out, GAP_MS)
    }
    return out.toShortArray()
}

private fun appendSilence(
    dst: MutableList<Short>,
    durationMs: Int,
) {
    val samples = (SAMPLE_RATE * durationMs / 1000.0).roundToInt().coerceAtLeast(1)
    repeat(samples) { dst.add(0) }
}

// The emitted audio above is the wire contract shared with iOS. Decoding is chirp-synchronized
// (ported from iOS `ProximityCodec.decodeAllTokens`): find each >=110 ms carrier run, then read the
// four digit slots at their fixed offsets. The previous sliding-window decoder had a broken Goertzel
// recurrence, merged repeated adjacent digits ("1122" -> "12"), and merged a leading 0 into the chirp.

/** Normalized power floor (about amplitude 0.0006); spectral dominance checks, not loudness, reject noise. */
private const val MIN_TONE_POWER = 1e-7

private fun sampleCount(ms: Int): Int = (SAMPLE_RATE * ms / 1000.0).roundToInt()

/** The carrier dominates its neighbourhood, so broadband noise or speech does not count. */
private fun isCarrierDominant(
    samples: ShortArray,
    offset: Int,
    len: Int,
): Boolean {
    val carrier = goertzelPower(samples, offset, len, HANDSHAKE_CARRIER_HZ)
    if (carrier <= MIN_TONE_POWER) return false
    val neighbours =
        doubleArrayOf(
            HANDSHAKE_CARRIER_HZ - 400.0,
            HANDSHAKE_CARRIER_HZ + DIGIT_STEP_HZ * 2,
            HANDSHAKE_CARRIER_HZ + DIGIT_STEP_HZ * 5,
            12_000.0,
        ).maxOf { goertzelPower(samples, offset, len, it) }
    return carrier > neighbours * 6.0
}

private fun readDigits(
    samples: ShortArray,
    chirpStart: Int,
): String? {
    val slot = sampleCount(TONE_MS + GAP_MS)
    val firstDigit = chirpStart + sampleCount(CHIRP_MS + GAP_MS)
    // Chirp-edge timing is known to within one 10 ms frame; a 30 ms window starting 12 ms into
    // each 55 ms tone tolerates -12..+13 ms of error.
    val margin = sampleCount(12)
    val window = sampleCount(30)
    val digits = StringBuilder(4)
    for (position in 0 until 4) {
        val start = firstDigit + position * slot + margin
        if (start + window > samples.size) return null
        val powers = DoubleArray(10) { goertzelPower(samples, start, window, digitFrequency(it)) }
        val ranked = powers.indices.sortedByDescending { powers[it] }
        val best = powers[ranked[0]]
        if (best <= MIN_TONE_POWER || best <= powers[ranked[1]] * 4.0) return null
        digits.append(ranked[0])
    }
    return digits.toString()
}

/**
 * Decodes every distinct 4-digit handshake token in a capture (several peers may chirp
 * back-to-back in one listen window). Sorted, de-duplicated.
 */
internal fun decodeAllHandshakeTokensFromPcmMono(samples: ShortArray): List<String> {
    if (samples.size < SAMPLE_RATE / 4) return emptyList()
    val hop = SAMPLE_RATE / 100 // 10 ms analysis frames
    val frameCount = samples.size / hop
    val carrierFrames = BooleanArray(frameCount) { isCarrierDominant(samples, it * hop, hop) }
    val tokens = sortedSetOf<String>()
    var index = 0
    while (index < frameCount) {
        if (!carrierFrames[index]) {
            index++
            continue
        }
        var end = index
        while (end < frameCount && carrierFrames[end]) end++
        // A digit-0 tone is 55 ms; only the 140 ms chirp produces a run this long.
        if (end - index >= 11) {
            // The first carrier frame may be partial; the run end is the chirp's sharp edge.
            val chirpEnd = end * hop
            val chirpStart = (chirpEnd - sampleCount(CHIRP_MS)).coerceAtLeast(0)
            readDigits(samples, chirpStart)?.let { tokens.add(it) }
        }
        index = end
    }
    return tokens.toList()
}

/** First token in a capture, or null. */
internal fun decodeTokenFromPcmMono(samples: ShortArray): String? = decodeAllHandshakeTokensFromPcmMono(samples).firstOrNull()

/**
 * A random token every decoder hears unambiguously: no leading 0 (digit 0 shares the carrier
 * frequency) and no adjacent repeats. 9^4 = 6561 tokens; the server matches on overlapping
 * evidence, not the token alone. Mirrors iOS `ProximityCodec.randomToken`.
 */
internal fun randomHandshakeToken(random: Random = Random.Default): String {
    val digits = IntArray(4)
    digits[0] = random.nextInt(1, 10)
    for (i in 1 until 4) {
        var next = random.nextInt(0, 9)
        if (next >= digits[i - 1]) next += 1
        digits[i] = next
    }
    return digits.joinToString("")
}

/** True when [token] avoids a leading 0 and adjacent repeated digits. */
internal fun isCrossPlatformSafeHandshakeToken(token: String): Boolean =
    token.length == 4 &&
        token.all(Char::isDigit) &&
        token[0] != '0' &&
        token.zipWithNext().none { (a, b) -> a == b }

internal fun pcmRms(samples: ShortArray): Double {
    if (samples.isEmpty()) return 0.0
    var s = 0.0
    for (x in samples) {
        val n = x / 32768.0
        s += n * n
    }
    return sqrt(s / samples.size)
}
