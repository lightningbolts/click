package compose.project.click.click.calls

import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.api.LiveKitTokenPostBody
import compose.project.click.click.data.api.LiveKitTokenResponse

class CallCoordinator(
    private val apiClient: ApiClient = ApiClient(),
) {
    suspend fun fetchCallToken(
        connectionId: String,
        roomName: String,
        participantName: String,
    ): Result<LiveKitTokenResponse> {
        return apiClient.postLiveKitToken(
            LiveKitTokenPostBody(
                connectionId = connectionId,
                roomName = roomName,
                participantName = participantName,
            ),
        )
    }
}
