package compose.project.click.click.proximity

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for the chirp-synchronized decoder (ported from iOS `ProximityCodec`).
 * The old decoder merged repeated digits and a leading 0; these must now decode exactly.
 */
class UltrasonicTokenCodecTest {
    private fun silence(ms: Int) = ShortArray(44_100 * ms / 1000)

    private fun concat(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(out, at)
            at += p.size
        }
        return out
    }

    @Test
    fun decodesPlainToken() {
        val pcm = concat(silence(80), buildHandshakeAudioPcm("1234"), silence(80))
        assertEquals(listOf("1234"), decodeAllHandshakeTokensFromPcmMono(pcm))
    }

    @Test
    fun decodesRepeatedAdjacentDigits() {
        for (token in listOf("1123", "7700", "5555", "9988")) {
            val pcm = concat(silence(60), buildHandshakeAudioPcm(token), silence(60))
            assertEquals(listOf(token), decodeAllHandshakeTokensFromPcmMono(pcm), "token $token")
        }
    }

    @Test
    fun decodesLeadingAndTrailingZero() {
        for (token in listOf("0500", "0123", "1000")) {
            val pcm = concat(silence(60), buildHandshakeAudioPcm(token), silence(60))
            assertEquals(listOf(token), decodeAllHandshakeTokensFromPcmMono(pcm), "token $token")
        }
    }

    @Test
    fun decodesEverySafeTokenFromARandomSample() {
        val random = Random(42)
        repeat(60) {
            val token = randomHandshakeToken(random)
            val pcm = concat(silence(random.nextInt(0, 120)), buildHandshakeAudioPcm(token), silence(40))
            assertEquals(token, decodeTokenFromPcmMono(pcm), "token $token")
        }
    }

    @Test
    fun decodesBackToBackPeers() {
        val pcm =
            concat(
                silence(50),
                buildHandshakeAudioPcm("2468"),
                silence(120),
                buildHandshakeAudioPcm("9135"),
                silence(50),
            )
        assertEquals(listOf("2468", "9135"), decodeAllHandshakeTokensFromPcmMono(pcm))
    }

    @Test
    fun decodesWithModerateAttenuationAndBroadbandNoise() {
        val random = Random(7)
        val clean = concat(silence(100), buildHandshakeAudioPcm("3141"), silence(100))
        val noisy =
            ShortArray(clean.size) { i ->
                val attenuated = clean[i] * 0.05
                val hum = 900.0 * sin(2.0 * PI * 220.0 * i / 44_100.0) // speech-band tone
                val hiss = random.nextInt(-300, 300)
                (attenuated + hum + hiss).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        assertEquals(listOf("3141"), decodeAllHandshakeTokensFromPcmMono(noisy))
    }

    @Test
    fun ignoresNoiseOnly() {
        val random = Random(3)
        val noise = ShortArray(44_100) { random.nextInt(-2000, 2000).toShort() }
        assertTrue(decodeAllHandshakeTokensFromPcmMono(noise).isEmpty())
        assertNull(decodeTokenFromPcmMono(noise))
    }

    @Test
    fun randomTokensAreAlwaysCrossPlatformSafe() {
        val random = Random(1)
        repeat(5_000) {
            val token = randomHandshakeToken(random)
            assertTrue(isCrossPlatformSafeHandshakeToken(token), "unsafe token $token")
        }
    }

    @Test
    fun safetyCheckRejectsLeadingZeroAndRepeats() {
        assertTrue(isCrossPlatformSafeHandshakeToken("1212"))
        assertTrue(!isCrossPlatformSafeHandshakeToken("0123"))
        assertTrue(!isCrossPlatformSafeHandshakeToken("1123"))
        assertTrue(!isCrossPlatformSafeHandshakeToken("123"))
    }
}
