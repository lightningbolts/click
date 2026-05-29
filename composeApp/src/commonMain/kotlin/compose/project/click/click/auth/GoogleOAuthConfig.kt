package compose.project.click.click.auth

/**
 * Google OAuth Web Client ID — used by native sign-in (Android Credential Manager / iOS GoogleSignIn)
 * so Supabase can verify the returned ID token via [io.github.jan.supabase.auth.providers.builtin.IDToken].
 */
object GoogleOAuthConfig {
    const val WEB_CLIENT_ID =
        "113180501985-62rus3q9pfa1ksspocjvn7j9c0oqp8fo.apps.googleusercontent.com"
}
