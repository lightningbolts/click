package compose.project.click.click.data

import com.russhwolf.settings.Settings

/**
 * Platform [Settings] used by Supabase GoTrue [io.github.jan.supabase.auth.SettingsSessionManager]
 * for serialized session JSON and PKCE verifier keys. Must persist across launches and app updates.
 */
internal expect fun createSupabaseAuthSettings(): Settings
