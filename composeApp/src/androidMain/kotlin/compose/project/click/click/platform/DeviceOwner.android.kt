package compose.project.click.click.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun deviceKindLabel(): String {
    val config = AndroidActivityRuntime.appContext()?.resources?.configuration
    val tablet =
        config != null &&
            config.smallestScreenWidthDp >= 600 &&
            (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    return if (tablet) "Android tablet" else "Android phone"
}

/**
 * Uses the framework [BiometricPrompt] so it works from the app's ComponentActivity, allowing the
 * screen-lock PIN, pattern or password as well as biometrics. Accepting the screen lock needs API 29
 * (`setDeviceCredentialAllowed`) or 30 (`setAllowedAuthenticators`), so on Android 9 and earlier
 * approval fails closed and the emailed link remains the way in.
 */
actual suspend fun authenticateDeviceOwner(reason: String) {
    val activity = AndroidActivityRuntime.currentActivity() ?: throw DeviceOwnerAuthUnavailable("No foreground activity")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw DeviceOwnerAuthUnavailable("Android 10 or later is required")
    val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (keyguard?.isDeviceSecure != true) throw DeviceOwnerAuthUnavailable("Set a screen lock to approve devices")
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val builder =
                BiometricPrompt
                    .Builder(activity)
                    .setTitle("Approve new sign-in")
                    .setDescription(reason)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            builder.build().authenticate(
                signal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (!cont.isActive) return
                        val cancelled =
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        cont.resumeWithException(
                            if (cancelled) DeviceOwnerAuthCancelled() else DeviceOwnerAuthUnavailable(errString.toString()),
                        )
                    }
                    // onAuthenticationFailed is one bad attempt; the prompt stays up for another try.
                },
            )
        }
    }
}
