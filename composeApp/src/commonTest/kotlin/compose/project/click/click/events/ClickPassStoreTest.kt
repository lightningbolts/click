package compose.project.click.click.events

import compose.project.click.click.core.AppSessionHooks
import compose.project.click.click.core.InMemoryDiskCacheBackend
import compose.project.click.click.core.UserDiskCache
import compose.project.click.click.core.platformDiskCacheBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Passes live in [UserDiskCache] per user and are gone after sign-out (sheet 12 §2, D17). */
class ClickPassStoreTest {
    private val backend = InMemoryDiskCacheBackend()
    private val pass = ClickPass(credentialUrl = "https://joinclick.co/e/b1?pass=abc", code = "K7P-4QX")

    @BeforeTest
    fun install() {
        UserDiskCache.backendFactory = { backend }
        ClickPassStore.reset()
    }

    @AfterTest
    fun restore() {
        ClickPassStore.reset()
        UserDiskCache.backendFactory = ::platformDiskCacheBackend
    }

    @Test
    fun passIsStoredPerUserAndReloads() {
        ClickPassStore.bindUser("u1")
        ClickPassStore.put("b1", pass)
        assertTrue(backend.files.containsKey("u1" to ClickPassStore.CACHE_KEY))

        ClickPassStore.bindUser("u2")
        assertNull(ClickPassStore.cached("b1"))
        ClickPassStore.bindUser("u1")
        assertEquals(pass, ClickPassStore.cached("b1"))
    }

    @Test
    fun signOutClearsTheStoredPass() =
        runTest {
            ClickPassStore.bindUser("u1")
            ClickPassStore.put("b1", pass)

            AppSessionHooks.onSessionEnded()

            assertNull(ClickPassStore.cached("b1"))
            assertTrue(backend.files.isEmpty())
            ClickPassStore.bindUser("u1")
            assertNull(ClickPassStore.cached("b1"))
        }
}
