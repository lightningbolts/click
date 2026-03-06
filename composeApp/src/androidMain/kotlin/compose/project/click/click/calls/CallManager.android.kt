package compose.project.click.click.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private const val CALL_PERMISSION_REQUEST_CODE = 4013

private object AndroidCallRuntime {
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    fun init(context: Context, activity: Activity? = null) {
        applicationContext = context.applicationContext
        if (activity != null) {
            currentActivityRef = WeakReference(activity)
        }
    }

    fun currentActivity(): Activity? = currentActivityRef?.get()
}

actual class CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    actual val callState: StateFlow<CallState> = _callState.asStateFlow()

    actual fun startCall(roomName: String, token: String, wsUrl: String, videoEnabled: Boolean) {
        val activity = AndroidCallRuntime.currentActivity()
        if (activity == null) {
            _callState.value = CallState.Ended("Call context unavailable")
            return
        }

        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (videoEnabled) add(Manifest.permission.CAMERA)
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            activity.runOnUiThread {
                ActivityCompat.requestPermissions(
                    activity,
                    missingPermissions.toTypedArray(),
                    CALL_PERMISSION_REQUEST_CODE
                )
            }
            _callState.value = CallState.Ended("Camera or microphone permission required")
            return
        }

        scope.launch {
            _callState.value = CallState.Connecting
            delay(250)
            _callState.value = CallState.Connected(hasVideo = videoEnabled)
        }
    }

    actual fun endCall() {
        _callState.value = CallState.Ended()
        scope.launch {
            delay(150)
            _callState.value = CallState.Idle
        }
    }
}

fun initCallManager(context: Context, activity: Activity? = null) {
    AndroidCallRuntime.init(context, activity)
}

actual fun createCallManager(): CallManager = CallManager()