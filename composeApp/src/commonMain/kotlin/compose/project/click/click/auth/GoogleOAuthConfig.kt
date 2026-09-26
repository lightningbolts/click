package compose.project.click.click.auth

/**
 * Google OAuth client IDs for native sign-in.
 *
 * Native Google Sign-In requires platform OAuth clients from the **same Google Cloud project**
 * as [WEB_CLIENT_ID]. Create them at:
 * https://console.cloud.google.com/apis/credentials
 * (Android package: compose.project.click.click)
 *
 * Android Credential Manager uses [WEB_CLIENT_ID] as `serverClientId` (ID token audience).
 * The [ANDROID_CLIENT_ID] must exist in GCP with the app's package name + SHA-1, but is not
 * passed into the Credential Manager request.
 *
 * Also add the web client ID to Supabase → Authentication → Google → Client IDs,
 * and enable "Skip nonce check".
 */
object GoogleOAuthConfig {
    /** Web client — Supabase verifies ID tokens against this audience. */
    const val WEB_CLIENT_ID =
        "530817233802-3ki7usecs885vvag9uq92ubu5hgkv2sp.apps.googleusercontent.com"

    /**
     * Android OAuth client (package: compose.project.click.click + signing SHA-1).
     * Required in Google Cloud for Play Services to authorize the app; not used as
     * `serverClientId` — see [WEB_CLIENT_ID] in GoogleSignInHelper.android.kt.
     */
    const val ANDROID_CLIENT_ID =
        "530817233802-lhuv57k9593qqbgbhkruv6p9r56sfnr9.apps.googleusercontent.com"
}
