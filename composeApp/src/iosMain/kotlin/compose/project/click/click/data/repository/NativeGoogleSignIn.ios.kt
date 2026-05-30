@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.data.repository

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val GOOGLE_SIGN_IN_START = "ClickGoogleSignInStart"
private const val GOOGLE_SIGN_IN_DID_COMPLETE = "ClickGoogleSignInDidComplete"

actual suspend fun requestNativeGoogleSignInPayload(): Result<NativeGoogleSignInPayload?> =
    suspendCancellableCoroutine { cont ->
        var settled = false
        val center = NSNotificationCenter.defaultCenter
        var observer: Any? = null

        fun finish(result: Result<NativeGoogleSignInPayload?>) {
            if (settled) return
            settled = true
            observer?.let { center.removeObserver(it) }
            cont.resume(result)
        }

        observer = center.addObserverForName(
            name = GOOGLE_SIGN_IN_DID_COMPLETE,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            val userInfo = notification?.userInfo ?: run {
                finish(Result.failure(IllegalStateException("Google sign-in returned no payload.")))
                return@addObserverForName
            }
            val error = userInfo["error"] as? String
            if (!error.isNullOrBlank()) {
                finish(Result.failure(Exception(error)))
                return@addObserverForName
            }
            val idToken = userInfo["idToken"] as? String
            if (idToken.isNullOrBlank()) {
                finish(Result.failure(IllegalStateException("Google ID token was unavailable.")))
                return@addObserverForName
            }
            val nonce = (userInfo["nonce"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val accessToken = (userInfo["accessToken"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            finish(
                Result.success(
                    NativeGoogleSignInPayload(
                        idToken = idToken.trim(),
                        nonce = nonce,
                        accessToken = accessToken,
                    ),
                ),
            )
        }

        fun postStart() {
            center.postNotificationName(aName = GOOGLE_SIGN_IN_START, `object` = null)
        }

        if (NSThread.isMainThread) {
            postStart()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                if (!settled) postStart()
            }
        }

        cont.invokeOnCancellation {
            settled = true
            observer?.let { center.removeObserver(it) }
        }
    }
