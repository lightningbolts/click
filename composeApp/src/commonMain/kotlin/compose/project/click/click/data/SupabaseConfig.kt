package compose.project.click.click.data

import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import compose.project.click.click.data.storage.TokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


object SupabaseConfig {
    private const val SUPABASE_URL = "https://lrgcwnmcscimkmslihxp.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxyZ2N3bm1jc2NpbWttc2xpaHhwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjA1MTgwNDksImV4cCI6MjA3NjA5NDA0OX0.-_LAhv-gUeCvViwTt8QZwM13U7jMIgTbiMZDkFf-oXk"

    /** Public anon key for Edge Function `apikey` header (matches embedded client key). */
    val supabaseAnonApiKey: String get() = SUPABASE_ANON_KEY

    fun functionUrl(functionName: String): String = "$SUPABASE_URL/functions/v1/$functionName"

    /** Persists GoTrue session JSON + PKCE verifier via platform secure storage (not in-memory [Settings]). */
    private val authSessionSettings: Settings by lazy { createSupabaseAuthSettings() }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = "click"
                host = "login"
                // Use SettingsSessionManager for persistent session storage
                sessionManager = SettingsSessionManager(authSessionSettings)
                // Ensure tokens are auto-refreshed and auto-loaded
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    /**
     * Start observing session status changes from the Supabase SDK and
     * sync every refreshed token into our dual-storage TokenStorage.
     *
     * This eliminates the root cause of "random logouts": the SDK refreshes
     * tokens into SettingsSessionManager but our Keychain / EncryptedPrefs
     * (TokenStorage) would go stale. Now they stay in sync.
     */
    fun startSessionSync(tokenStorage: TokenStorage) {
        syncScope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val session = status.session
                        println("SupabaseConfig: Session authenticated/refreshed — syncing to TokenStorage")
                        tokenStorage.saveTokens(
                            jwt = session.accessToken,
                            refreshToken = session.refreshToken,
                            expiresAt = session.expiresAt?.toEpochMilliseconds(),
                            tokenType = session.tokenType
                        )
                    }
                    is SessionStatus.NotAuthenticated -> {
                        println("SupabaseConfig: Session not authenticated")
                        // Don't clear tokens here — let explicit sign-out handle that
                    }
                    else -> {
                        println("SupabaseConfig: Session status: $status")
                    }
                }
            }
        }
    }
}

