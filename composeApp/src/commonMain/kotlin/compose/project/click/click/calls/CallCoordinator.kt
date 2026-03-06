package compose.project.click.click.calls

import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage

class CallCoordinator(
    private val callApiClient: CallApiClient = CallApiClient(),
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    suspend fun fetchCallToken(
        roomName: String,
        participantName: String,
        userId: String
    ): Result<CallApiClient.LiveKitTokenResponse> {
        val authToken = tokenStorage.getJwt()
            ?: return Result.failure(IllegalStateException("Missing auth token"))

        return callApiClient.fetchToken(
            authToken = authToken,
            roomName = roomName,
            participantName = participantName,
            userId = userId
        )
    }
}