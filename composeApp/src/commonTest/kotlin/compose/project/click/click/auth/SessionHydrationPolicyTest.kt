package compose.project.click.click.auth // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHydrationPolicyTest {
    @Test
    fun shouldImportStoredSession_whenSdkEmpty() {
        assertTrue(SessionHydrationPolicy.shouldImportStoredSession(sdkHasSession = false))
    }

    @Test
    fun shouldImportStoredSession_doesNotClobberMatchingSdkSession() {
        assertFalse(
            SessionHydrationPolicy.shouldImportStoredSession(
                sdkHasSession = true,
                sdkRefresh = "same",
                storedRefresh = "same",
                sdkAccessExpMs = 1_000L,
                storedAccessExpMs = 2_000L,
            ),
        )
        assertFalse(SessionHydrationPolicy.shouldImportStoredSession(sdkHasSession = true))
    }

    @Test
    fun shouldImportStoredSession_whenStorageWonLaterRotation() {
        assertTrue(
            SessionHydrationPolicy.shouldImportStoredSession(
                sdkHasSession = true,
                sdkRefresh = "old-refresh",
                storedRefresh = "new-refresh",
                sdkAccessExpMs = 1_000L,
                storedAccessExpMs = 5_000L,
            ),
        )
    }

    @Test
    fun shouldImportStoredSession_doesNotImportOlderStorageOverSdk() {
        assertFalse(
            SessionHydrationPolicy.shouldImportStoredSession(
                sdkHasSession = true,
                sdkRefresh = "new-refresh",
                storedRefresh = "old-refresh",
                sdkAccessExpMs = 5_000L,
                storedAccessExpMs = 1_000L,
            ),
        )
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
