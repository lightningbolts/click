package compose.project.click.click.data.repository

data class NativeAppleSignInPayload(
    val idToken: String,
    val nonce: String? = null,
)

expect suspend fun requestNativeAppleSignInPayload(): Result<NativeAppleSignInPayload?>
