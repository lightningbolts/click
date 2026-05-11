package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.darwin.NSObject

private class IosBluetoothPermissionProbe(
    private val onResult: (Boolean) -> Unit,
) : NSObject(), CBCentralManagerDelegateProtocol {
    private var finished = false
    private var manager: CBCentralManager? = null

    fun start() {
        manager = CBCentralManager(delegate = this, queue = null)
    }

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        if (central.state == CBManagerStateUnknown || central.state == CBManagerStateResetting) return
        finish(central.state != CBManagerStateUnauthorized)
    }

    private fun finish(granted: Boolean) {
        if (finished) return
        finished = true
        manager?.stopScan()
        manager = null
        onResult(granted)
    }
}

@Composable
actual fun rememberProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit) {
    val session = remember { AVAudioSession.sharedInstance() }
    var bluetoothProbe by remember { mutableStateOf<IosBluetoothPermissionProbe?>(null) }

    fun requestBluetooth(onResult: (Boolean) -> Unit) {
        val probe = IosBluetoothPermissionProbe { granted ->
            bluetoothProbe = null
            if (!granted) {
                openApplicationSystemSettings()
            }
            onResult(granted)
        }
        bluetoothProbe = probe
        probe.start()
    }

    return { onResult ->
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> requestBluetooth(onResult)
            AVAudioSessionRecordPermissionDenied -> {
                openApplicationSystemSettings()
                onResult(false)
            }
            else -> {
                session.requestRecordPermission { micGranted: Boolean ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (micGranted) {
                            requestBluetooth(onResult)
                        } else {
                            openApplicationSystemSettings()
                            onResult(false)
                        }
                    }
                }
            }
        }
    }
}
