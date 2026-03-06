package compose.project.click.click.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AudioToolbox.AudioServicesPlaySystemSound

actual object CallRingtonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ringJob: Job? = null

    actual fun startOutgoing() {
        startLoop(soundId = 1003u, pauseMs = 1200)
    }

    actual fun startIncoming() {
        startLoop(soundId = 1005u, pauseMs = 1500)
    }

    actual fun stop() {
        ringJob?.cancel()
        ringJob = null
    }

    private fun startLoop(soundId: UInt, pauseMs: Long) {
        stop()
        ringJob = scope.launch {
            while (true) {
                AudioServicesPlaySystemSound(soundId)
                delay(pauseMs)
            }
        }
    }
}