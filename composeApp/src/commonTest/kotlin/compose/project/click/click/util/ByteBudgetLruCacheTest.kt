package compose.project.click.click.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ByteBudgetLruCacheTest {
    private fun cache(max: Long = 10) = ByteBudgetLruCache<String, ByteArray>(max) { it.size.toLong() }

    @Test
    fun evictsLeastRecentlyUsedOnceOverBudget() {
        val c = cache()
        assertEquals(emptyList(), c.put("a", ByteArray(4)))
        assertEquals(emptyList(), c.put("b", ByteArray(4)))
        assertEquals(listOf("a"), c.put("c", ByteArray(4)))
        assertFalse("a" in c)
        assertEquals(8, c.totalBytes)
        assertEquals(listOf("b", "c"), c.keys())
    }

    @Test
    fun getMarksAnEntryRecentlyUsed() {
        val c = cache()
        c.put("a", ByteArray(4))
        c.put("b", ByteArray(4))
        c.get("a")
        assertEquals(listOf("b"), c.put("c", ByteArray(4)))
        assertTrue("a" in c)
    }

    @Test
    fun evictsSeveralToFitOneLargeEntry() {
        val c = cache()
        c.put("a", ByteArray(3))
        c.put("b", ByteArray(3))
        c.put("c", ByteArray(3))
        assertEquals(listOf("a", "b", "c"), c.put("big", ByteArray(9)))
        assertEquals(listOf("big"), c.keys())
        assertEquals(9, c.totalBytes)
    }

    @Test
    fun keepsASingleEntryLargerThanTheBudget() {
        val c = cache()
        c.put("a", ByteArray(2))
        assertEquals(listOf("a"), c.put("huge", ByteArray(50)))
        assertEquals(1, c.size)
        assertEquals(50, c.totalBytes)
    }

    @Test
    fun replacingAKeyRecountsItsSize() {
        val c = cache()
        c.put("a", ByteArray(6))
        c.put("a", ByteArray(2))
        assertEquals(2, c.totalBytes)
        assertEquals(emptyList(), c.put("b", ByteArray(8)))
    }

    @Test
    fun removeAndClearFreeTheirBytes() {
        val c = cache()
        c.put("a", ByteArray(4))
        c.put("b", ByteArray(4))
        c.remove("a")
        assertEquals(4, c.totalBytes)
        assertNull(c.remove("missing"))
        c.clear()
        assertEquals(0, c.totalBytes)
        assertEquals(emptyMap(), c.snapshot())
    }

    @Test
    fun rejectsANonPositiveBudget() {
        assertFailsWith<IllegalArgumentException> { cache(0) }
    }
}
