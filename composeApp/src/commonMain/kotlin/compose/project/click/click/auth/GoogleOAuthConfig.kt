package compose.project.click.click.auth

/**
 * Google OAuth client IDs for native sign-in.
 *
 * iOS native GoogleSignIn requires an **iOS** OAuth client from the **same Google Cloud project**
 * as [WEB_CLIENT_ID]. Set [IOS_CLIENT_ID] after creating one at:
 * https://console.cloud.google.com/apis/credentials (bundle: compose.project.click.click)
 *
 * Also add both IDs (web first, then iOS) to Supabase → Authentication → Google → Client IDs,
 * and enable "Skip nonce check".
 */
object GoogleOAuthConfig {
    /** Web client — Supabase verifies ID tokens against this audience. */
    const val WEB_CLIENT_ID =
        "530817233802-3ki7usecs885vvag9uq92ubu5hgkv2sp.apps.googleusercontent.com"

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
