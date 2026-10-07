package compose.project.click.click.ui.theme // pragma: allowlist secret

import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Me → Appearance (iOS `SettingsStore.Appearance`). System follows the phone's dark theme. */
enum class AppearanceMode(
    val label: String,
) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
    ;

    fun resolveDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark -> true
        }

    /** Stored in the existing dark-mode pref: absent means System. */
    fun toStoredDarkMode(): Boolean? =
        when (this) {
            System -> null
            Light -> false
            Dark -> true
        }

    companion object {
        fun fromStoredDarkMode(stored: Boolean?): AppearanceMode =
            when (stored) {
                null -> System
                true -> Dark
                false -> Light
            }
    }
}

object AppAppearance {
    private val _mode = MutableStateFlow(AppearanceMode.System)
    val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

    suspend fun restore(tokenStorage: TokenStorage) {
        _mode.value = AppearanceMode.fromStoredDarkMode(tokenStorage.getDarkModeEnabled())
    }

    suspend fun set(
        mode: AppearanceMode,
        tokenStorage: TokenStorage,
    ) {
        _mode.value = mode
        tokenStorage.saveDarkModeEnabled(mode.toStoredDarkMode())
    }
}
