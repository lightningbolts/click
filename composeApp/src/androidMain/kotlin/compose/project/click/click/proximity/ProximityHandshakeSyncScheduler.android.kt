package compose.project.click.click.proximity

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import compose.project.click.click.data.storage.androidStorageContextOrThrow

actual fun scheduleProximityHandshakeSync() {
    val ctx = androidStorageContextOrThrow()
    val req = OneTimeWorkRequestBuilder<PendingHandshakeSyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(ctx).enqueueUniqueWork(
        "click_pending_proximity_handshake",
        ExistingWorkPolicy.REPLACE,
        req,
    )
}
