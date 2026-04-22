package compose.project.click.click.proximity

import android.os.Build

actual fun isSimulatorOrEmulatorRuntime(): Boolean {
    val fp = Build.FINGERPRINT
    val model = Build.MODEL
    val product = Build.PRODUCT
    return fp.startsWith("generic") ||
        fp.startsWith("unknown") ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for x86", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        product.contains("sdk_google", ignoreCase = true) ||
        product.contains("google_sdk", ignoreCase = true) ||
        product.contains("sdk", ignoreCase = true) ||
        product.contains("sdk_x86", ignoreCase = true) ||
        product.contains("vbox86p", ignoreCase = true) ||
        product.contains("emulator", ignoreCase = true) ||
        product.contains("simulator", ignoreCase = true)
}
