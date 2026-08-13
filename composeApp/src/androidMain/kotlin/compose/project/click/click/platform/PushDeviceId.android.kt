package compose.project.click.click.platform

import compose.project.click.click.data.storage.createEncryptedSharedPreferences
import compose.project.click.click.notifications.AndroidPushNotificationRuntime
import java.util.UUID

private const val PREFS = "click_push_device_id"
private const val KEY = "device_id"

private var inMemoryFallbackId: String? = null

actual fun persistentPushDeviceId(): String {
    val context =
        AndroidPushNotificationRuntime.requireContext()
            ?: return inMemoryFallbackId ?: UUID.randomUUID().toString().also { inMemoryFallbackId = it }
    val prefs = createEncryptedSharedPreferences(context, PREFS)
    val existing = prefs.getString(KEY, null)?.trim().orEmpty()
    if (existing.isNotEmpty()) return existing
    val created = UUID.randomUUID().toString()
    prefs.edit().putString(KEY, created).apply()
    return created
}
