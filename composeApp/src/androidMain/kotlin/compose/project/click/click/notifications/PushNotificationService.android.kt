package compose.project.click.click.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import compose.project.click.click.data.repository.PushTokenRepository
import compose.project.click.click.data.storage.createEncryptedSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// Plaintext (legacy, migrated away). Kept only so existing installs can be
// read once and cleared during the first post-upgrade launch. New writes go
// exclusively to PUSH_NOTIFICATION_PREFS_ENCRYPTED.
private const val PUSH_NOTIFICATION_PREFS = "push_notification_prefs"
private const val PUSH_NOTIFICATION_PREFS_ENCRYPTED = "push_notification_prefs_encrypted"
private const val KEY_PUSH_USER_ID = "push_user_id"

// Per-tokenType pending slots (R0.6 — Android now matches iOS semantics).
// We intentionally do NOT use a single shared slot because FCM (standard)
// and any secondary channel (e.g. voip) can both arrive before login and
// must both be preserved.
private const val PENDING_TOKEN_TYPES_KEY = "pending_push_token_types"
private fun pendingTokenKey(type: String) = "pending_push_token_$type"
private fun pendingPlatformKey(type: String) = "pending_push_platform_$type"

// Legacy single-slot keys (kept for one-time migration).
private const val LEGACY_KEY_PENDING_PUSH_TOKEN = "pending_push_token"
private const val LEGACY_KEY_PENDING_PUSH_PLATFORM = "pending_push_platform"
private const val LEGACY_KEY_PENDING_PUSH_TOKEN_TYPE = "pending_push_token_type"

private const val PUSH_PERMISSION_REQUEST_CODE = 4012

internal object AndroidPushNotificationRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var appInForeground: Boolean = false

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
        }
    }

    fun requireContext(): Context? = applicationContext

    fun currentActivity(): Activity? = currentActivityRef?.get()

    fun setAppInForeground(isForeground: Boolean) {
        appInForeground = isForeground
    }

    fun isAppInForeground(): Boolean = appInForeground

    private fun encryptedPrefs(context: Context) =
        createEncryptedSharedPreferences(context, PUSH_NOTIFICATION_PREFS_ENCRYPTED)

    fun storeUserId(userId: String) {
        val context = applicationContext ?: run {
            // R0.6: surface the previously-silent drop; upload-on-login can't
            // recover if the userId was never persisted.
            println("AndroidPushNotificationRuntime: storeUserId skipped — context not initialized")
            return
        }
        encryptedPrefs(context)
            .edit()
            .putString(KEY_PUSH_USER_ID, userId)
            .apply()
    }

    fun storedUserId(): String? {
        val context = applicationContext ?: return null
        return encryptedPrefs(context).getString(KEY_PUSH_USER_ID, null)
    }

    fun savePendingToken(token: String, platform: String, tokenType: String) {
        val context = applicationContext ?: run {
            println("AndroidPushNotificationRuntime: savePendingToken dropped — context not initialized")
            return
        }
        val prefs = encryptedPrefs(context)
        val existingTypes = prefs.getStringSet(PENDING_TOKEN_TYPES_KEY, null).orEmpty().toMutableSet()
        existingTypes += tokenType
        prefs.edit()
            .putString(pendingTokenKey(tokenType), token)
            .putString(pendingPlatformKey(tokenType), platform)
            .putStringSet(PENDING_TOKEN_TYPES_KEY, existingTypes)
            .apply()
    }

    fun consumePendingTokens(): List<PendingPushToken> {
        val context = applicationContext ?: return emptyList()
        val prefs = encryptedPrefs(context)

        // One-shot migration from the legacy single-slot format. Tokens live
        // in the OLD plaintext prefs file; read, normalize, and wipe.
        migrateLegacyPendingTokensIfNeeded(context, prefs)

        val types = prefs.getStringSet(PENDING_TOKEN_TYPES_KEY, null).orEmpty()
        if (types.isEmpty()) return emptyList()
        val result = ArrayList<PendingPushToken>(types.size)
        val editor = prefs.edit()
        for (type in types) {
            val token = prefs.getString(pendingTokenKey(type), null)
            val platform = prefs.getString(pendingPlatformKey(type), null)
            if (token != null && platform != null) {
                result += PendingPushToken(token = token, platform = platform, tokenType = type)
            }
            editor.remove(pendingTokenKey(type))
            editor.remove(pendingPlatformKey(type))
        }
        editor.remove(PENDING_TOKEN_TYPES_KEY).apply()
        return result
    }

    private fun migrateLegacyPendingTokensIfNeeded(context: Context, encrypted: SharedPreferences) {
        val legacyPrefs = context.getSharedPreferences(PUSH_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
        val legacyToken = legacyPrefs.getString(LEGACY_KEY_PENDING_PUSH_TOKEN, null)
        val legacyPlatform = legacyPrefs.getString(LEGACY_KEY_PENDING_PUSH_PLATFORM, null)
        val legacyType = legacyPrefs.getString(LEGACY_KEY_PENDING_PUSH_TOKEN_TYPE, null) ?: "standard"
        val legacyUserId = legacyPrefs.getString(KEY_PUSH_USER_ID, null)

        if (legacyToken != null && legacyPlatform != null) {
            val existingTypes = encrypted.getStringSet(PENDING_TOKEN_TYPES_KEY, null).orEmpty().toMutableSet()
            existingTypes += legacyType
            encrypted.edit()
                .putString(pendingTokenKey(legacyType), legacyToken)
                .putString(pendingPlatformKey(legacyType), legacyPlatform)
                .putStringSet(PENDING_TOKEN_TYPES_KEY, existingTypes)
                .apply()
        }
        if (legacyUserId != null && encrypted.getString(KEY_PUSH_USER_ID, null).isNullOrBlank()) {
            encrypted.edit().putString(KEY_PUSH_USER_ID, legacyUserId).apply()
        }
        if (legacyToken != null || legacyUserId != null) {
            legacyPrefs.edit()
                .remove(LEGACY_KEY_PENDING_PUSH_TOKEN)
                .remove(LEGACY_KEY_PENDING_PUSH_PLATFORM)
                .remove(LEGACY_KEY_PENDING_PUSH_TOKEN_TYPE)
                .remove(KEY_PUSH_USER_ID)
                .apply()
        }
    }
}

private class AndroidPushNotificationService : PushNotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pushTokenRepository = PushTokenRepository()

    override fun registerToken(userId: String) {
        AndroidPushNotificationRuntime.storeUserId(userId)
        requestPermission()

        // Upload any tokens that arrived before login
        val pending = consumePendingPushTokens()
        if (pending.isNotEmpty()) {
            scope.launch {
                pending.forEach { pendingToken ->
                    pushTokenRepository.savePushToken(
                        userId = userId,
                        token = pendingToken.token,
                        platform = pendingToken.platform,
                        tokenType = pendingToken.tokenType,
                    )
                }
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("PushNotificationService.android: Failed to fetch FCM token: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val token = task.result
            if (token.isNullOrBlank()) return@addOnCompleteListener

            scope.launch {
                pushTokenRepository.savePushToken(
                    userId = userId,
                    token = token,
                    platform = "android"
                )
            }
        }
    }

    override fun requestPermission() {
        val activity = AndroidPushNotificationRuntime.currentActivity() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        activity.runOnUiThread {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PUSH_PERMISSION_REQUEST_CODE
            )
        }
    }
}

internal fun uploadAndroidPushToken(token: String) {
    val userId = AndroidPushNotificationRuntime.storedUserId()
    if (userId.isNullOrBlank()) {
        println("PushNotificationService.android: No user id yet — saving token as pending for later upload")
        savePendingPushToken(token, "android", "standard")
        return
    }

    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        PushTokenRepository().savePushToken(
            userId = userId,
            token = token,
            platform = "android"
        )
    }
}

fun initPushNotificationService(context: Context, activity: Activity? = null) {
    AndroidPushNotificationRuntime.init(context, activity)
}

actual fun createPushNotificationService(): PushNotificationService = AndroidPushNotificationService()