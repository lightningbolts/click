package compose.project.click.click.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

private const val SUPABASE_AUTH_SUITE_NAME = "click_supabase_auth_settings"

internal actual fun createSupabaseAuthSettings(): Settings {
    val defaults = NSUserDefaults(suiteName = SUPABASE_AUTH_SUITE_NAME)
        ?: NSUserDefaults.standardUserDefaults
    return NSUserDefaultsSettings(defaults)
}
