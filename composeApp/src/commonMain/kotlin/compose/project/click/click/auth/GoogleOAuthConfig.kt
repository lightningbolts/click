package compose.project.click.click.auth

/**
 * Google OAuth client IDs for native sign-in.
 *
 * Native Google Sign-In requires platform OAuth clients from the **same Google Cloud project**
 * as [WEB_CLIENT_ID]. Create them at:
 * https://console.cloud.google.com/apis/credentials
 * (Android package / iOS bundle: compose.project.click.click)
 *
 * Android Credential Manager uses [WEB_CLIENT_ID] as `serverClientId` (ID token audience).
 * The [ANDROID_CLIENT_ID] must exist in GCP with the app's package name + SHA-1, but is not
 * passed into the Credential Manager request.
 *
 * Also add web + iOS IDs to Supabase → Authentication → Google → Client IDs,
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

    /**
     * iOS OAuth client — must be from the **same Google Cloud project** as [WEB_CLIENT_ID].
     * Update this after creating an iOS client in that project (bundle: compose.project.click.click).
     */
    const val IOS_CLIENT_ID =
        "530817233802-crnehf5a9duauov4vos4lgsijkgingdj.apps.googleusercontent.com"

    private fun projectNumber(clientId: String): String? {
        val trimmed = clientId.trim()
        if (trimmed.isEmpty() || !trimmed.endsWith(".apps.googleusercontent.com")) return null
        return trimmed.substringBefore('-').takeIf { it.isNotEmpty() }
    }

    /** True when [IOS_CLIENT_ID] is set and shares a GCP project with [WEB_CLIENT_ID]. */
    fun iosNativeSignInConfigured(): Boolean {
        val iosProject = projectNumber(IOS_CLIENT_ID) ?: return false
        val webProject = projectNumber(WEB_CLIENT_ID) ?: return false
        return iosProject == webProject
    }

    fun iosNativeSignInMisconfigurationMessage(): String? {
        if (IOS_CLIENT_ID.isBlank()) {
            return "Google native sign-in is not configured. Add an iOS OAuth client ID from the same Google Cloud project as the web client."
        }
        if (iosNativeSignInConfigured()) return null
        val iosProject = projectNumber(IOS_CLIENT_ID) ?: return "Google iOS client ID is invalid."
        val webProject = projectNumber(WEB_CLIENT_ID) ?: return "Google web client ID is invalid."
        return "Google Sign-In is misconfigured in the app: iOS client is from GCP project $iosProject, " +
            "but GIDServerClientID / WEB_CLIENT_ID is set to a web client from project $webProject. " +
            "Open Google Cloud → Click → Web client 1, copy its Client ID, and use that as WEB_CLIENT_ID and GIDServerClientID. " +
            "Supabase Auth → Google must use the same web client ID and secret."
    }

    fun reversedIosClientUrlScheme(): String? {
        val projectAndSuffix = projectNumber(IOS_CLIENT_ID)?.let { num ->
            val suffix = IOS_CLIENT_ID.removePrefix("$num-").removeSuffix(".apps.googleusercontent.com")
            if (suffix.isEmpty()) null else "$num-$suffix"
        } ?: return null
        return "com.googleusercontent.apps.$projectAndSuffix"
    }
}
