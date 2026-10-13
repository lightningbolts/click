package compose.project.click.click.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A transparent host for [BiometricPrompt], which needs a [FragmentActivity] (MainActivity is a
 * ComponentActivity). Started for one request; finishes as soon as the prompt ends (sheet 10 §A1).
 */
class OwnerAuthActivity : FragmentActivity() {
    private var requestKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_REQUEST)
        val result = key?.let { pending[it] }
        if (key == null || result == null || result.isCompleted) {
            finishQuietly()
            return
        }
        requestKey = key
        running[key] = WeakReference(this)
        // Built on every create so a recreated activity gets the result; shown only once.
        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        complete(OwnerAuthResult.Success)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        complete(ownerAuthResultForError(errorCode))
                    }
                    // onAuthenticationFailed (a finger that didn't match) leaves the prompt up.
                },
            )
        if (savedInstanceState == null) {
            prompt.authenticate(
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle("Click")
                    .setDescription(intent.getStringExtra(EXTRA_REASON).orEmpty())
                    .setAllowedAuthenticators(ownerAuthenticators(Build.VERSION.SDK_INT))
                    .build(),
            )
        }
    }

    private fun complete(result: OwnerAuthResult) {
        requestKey?.let { pending[it]?.complete(result) }
        finishQuietly()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // Gone without an answer (the system finished it): a cancel, never a success.
        if (isFinishing) {
            requestKey?.let { key ->
                pending[key]?.complete(OwnerAuthResult.Cancelled)
                running.remove(key)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REQUEST = "owner_auth_request"
        private const val EXTRA_REASON = "owner_auth_reason"
        private val pending = ConcurrentHashMap<String, CompletableDeferred<OwnerAuthResult>>()
        private val running = ConcurrentHashMap<String, WeakReference<OwnerAuthActivity>>()

        /** False when there's no biometric or screen lock to confirm with. */
        fun canAuthenticate(context: Context): Boolean =
            runCatching {
                BiometricManager.from(context).canAuthenticate(ownerAuthenticators(Build.VERSION.SDK_INT)) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false)

        /** Shows the prompt over [context] and waits for it. Cancelling the caller closes the prompt. */
        suspend fun authenticate(
            context: Context,
            reason: String,
        ): OwnerAuthResult {
            if (!canAuthenticate(context)) return OwnerAuthResult.Unavailable
            val key = UUID.randomUUID().toString()
            val result = CompletableDeferred<OwnerAuthResult>()
            pending[key] = result
            try {
                val started =
                    withContext(Dispatchers.Main) {
                        runCatching {
                            val intent =
                                Intent(context, OwnerAuthActivity::class.java)
                                    .putExtra(EXTRA_REQUEST, key)
                                    .putExtra(EXTRA_REASON, reason)
                            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            if (context is Activity) {
                                @Suppress("DEPRECATION")
                                context.overridePendingTransition(0, 0)
                            }
                        }.isSuccess
                    }
                if (!started) return OwnerAuthResult.Unavailable
                return result.await()
            } finally {
                pending.remove(key)
                withContext(NonCancellable + Dispatchers.Main) {
                    running.remove(key)?.get()?.takeIf { !it.isFinishing }?.finishQuietly()
                }
            }
        }
    }
}

/**
 * API 30+: a strong biometric or the screen lock. API 24–29: any biometric or the screen lock
 * (strong + credential isn't supported there; androidx uses KeyguardManager on 28 and below).
 */
internal fun ownerAuthenticators(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** User, system and negative-button cancels go back to asking; anything else fails closed. */
internal fun ownerAuthResultForError(errorCode: Int): OwnerAuthResult =
    when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        -> OwnerAuthResult.Cancelled
        else -> OwnerAuthResult.Unavailable
    }
