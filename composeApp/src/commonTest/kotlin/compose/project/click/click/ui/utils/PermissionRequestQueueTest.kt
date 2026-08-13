package compose.project.click.click.ui.utils

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionRequestQueueTest {
    @BeforeTest
    fun setUp() {
        PermissionRequestQueue.resetForTests()
    }

    @AfterTest
    fun tearDown() {
        PermissionRequestQueue.resetForTests()
    }

    @Test
    fun enqueueSerializesTwoOverlappingRequests() {
        val order = mutableListOf<String>()
        PermissionRequestQueue.enqueue(
            PermissionKind.Location,
            onComplete = { order.add("location") },
        )
        PermissionRequestQueue.enqueue(
            PermissionKind.Camera,
            onComplete = { order.add("camera") },
        )

        assertEquals(PermissionKind.Location, PermissionRequestQueue.current.value?.kind)
        assertEquals(emptyList(), order)

        PermissionRequestQueue.completeCurrent()
        assertEquals(listOf("location"), order)
        assertEquals(PermissionKind.Camera, PermissionRequestQueue.current.value?.kind)

        PermissionRequestQueue.completeCurrent()
        assertEquals(listOf("location", "camera"), order)
        assertNull(PermissionRequestQueue.current.value)
    }

    @Test
    fun dismissCompletesCallerAndAdvancesQueue() {
        val order = mutableListOf<String>()
        PermissionRequestQueue.enqueue(
            PermissionKind.Microphone,
            onComplete = { order.add("mic") },
        )
        PermissionRequestQueue.enqueue(
            PermissionKind.Calendar,
            onComplete = { order.add("cal") },
        )

        PermissionRequestQueue.dismissCurrent()
        assertEquals(listOf("mic"), order)
        assertEquals(PermissionKind.Calendar, PermissionRequestQueue.current.value?.kind)

        PermissionRequestQueue.dismissCurrent()
        assertEquals(listOf("mic", "cal"), order)
        assertNull(PermissionRequestQueue.current.value)
    }

    @Test
    fun proximityDismissReportsNotGranted() {
        var granted: Boolean? = null
        PermissionRequestQueue.enqueue(
            PermissionKind.ProximityHardware,
            onResult = { granted = it },
        )
        PermissionRequestQueue.dismissCurrent()
        assertEquals(false, granted)
        assertNull(PermissionRequestQueue.current.value)
    }
}
