package compose.project.click.click.notifications // pragma: allowlist secret

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import compose.project.click.click.MainActivity // pragma: allowlist secret
import compose.project.click.click.data.storage.androidStorageContextOrThrow // pragma: allowlist secret
import java.util.concurrent.TimeUnit

private const val REMINDERS_CHANNEL_ID = "click_plans_events"
private const val REMINDERS_CHANNEL_NAME = "Plans & events"
private const val WORK_PREFIX = "click_reminder."

private const val KEY_ID = "id"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
private const val KEY_TARGET = "target"
private const val KEY_CHAT_ID = "chat_id"
private const val KEY_CONNECTION_ID = "connection_id"
private const val KEY_BEACON_ID = "beacon_id"
private const val TARGET_CHAT = "chat"
private const val TARGET_EVENT = "event"

private fun canPostNotifications(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun ensureRemindersChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(REMINDERS_CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(REMINDERS_CHANNEL_ID, REMINDERS_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminders for plans you're going to and events you saved."
        },
    )
}

/** WorkManager-backed reminders: survive process death and reboots; timing is best-effort (Doze). */
actual object LocalReminderScheduler {
    private fun contextOrNull(): Context? = runCatching { androidStorageContextOrThrow() }.getOrNull()

    actual fun schedule(reminder: LocalReminder): Boolean {
        val context = contextOrNull() ?: return false
        val delayMs = reminder.fireAtEpochMs - System.currentTimeMillis()
        if (delayMs <= 0 || !canPostNotifications(context)) {
            cancel(reminder.id)
            return false
        }
        val targetData =
            when (val target = reminder.target) {
                is LocalReminderTarget.Chat ->
                    arrayOf(KEY_TARGET to TARGET_CHAT, KEY_CHAT_ID to target.chatId, KEY_CONNECTION_ID to target.connectionId)
                is LocalReminderTarget.Event -> arrayOf(KEY_TARGET to TARGET_EVENT, KEY_BEACON_ID to target.beaconId)
            }
        val request =
            OneTimeWorkRequestBuilder<LocalReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_ID to reminder.id,
                        KEY_TITLE to reminder.title,
                        KEY_BODY to reminder.body,
                        *targetData,
                    ),
                ).build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(WORK_PREFIX + reminder.id, ExistingWorkPolicy.REPLACE, request)
        return true
    }

    actual fun cancel(id: String) {
        val context = contextOrNull() ?: return
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + id)
    }
}

/** Posts one scheduled reminder. Re-checks permission at fire time; never prompts. */
class LocalReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!canPostNotifications(context)) return Result.success()
        ensureRemindersChannel(context)
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val launchIntent =
            when (inputData.getString(KEY_TARGET)) {
                TARGET_CHAT ->
                    MainActivity.createChatDeepLinkIntent(
                        context = context,
                        chatId = inputData.getString(KEY_CHAT_ID).orEmpty(),
                        connectionId = inputData.getString(KEY_CONNECTION_ID).orEmpty(),
                    )
                TARGET_EVENT -> MainActivity.createEventDeepLinkIntent(context, inputData.getString(KEY_BEACON_ID).orEmpty())
                else -> context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return Result.success()
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                id.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, REMINDERS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, 0, notification) }
        return Result.success()
    }
}
