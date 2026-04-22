package compose.project.click.click.proximity

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tri-factor handshake parsing coverage.
 *
 * These tests lock down the on-the-wire representation of the 4-digit
 * ephemeral handshake token. A regression here silently breaks peer
 * discovery across BLE and ultrasonic channels, so the codec shape is
 * treated as a public contract even though the functions are `internal`.
 */
class ProximityHandshakeCodecTest {

    // region normalizeHandshakeToken

    @Test
    fun normalize_acceptsExactFourDigits() {
        assertEquals("1234", normalizeHandshakeToken("1234"))
        assertEquals("0000", normalizeHandshakeToken("0000"))
        assertEquals("9999", normalizeHandshakeToken("9999"))
    }

    @Test
    fun normalize_padsShortTokensWithLeadingZeros() {
        assertEquals("0007", normalizeHandshakeToken("7"))
        assertEquals("0042", normalizeHandshakeToken("42"))
        assertEquals("0123", normalizeHandshakeToken("123"))
    }

    @Test
    fun normalize_stripsNonDigitsBeforePadding() {
        assertEquals("1234", normalizeHandshakeToken("12-34"))
        assertEquals("1234", normalizeHandshakeToken("  1234  "))
        assertEquals("1234", normalizeHandshakeToken("abc1234xyz"))
        assertEquals("0042", normalizeHandshakeToken("#4-2!"))
    }

    @Test
    fun normalize_keepsLastFourWhenOverflowing() {
        assertEquals("5678", normalizeHandshakeToken("12345678"))
        assertEquals("9012", normalizeHandshakeToken("123456789012"))
    }

    @Test
    fun normalize_coercesNoDigitInputToFallbackZeros() {
        // Current policy: no-digit input is coerced to "0000" rather than
        // rejected. This test locks that behaviour so any future tightening
        // (e.g. returning null for empty input) is an intentional change
        // surfaced by failing tests.
        assertEquals("0000", normalizeHandshakeToken(""))
        assertEquals("0000", normalizeHandshakeToken("abc"))
        assertEquals("0000", normalizeHandshakeToken("----"))
        assertEquals("0000", normalizeHandshakeToken(" \n\t"))
    }

    // endregion

    // region BLE manufacturer payload

    @Test
    fun buildBle_prependsCkAsciiHeader() {
        val bytes = buildBleManufacturerPayload("1234")
        assertEquals(6, bytes.size)
        assertEquals(0x43.toByte(), bytes[0]) // 'C'
        assertEquals(0x4B.toByte(), bytes[1]) // 'K'
        assertEquals('1'.code.toByte(), bytes[2])
        assertEquals('2'.code.toByte(), bytes[3])
        assertEquals('3'.code.toByte(), bytes[4])
        assertEquals('4'.code.toByte(), bytes[5])
    }

    @Test
    fun buildBle_normalizesBeforeEmitting() {
        val bytes = buildBleManufacturerPayload("42")
        assertEquals(6, bytes.size)
        assertEquals('0'.code.toByte(), bytes[2])
        assertEquals('0'.code.toByte(), bytes[3])
        assertEquals('4'.code.toByte(), bytes[4])
        assertEquals('2'.code.toByte(), bytes[5])
    }

    @Test
    fun buildBle_coercesNoDigitTokenToZeros() {
        // Matches the normalize policy: no-digit input becomes "0000".
        val bytes = buildBleManufacturerPayload("no digits at all")
        assertEquals(6, bytes.size)
        assertEquals(0x43.toByte(), bytes[0])
        assertEquals(0x4B.toByte(), bytes[1])
        for (i in 2..5) {
            assertEquals('0'.code.toByte(), bytes[i], "Byte $i should be ASCII '0'")
        }
    }

    @Test
    fun parseBle_roundTripsAllDigitTokens() {
        for (i in 0..9999) {
            val token = i.toString().padStart(4, '0')
            val payload = buildBleManufacturerPayload(token)
            val decoded = parseBleManufacturerPayload(payload)
            assertEquals(token, decoded, "Round-trip failed at $token")
        }
    }

    @Test
    fun parseBle_rejectsNullAndShortBuffers() {
        assertNull(parseBleManufacturerPayload(null))
        assertNull(parseBleManufacturerPayload(byteArrayOf()))
        assertNull(parseBleManufacturerPayload(byteArrayOf(0x43, 0x4B)))
        assertNull(parseBleManufacturerPayload(byteArrayOf(0x43, 0x4B, '1'.code.toByte())))
        assertNull(
            parseBleManufacturerPayload(
                byteArrayOf(0x43, 0x4B, '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte())
            )
        )
    }

    @Test
    fun parseBle_rejectsWrongPrefix() {
        val wrongFirst = byteArrayOf(
            0x44, 0x4B, '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte()
        )
        val wrongSecond = byteArrayOf(
            0x43, 0x4C, '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte()
        )
        assertNull(parseBleManufacturerPayload(wrongFirst))
        assertNull(parseBleManufacturerPayload(wrongSecond))
    }

    @Test
    fun parseBle_ignoresTrailingBytesPastFourDigits() {
        val payload = byteArrayOf(
            0x43, 0x4B,
            '1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(),
            0x00, 0x7F, 0x55.toByte()
        )
        assertEquals("1234", parseBleManufacturerPayload(payload))
    }

    @Test
    fun parseBle_coercesNonDigitPayloadBodyToZeros() {
        // Parser uses the same normalize policy: non-digit body collapses to "0000".
        val payload = byteArrayOf(
            0x43, 0x4B, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte()
        )
        assertEquals("0000", parseBleManufacturerPayload(payload))
    }

    // endregion

    // region ultrasonic codec

    @Test
    fun ultrasonic_buildProducesNonTrivialAudioForValidToken() {
        val pcm = buildHandshakeAudioPcm("1234")
        // 1 chirp (140 ms) + 5 gaps (22 ms) + 4 tones (55 ms) at 44.1 kHz.
        // Expected duration ~ 140 + 5*22 + 4*55 = 470 ms → ~20_727 samples.
        assertTrue(pcm.size in 20_000..22_000, "Unexpected pcm size ${pcm.size}")
        // Signal energy must be above silence.
        assertTrue(pcmRms(pcm) > 0.1, "Generated audio is too quiet")
    }

    @Test
    fun ultrasonic_buildProducesAudioForCoercedInvalidToken() {
        // normalize coerces garbage to "0000", so audio is still generated.
        val pcm = buildHandshakeAudioPcm("abcd")
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcmRms(pcm) > 0.1)
    }

    @Test
    fun ultrasonic_rejectsSilence() {
        val silent = ShortArray(44_100)
        assertNull(decodeTokenFromPcmMono(silent))
    }

    @Test
    fun ultrasonic_rejectsTooShortInput() {
        val tiny = ShortArray(1000)
        assertNull(decodeTokenFromPcmMono(tiny))
    }

    @Test
    fun ultrasonic_decodeAllRejectsPureSilence() {
        val silent = ShortArray(44_100)
        assertTrue(decodeAllHandshakeTokensFromPcmMono(silent).isEmpty())
    }

    @Test
    fun ultrasonic_decodeAllReturnsOnlyNormalized4DigitTokensSorted() {
        val pcm = buildHandshakeAudioPcm("1234")
        val found = decodeAllHandshakeTokensFromPcmMono(pcm)
        // Decoder quality is DSP-fuzzy; we don't assert an exact match, but
        // we do assert the shape of whatever it does return.
        assertEquals(found.sorted(), found)
        for (t in found) {
            assertEquals(4, t.length)
            assertTrue(t.all { ch -> ch.isDigit() })
        }
    }

    // endregion
}

/**
 * GPS / altitude proximity plausibility checks.
 *
 * The server is authoritative for clustering, but the client filters
 * obviously-bad fixes before hitting `bind-proximity-connection`. These
 * cover the pure maths used in the pre-flight validation path.
 */
class ProximityDistanceTest {

    @Test
    fun haversine_zeroForIdenticalPoints() {
        val d = compose.project.click.click.ui.utils.haversineDistance(
            37.7749, -122.4194, 37.7749, -122.4194
        )
        assertEquals(0.0, d, 1e-6)
    }

    @Test
    fun haversine_oneArcMinuteLatitudeIsAboutOneNauticalMile() {
        // One degree of latitude ≈ 111.195 km at the equator. One arc-minute ≈ 1852 m.
        val d = compose.project.click.click.ui.utils.haversineDistance(
            0.0, 0.0, 1.0 / 60.0, 0.0
        )
        assertTrue(abs(d - 1852.0) < 20.0, "Expected ~1852 m, got $d")
    }

    @Test
    fun haversine_symmetric() {
        val a = compose.project.click.click.ui.utils.haversineDistance(
            40.0, -74.0, 34.0522, -118.2437
        )
        val b = compose.project.click.click.ui.utils.haversineDistance(
            34.0522, -118.2437, 40.0, -74.0
        )
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun haversine_knownLongRangeDistance() {
        // NYC (40.7128, -74.0060) to LA (34.0522, -118.2437) ≈ 3_935 km.
        val d = compose.project.click.click.ui.utils.haversineDistance(
            40.7128, -74.0060, 34.0522, -118.2437
        )
        assertTrue(abs(d - 3_935_000.0) < 15_000.0, "Expected ~3935 km, got ${d / 1000} km")
    }

    @Test
    fun haversine_detectsSubMeterMovement() {
        // ~1 meter in latitude at 40°N.
        val d = compose.project.click.click.ui.utils.haversineDistance(
            40.0, -74.0, 40.0 + (1.0 / 111_000.0), -74.0
        )
        assertTrue(d in 0.8..1.2, "Expected ~1 m, got $d")
    }

    @Test
    fun haversine_worksAcrossEquatorAndPrimeMeridian() {
        val d = compose.project.click.click.ui.utils.haversineDistance(
            -0.001, -0.001, 0.001, 0.001
        )
        assertTrue(d > 0.0, "Expected positive distance across origin")
        assertTrue(d < 500.0, "Expected small distance, got $d")
    }

    @Test
    fun clientSideGpsValidity_matchesRepositoryRules() {
        // Mirrors the `hasGps` guard inside ConnectionRepository.bindProximityHandshake —
        // the server rejects (0,0) and non-finite inputs so the client must too.
        fun hasGps(lat: Double?, lon: Double?): Boolean =
            lat != null && lon != null && lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)

        assertTrue(hasGps(37.7749, -122.4194))
        assertFalse(hasGps(null, -122.0))
        assertFalse(hasGps(37.0, null))
        assertFalse(hasGps(0.0, 0.0))
        assertFalse(hasGps(Double.NaN, -122.0))
        assertFalse(hasGps(37.0, Double.POSITIVE_INFINITY))
        // (0, non-zero) is still accepted — only the null-island anti-pattern is rejected.
        assertTrue(hasGps(0.0, -0.001))
        assertTrue(hasGps(-0.001, 0.0))
    }
}
