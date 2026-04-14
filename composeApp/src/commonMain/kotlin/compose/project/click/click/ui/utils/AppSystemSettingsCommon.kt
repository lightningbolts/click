package compose.project.click.click.ui.utils

/**
 * Cross-platform runtime flags set from Android/iOS entry points before [App] composes.
 */
object AppSystemSettings {
    /** True for debuggable / non-release binaries when configured by the host app. */
    var isDebugMode: Boolean = false
}
