package compose.project.click.click.telemetry

import android.content.Context
import compose.project.click.click.data.storage.androidStorageContextOrThrow

private const val PRODUCT_TELEMETRY_PREFS = "click_product_telemetry"

/** Plain prefs: the install flag, the last open day and queued event bodies hold no PII. */
actual fun platformEventsTelemetryStorage(): EventsTelemetryStorage {
    val context = runCatching { androidStorageContextOrThrow() }.getOrNull() ?: return InMemoryEventsTelemetryStorage()
    val prefs = context.getSharedPreferences(PRODUCT_TELEMETRY_PREFS, Context.MODE_PRIVATE)
    return object : EventsTelemetryStorage {
        override fun get(key: String): String? = prefs.getString(key, null)

        override fun set(
            key: String,
            value: String?,
        ) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }
}
