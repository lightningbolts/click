package compose.project.click.click.data.repository

import compose.project.click.click.auth.requestNativeGoogleSignInPayload as requestAndroidGoogleSignIn

actual suspend fun requestNativeGoogleSignInPayload(): Result<NativeGoogleSignInPayload?> =
    requestAndroidGoogleSignIn()
