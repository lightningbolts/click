package compose.project.click.click.data.repository

data class NativeGoogleSignInPayload(
    val idToken: String,
    val nonce: String? = null,
    val accessToken: String? = null,
)

/** Returns a Google ID token from the platform native sign-in sheet, or null when unsupported. */
expect suspend fun requestNativeGoogleSignInPayload(): Result<NativeGoogleSignInPayload?>
