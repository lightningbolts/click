package compose.project.click.click.auth // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHydrationPolicyTest {

    @Test
    fun shouldImportStoredSession_onlyWhenSdkEmpty() {
        assertTrue(SessionHydrationPolicy.shouldImportStoredSession(sdkHasSession = false))
        assertFalse(SessionHydrationPolicy.shouldImportStoredSession(sdkHasSession = true))
    }

    @Test
    fun shouldSyncSdkTokensToStorage_whenMissingOrDiverged() {
        assertTrue(
            SessionHydrationPolicy.shouldSyncSdkTokensToStorage(
                storedRefresh = null,
                sdkRefresh = "sdk-refresh",
            ),
        )
        assertTrue(
            SessionHydrationPolicy.shouldSyncSdkTokensToStorage(
                storedRefresh = "stale-refresh",
                sdkRefresh = "sdk-refresh",
            ),
        )
        assertFalse(
            SessionHydrationPolicy.shouldSyncSdkTokensToStorage(
                storedRefresh = "sdk-refresh",
                sdkRefresh = "sdk-refresh",
            ),
        )
        assertFalse(
            SessionHydrationPolicy.shouldSyncSdkTokensToStorage(
                storedRefresh = "anything",
                sdkRefresh = null,
            ),
        )
    }
}
