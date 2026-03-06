package compose.project.click.click.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import compose.project.click.click.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val PUSH_NOTIFICATION_PREFS = "push_notification_prefs"
private const val KEY_PUSH_USER_ID = "push_user_id"
private const val KEY_PENDING_PUSH_TOKEN = "pending_push_token"
private const val KEY_PENDING_PUSH_PLATFORM = "pending_push_platform"
private const val PUSH_PERMISSION_REQUEST_CODE = 4012

internal object AndroidPushNotificationRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
        }
    }

    fun requireContext(): Context? = applicationContext

    fun currentActivity(): Activity? = currentActivityRef?.get()

    fun storeUserId(userId: String) {
        val context = applicationContext ?: return
        context.getSharedPreferences(PUSH_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PUSH_USER_ID, userId)
            .apply()
    }

    fun storedUserId(): String? {
        val context = applicationContext ?: return null
        return context.getSharedPreferences(PUSH_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PUSH_USER_ID, null)
    }

    fun savePendingToken(token: String, platform: String) {
        val context = applicationContext ?: return
        context.getSharedPreferences(PUSH_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_PUSH_TOKEN, token)
            .putString(KEY_PENDING_PUSH_PLATFORM, platform)
            .apply()
    }

    fun consumePendingToken(): PendingPushToken? {
        val context = applicationContext ?: return null
        val prefs = context.getSharedPreferences(PUSH_NOTIFICATION_PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_PENDING_PUSH_TOKEN, null) ?: return null
        val platform = prefs.getString(KEY_PENDING_PUSH_PLATFORM, null) ?: return null
        prefs.edit()
            .remove(KEY_PENDING_PUSH_TOKEN)
            .remove(KEY_PENDING_PUSH_PLATFORM)
            .apply()
        return PendingPushToken(token = token, platform = platform)
    }
}

private class AndroidPushNotificationService : PushNotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pushTokenRepository = PushTokenRepository()

    override fun registerToken(userId: String) {
        AndroidPushNotificationRuntime.storeUserId(userId)
        requestPermission()
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
        println("PushNotificationService.android: Skipping token upload because no user id is cached")
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