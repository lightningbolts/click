package compose.project.click.click.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private var appContext: Context? = null

fun initAppSystemSettings(context: Context) {
    appContext = context.applicationContext
}

actual fun openApplicationSystemSettings() {
    val ctx = appContext ?: return
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}
