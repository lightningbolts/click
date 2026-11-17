package compose.project.click.click.platform

import java.util.Locale

actual fun platformUsesFahrenheit(): Boolean {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val override = runCatching { locale.getUnicodeLocaleType("mu") }.getOrNull()
    return usesFahrenheitFor(locale.country, override)
}
