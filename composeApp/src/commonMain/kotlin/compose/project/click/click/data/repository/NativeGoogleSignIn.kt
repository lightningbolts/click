package compose.project.click.click.data.repository

data class NativeGoogleSignInPayload(
    val idToken: String,
)

/** Returns a Google ID token from the platform native sign-in sheet, or null when unsupported. */
expect suspend fun requestNativeGoogleSignInPayload(): Result<NativeGoogleSignInPayload?>
