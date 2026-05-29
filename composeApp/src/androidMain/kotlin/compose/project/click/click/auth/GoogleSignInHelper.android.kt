package compose.project.click.click.auth

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import compose.project.click.click.calls.AndroidCallRuntime
import compose.project.click.click.data.repository.NativeGoogleSignInPayload

suspend fun requestNativeGoogleSignInPayload(): Result<NativeGoogleSignInPayload?> {
    val activity = AndroidCallRuntime.currentActivity()
        ?: return Result.failure(IllegalStateException("Google sign-in requires an active activity."))

    return runCatching {
        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(GoogleOAuthConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(
            context = activity,
            request = request,
        )
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleCredential.idToken.trim()
            if (idToken.isEmpty()) {
                throw IllegalStateException("Google ID token was empty.")
            }
            NativeGoogleSignInPayload(idToken = idToken)
        } else {
            throw IllegalStateException("Unexpected Google credential type.")
        }
    }.fold(
        onSuccess = { payload -> Result.success(payload) },
        onFailure = { error ->
            when (error) {
                is GetCredentialCancellationException ->
                    Result.failure(Exception("Google sign-in was canceled."))
                else -> Result.failure(error)
            }
        },
    )
}
