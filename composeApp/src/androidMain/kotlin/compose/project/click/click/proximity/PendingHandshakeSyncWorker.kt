package compose.project.click.click.proximity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import compose.project.click.click.data.AppDataManager

/**
 * Flushes the offline [PendingHandshake] queue when network is available (WorkManager constraint).
 */
class PendingHandshakeSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AppDataManager.flushPendingProximityHandshakesFromBackgroundWorker()
        return Result.success()
    }
}
