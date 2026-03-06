package compose.project.click.click.notifications

data class PendingPushToken(
    val token: String,
    val platform: String
)

expect fun savePendingPushToken(token: String, platform: String)

expect fun consumePendingPushToken(): PendingPushToken?