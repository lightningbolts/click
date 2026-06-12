package compose.project.click.click.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgressiveLocationSessionTest {

    @Test
    fun bucket1_acceptsUpTo1mInFirstWindow() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        val tight = session.onReading(1.0, 2.0, 0.8, null)
        assertNotNull(tight)
        assertEquals(0.8, tight.accuracyMeters)
        val atLimit = session.onReading(1.0, 2.0, 1.0, null)
        assertNotNull(atLimit)
        assertEquals(1.0, atLimit.accuracyMeters)
    }

    @Test
    fun bucket1_acceptsUpTo1mThrough3s() {
        var t = 2999L
        val session = ProgressiveLocationSession.forTest { t }
        assertNotNull(session.onReading(1.0, 2.0, 0.9, null))
    }

    @Test
    fun bucket1_rejectsOver1mInFirstWindow() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 1.1, null))
        assertNull(session.onReading(1.0, 2.0, 4.0, null))
    }

    @Test
    fun bucket2_acceptsUpTo5m() {
        var t = 3000L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 5.1, null))
        assertNotNull(session.onReading(1.0, 2.0, 5.0, null))
        assertNotNull(session.onReading(1.0, 2.0, 2.0, null))
    }

    @Test
    fun bucket3_acceptsUpTo10m() {
        var t = 5000L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 10.1, null))
        val r = session.onReading(1.0, 2.0, 10.0, null)
        assertNotNull(r)
    }

    @Test
    fun bestAtTimeout_picksTightestAmongUnder15() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        session.onReading(1.0, 2.0, 12.0, null)
        session.onReading(1.0, 2.0, 4.0, null)
        session.onReading(1.0, 2.0, 20.0, null)
        val best = session.bestAtTimeout()
        assertNotNull(best)
        assertEquals(4.0, best.accuracyMeters)
    }

    @Test
    fun bestAtTimeout_nullWhenNothingUnder15() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        session.onReading(1.0, 2.0, 16.0, null)
        assertNull(session.bestAtTimeout())
    }
}
