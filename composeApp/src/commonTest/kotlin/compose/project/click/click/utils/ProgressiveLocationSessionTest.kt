package compose.project.click.click.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgressiveLocationSessionTest {

    @Test
    fun bucket1_acceptsUnder15mInFirstWindow() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        val r = session.onReading(1.0, 2.0, 14.5, null)
        assertNotNull(r)
        assertEquals(14.5, r.accuracyMeters)
    }

    @Test
    fun bucket1_rejects15mOrMoreInFirstWindow() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 15.0, null))
        assertNull(session.onReading(1.0, 2.0, 25.0, null))
    }

    @Test
    fun bucket2_acceptsUnder35m() {
        var t = 1500L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 35.0, null))
        assertNotNull(session.onReading(1.0, 2.0, 9.0, null))
        assertNotNull(session.onReading(1.0, 2.0, 34.0, null))
    }

    @Test
    fun bucket3_acceptsUnder65m() {
        var t = 3500L
        val session = ProgressiveLocationSession.forTest { t }
        assertNull(session.onReading(1.0, 2.0, 65.0, null))
        val r = session.onReading(1.0, 2.0, 64.0, null)
        assertNotNull(r)
    }

    @Test
    fun bestAtTimeout_picksTightestAmongUnder65() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        session.onReading(1.0, 2.0, 50.0, null)
        session.onReading(1.0, 2.0, 20.0, null)
        session.onReading(1.0, 2.0, 70.0, null)
        val best = session.bestAtTimeout()
        assertNotNull(best)
        assertEquals(20.0, best.accuracyMeters)
    }

    @Test
    fun bestAtTimeout_nullWhenNothingUnder65() {
        var t = 0L
        val session = ProgressiveLocationSession.forTest { t }
        session.onReading(1.0, 2.0, 80.0, null)
        assertNull(session.bestAtTimeout())
    }
}
