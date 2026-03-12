package compose.project.click.click.notifications

data class PendingPushToken(
    val token: String,
    val platform: String,
    val tokenType: String = "standard",
)

expect fun savePendingPushToken(token: String, platform: String, tokenType: String = "standard")

expect fun consumePendingPushTokens(): List<PendingPushToken>