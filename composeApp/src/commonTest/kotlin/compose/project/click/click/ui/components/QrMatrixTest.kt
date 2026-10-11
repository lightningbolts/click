package compose.project.click.click.ui.components

import qrcode.raw.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QrMatrixTest {
    // Byte mode at level M: version 1 holds 14 bytes (21 modules), version 3 holds 42 (29 modules).
    private val shortPayload = "HELLO WORLD"
    private val passUrl = "https://joinclick.co/e/e1?pass=K7P4QXabcd" // 41 bytes

    @Test
    fun shortPayload_isVersion1AtLevelM() {
        val modules = assertNotNull(qrModules(shortPayload))
        assertEquals(21, modules.size)
        assertTrue(modules.all { it.size == 21 })
    }

    @Test
    fun passUrl_isVersion3AtLevelM() {
        val modules = assertNotNull(qrModules(passUrl, ErrorCorrectionLevel.M))
        assertEquals(29, modules.size)
        assertTrue(modules.all { it.size == 29 })
    }

    @Test
    fun finderPatterns_sitInThreeCorners() {
        val modules = assertNotNull(qrModules(passUrl))
        val last = modules.size - 1
        // Each finder's outer ring is dark at its corners and its 3x3 centre is dark.
        assertTrue(modules[0][0] && modules[0][6] && modules[6][0] && modules[3][3])
        assertTrue(modules[0][last] && modules[3][last - 3])
        assertTrue(modules[last][0] && modules[last - 3][3])
        // The light ring inside the outer ring.
        assertTrue(!modules[1][1] && !modules[5][5])
    }

    @Test
    fun encoding_isDeterministic() {
        val first = assertNotNull(qrModules(passUrl)).map { it.toList() }
        val second = assertNotNull(qrModules(passUrl)).map { it.toList() }
        assertEquals(first, second)
    }

    @Test
    fun levelM_isSparserThanLevelH() {
        val m = assertNotNull(qrModules(passUrl, ErrorCorrectionLevel.M))
        val h = assertNotNull(qrModules(passUrl, ErrorCorrectionLevel.H))
        assertTrue(m.size < h.size)
    }
}
