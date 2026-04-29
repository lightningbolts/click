package compose.project.click.click.proximity

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.provider.Settings
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val CLICK_SERVICE_PARCEL_UUID: ParcelUuid =
    ParcelUuid.fromString(CLICK_PRIMARY_SERVICE_UUID_STRING)

private val CLICK_PRIMARY_SERVICE_UUID: UUID =
    UUID.fromString(CLICK_PRIMARY_SERVICE_UUID_STRING)

private val CLICK_TOKEN_CHARACTERISTIC_UUID: UUID =
    UUID.fromString(CLICK_TOKEN_CHARACTERISTIC_UUID_STRING)

@SuppressLint("MissingPermission")
class AndroidProximityManager(
    private val context: Context,
) : ProximityManager {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var audioTrack: AudioTrack? = null
    private var gattSupervisor = SupervisorJob()
    private var gattScope = CoroutineScope(gattSupervisor + Dispatchers.Main.immediate)
    private val openGatts = Collections.newSetFromMap(ConcurrentHashMap<BluetoothGatt, Boolean>())
    private val gattAttemptedAddresses = ConcurrentHashMap.newKeySet<String>()

    private fun assertBleRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (missing.isNotEmpty()) {
            throw ProximityBluetoothPermissionException(
                "Missing BLE permissions: ${missing.joinToString()}. Grant them in Settings to use Tap to Connect.",
            )
        }
    }

    override fun supportsTapExchange(): Boolean {
        val a = adapter ?: return false
        return a.isEnabled && advertiser != null && scanner != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || a.isLeExtendedAdvertisingSupported || a.isMultipleAdvertisementSupported)
    }

    override fun capabilityNote(): String {
        if (adapter == null) return "Bluetooth is not available on this device."
        if (advertiser == null) return "This device cannot send BLE advertisements needed for Tap to Connect."
        return "Uses Bluetooth Low Energy and short-range audio tones (including 18.5 kHz) to find nearby taps."
    }

    override fun openRadiosSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        val ok = runCatching {
            if (context is Activity) {
                context.startActivity(intent)
            } else {
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        }.isSuccess
        if (!ok) {
            runCatching {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        stopAll()
        assertBleRuntimePermissions()
        val adv = advertiser ?: return
        val payload = runCatching { buildBleManufacturerPayload(ephemeralToken) }.getOrElse { return }
        val tokenUuid = ParcelUuid.fromString(encodeBleTokenIntoAdvertisedUuidString(ephemeralToken))

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val cb = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                advertiseCallback = cb
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()
                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(CLICK_BLE_MANUFACTURER_ID, payload)
                    .addServiceUuid(CLICK_SERVICE_PARCEL_UUID)
                    .addServiceUuid(tokenUuid)
                    .build()
                adv.startAdvertising(settings, data, cb)
                cont.invokeOnCancellation { runCatching { adv.stopAdvertising(cb) } }
            }
        }

        val pcm = buildHandshakeAudioPcm(ephemeralToken)
        if (pcm.isNotEmpty()) {
            playPcmMono(pcm)
        }
    }

    override suspend fun startHandshakeListening(): List<String> {
        assertBleRuntimePermissions()
        val tokens = ConcurrentHashMap.newKeySet<String>()
        stopScanOnly()
        gattAttemptedAddresses.clear()
        val sc = scanner
        val cb = if (sc != null) {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    ingestScanResult(result, tokens)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { onScanResult(0, it) }
                }
            }
        } else {
            null
        }
        if (sc != null && cb != null) {
            withContext(Dispatchers.Main) {
                scanCallback = cb
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0L)
                    .build()
                val filter = ScanFilter.Builder()
                    .setServiceUuid(CLICK_SERVICE_PARCEL_UUID)
                    .build()
                sc.startScan(listOf(filter), settings, cb)
            }
        }
        coroutineScope {
            val audio = async(Dispatchers.IO) { collectAudioToken(3000L, tokens) }
            delay(3000L)
            audio.await()
        }
        withContext(Dispatchers.Main) {
            stopScanOnly()
            disconnectGatt()
        }
        return tokens.sorted()
    }

    private fun ingestScanResult(result: ScanResult?, tokens: MutableSet<String>) {
        val record = result?.scanRecord ?: return
        val fromMfg = record.getManufacturerSpecificData(CLICK_BLE_MANUFACTURER_ID)
            ?.let { parseBleManufacturerPayload(it) }
        if (fromMfg != null) {
            tokens.add(fromMfg)
            return
        }
        val uuids = record.serviceUuids
        if (uuids != null) {
            for (pu in uuids) {
                decodeBleTokenFromAdvertisedUuidString(pu.uuid.toString())?.let {
                    tokens.add(it)
                    return
                }
            }
        }
        val device = result.device ?: return
        val addr = device.address ?: return
        val hasPrimary = uuids?.any { it.uuid == CLICK_PRIMARY_SERVICE_UUID } == true
        if (hasPrimary && gattAttemptedAddresses.add(addr)) {
            gattScope.launch {
                readTokenViaGatt(device, tokens)
            }
        }
    }

    private suspend fun readTokenViaGatt(device: BluetoothDevice, tokens: MutableSet<String>) {
        withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                var finished = false
                fun finishOnce() {
                    if (!finished) {
                        finished = true
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            openGatts.add(gatt)
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            runCatching { gatt.close() }
                            openGatts.remove(gatt)
                            finishOnce()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            gatt.disconnect()
                            return
                        }
                        val svc = gatt.getService(CLICK_PRIMARY_SERVICE_UUID) ?: run {
                            gatt.disconnect()
                            return
                        }
                        val ch = svc.getCharacteristic(CLICK_TOKEN_CHARACTERISTIC_UUID) ?: run {
                            gatt.disconnect()
                            return
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.readCharacteristic(ch)
                        } else {
                            @Suppress("DEPRECATION")
                            gatt.readCharacteristic(ch)
                        }
                    }

                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int,
                    ) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                @Suppress("DEPRECATION")
                                characteristic.value?.let { parseBleManufacturerPayload(it) }?.let { tokens.add(it) }
                            }
                            gatt.disconnect()
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int,
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                parseBleManufacturerPayload(value)?.let { tokens.add(it) }
                            }
                            gatt.disconnect()
                        }
                    }
                }
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(
                        context,
                        false,
                        callback,
                        BluetoothDevice.TRANSPORT_LE,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(context, false, callback)
                }
                cont.invokeOnCancellation {
                    runCatching {
                        gatt.disconnect()
                        gatt.close()
                    }
                }
            }
        }
    }

    private fun disconnectGatt() {
        val copy = openGatts.toMutableSet()
        openGatts.clear()
        for (g in copy) {
            runCatching {
                g.disconnect()
                g.close()
            }
        }
    }

    private suspend fun collectAudioToken(durationMs: Long, sink: MutableSet<String>) {
        val rate = 44_100
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(rate, channel, encoding)
        if (minBuf <= 0) return
        val bufferSize = (minBuf * 2).coerceAtLeast(rate / 5)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            channel,
            encoding,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        val totalSamples = (rate * durationMs / 1000).toInt()
        val acc = ShortArray(totalSamples)
        var written = 0
        record.startRecording()
        try {
            val chunk = ShortArray(bufferSize / 2)
            val endAt = System.currentTimeMillis() + durationMs
            while (written < totalSamples && System.currentTimeMillis() < endAt) {
                val n = record.read(chunk, 0, chunk.size.coerceAtMost(totalSamples - written))
                if (n <= 0) break
                chunk.copyInto(acc, written, 0, n)
                written += n
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        if (written < rate / 5) return
        val slice = acc.copyOf(written)
        if (pcmRms(slice) < 0.002) return
        decodeAllHandshakeTokensFromPcmMono(slice).forEach { sink.add(it) }
    }

    private suspend fun playPcmMono(pcm: ShortArray) = withContext(Dispatchers.IO) {
        val rate = 44_100
        val channelMask = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(rate, channelMask, encoding)
        if (minBuf <= 0) return@withContext
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(rate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes((minBuf * 2).coerceAtLeast(pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                rate,
                channelMask,
                encoding,
                (minBuf * 2).coerceAtLeast(pcm.size * 2),
                AudioTrack.MODE_STATIC,
            )
        }
        audioTrack = track
        runCatching {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.write(pcm, 0, pcm.size)
                track.play()
                val playMs = (pcm.size * 1000L / rate) + 80L
                delay(playMs)
                runCatching { track.stop() }
            }
        }
        track.release()
        audioTrack = null
    }

    private fun stopScanOnly() {
        val sc = scanner ?: return
        val cb = scanCallback ?: return
        runCatching { sc.stopScan(cb) }
        scanCallback = null
    }

    override fun stopAll() {
        stopScanOnly()
        gattSupervisor.cancel()
        gattSupervisor = SupervisorJob()
        gattScope = CoroutineScope(gattSupervisor + Dispatchers.Main.immediate)
        disconnectGatt()
        val adv = advertiser
        val cb = advertiseCallback
        if (adv != null && cb != null) {
            runCatching { adv.stopAdvertising(cb) }
        }
        advertiseCallback = null
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
    }
}

@Composable
actual fun rememberProximityManager(): ProximityManager {
    val context = LocalContext.current
    // Directive C11: do not inject [MockProximityManager] on emulators — the UI graph
    // must reflect real (or empty) database state. [MockProximityManager] is still
    // available for unit tests via direct instantiation.
    return remember(context) { AndroidProximityManager(context) }
}
