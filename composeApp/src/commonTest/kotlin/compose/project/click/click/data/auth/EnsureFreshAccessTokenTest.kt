package compose.project.click.click.data.auth // pragma: allowlist secret

import compose.project.click.click.data.storage.FakeTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnsureFreshAccessTokenTest {
    @Test
    fun jwtExpEpochMs_parsesStandardPayload() {
        // {"exp":1700000000} base64url
        val payload = "eyJleHAiOjE3MDAwMDAwMDB9"
        val jwt = "hdr.$payload.sig"
        val exp = EnsureFreshAccessToken.jwtExpEpochMs(jwt)
        assertEquals(1_700_000_000_000L, exp)
    }

    @Test
    fun jwtExpEpochMs_rejectsMalformed() {
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs(null))
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs(""))
        assertNull(EnsureFreshAccessToken.jwtExpEpochMs("not-a-jwt"))
    }

    @Test
    fun isAccessTokenFresh_rejectsExpired() {
        val payload = "eyJleHAiOjF9" // {"exp":1}
        val jwt = "hdr.$payload.sig"
        assertFalse(EnsureFreshAccessToken.isAccessTokenFresh(jwt, nowMs = 2_000L))
    }

    @Test
    fun isAccessTokenFresh_acceptsFutureExp() {
        val payload = "eyJleHAiOjE3MDAwMDAwMDB9" // {"exp":1700000000}
        val jwt = "hdr.$payload.sig"
        assertTrue(EnsureFreshAccessToken.isAccessTokenFresh(jwt, nowMs = 1_699_999_000_000L))
    }

    @Test
    fun sdkAccessIsFresh_rejectsBlank() {
        assertFalse(EnsureFreshAccessToken.isAccessTokenFresh(null))
        assertFalse(EnsureFreshAccessToken.isAccessTokenFresh(""))
    }

    @Test
    fun refreshSkew_isPositive() {
        assertTrue(EnsureFreshAccessToken.REFRESH_SKEW_MS > 0L)
        assertNotNull(EnsureFreshAccessToken.REFRESH_SKEW_MS)
    }

    @Test
    fun get_returnsStoredJwtWhenExpiresAtHasHeadroom() =
        runTest {
            SessionResumeGate.markCompleted()
            try {
                val jwt = "hdr.eyJleHAiOjQxMDI0NDQ4MDB9.sig"
                val storage =
                    FakeTokenStorage(
                        jwt = jwt,
                        expiresAtEpochMs = 4_102_444_800_000L,
                    )
                assertEquals(
                    jwt,
                    EnsureFreshAccessToken.get(
                        tokenStorage = storage,
                        sdkSessionProvider = { null },
                    ),
                )
            } finally {
                SessionResumeGate.resetForTests()
            }
        }

    @Test
    fun get_prefersFreshLiveSdkTokenOverFreshStoredToken() =
        runTest {
            SessionResumeGate.markCompleted()
            try {
                val storedJwt = "hdr.eyJleHAiOjQxMDI0NDQ4MDB9.sig"
                val liveJwt = "hdr.eyJleHAiOjQxMDI0NDUwMDB9.sig"
                val storage =
                    FakeTokenStorage(
                        jwt = storedJwt,
                        refreshToken = "stored-refresh",
                        expiresAtEpochMs = 4_102_444_800_000L,
                    )

                assertEquals(
                    liveJwt,
                    EnsureFreshAccessToken.get(
                        tokenStorage = storage,
                        sdkSessionProvider = {
                            EnsureFreshAccessToken.SessionSnapshot(
                                accessToken = liveJwt,
                                refreshToken = "live-refresh",
                                expiresAtMs = 4_102_450_000_000L,
                                tokenType = "bearer",
                            )
                        },
                    ),
                )
            } finally {
                SessionResumeGate.resetForTests()
            }
        }

    @Test
    fun get_rechecksLiveSdkAfterLaterStorageImportFails() =
        runTest {
            SessionResumeGate.markCompleted()
            try {
                val storedJwt = "hdr.eyJleHAiOjQxMDI0NTAwMDB9.sig"
                val oldLiveJwt = "hdr.eyJleHAiOjQxMDI0NDAwMDB9.sig"
                val rotatedLiveJwt = "hdr.eyJleHAiOjQxMDI0NjAwMDB9.sig"
                val storage =
                    FakeTokenStorage(
                        jwt = storedJwt,
                        refreshToken = "stored-refresh",
                        expiresAtEpochMs = 4_102_450_000_000L,
                    )
                val snapshots =
                    listOf(
                        EnsureFreshAccessToken.SessionSnapshot(
                            accessToken = oldLiveJwt,
                            refreshToken = "old-live-refresh",
                            expiresAtMs = 4_102_440_000_000L,
                            tokenType = "bearer",
                        ),
                        EnsureFreshAccessToken.SessionSnapshot(
                            accessToken = rotatedLiveJwt,
                            refreshToken = "rotated-live-refresh",
                            expiresAtMs = 4_102_460_000_000L,
                            tokenType = "bearer",
                        ),
                    )
                var snapshotIndex = 0

                assertEquals(
                    rotatedLiveJwt,
                    EnsureFreshAccessToken.get(
                        tokenStorage = storage,
                        sdkSessionProvider = { snapshots.getOrNull(snapshotIndex++) },
                        storedSessionImporter = { false },
                    ),
                )
            } finally {
                SessionResumeGate.resetForTests()
            }
        }
}
