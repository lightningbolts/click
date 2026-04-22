package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted

@Composable
actual fun rememberMicrophonePermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    val session = remember { AVAudioSession.sharedInstance() }

    return { onComplete ->
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> onComplete()
            AVAudioSessionRecordPermissionDenied -> {
                openApplicationSystemSettings()
                onComplete()
            }
            else -> {
                session.requestRecordPermission { _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        onComplete()
                    }
                }
            }
        }
    }
}
