package compose.project.click.click.platform

import android.os.Build

actual fun thisDeviceLabel(): String? =
    runCatching { friendlyDeviceLabel(Build.MANUFACTURER, Build.MODEL) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
