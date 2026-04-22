package compose.project.click.click.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import compose.project.click.click.data.storage.androidStorageContextOrThrow
import compose.project.click.click.data.storage.createEncryptedSharedPreferences

private const val SUPABASE_GOTRUE_PREFS_NAME = "click_supabase_gotrue"

internal actual fun createSupabaseAuthSettings(): Settings =
    SharedPreferencesSettings(
        createEncryptedSharedPreferences(
            androidStorageContextOrThrow(),
            SUPABASE_GOTRUE_PREFS_NAME,
        ),
    )
