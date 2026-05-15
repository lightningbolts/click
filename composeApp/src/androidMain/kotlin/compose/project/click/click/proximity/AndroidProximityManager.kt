package compose.project.click.click.proximity

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
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
import android.provider.Settings
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val PROXIMITY_DEBOUNCE_WINDOW_MS: Long = 4_000L

@SuppressLint("MissingPermission")
class AndroidProximityManager(
    private val context: Context,
) : ProximityManager {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var gattServer: BluetoothGattServer? = null
    private var tokenCharacteristic: BluetoothGattCharacteristic? = null
    private val activeClientGatts = ConcurrentHashMap.newKeySet<BluetoothGatt>()
    private val connectingDeviceAddresses = ConcurrentHashMap.newKeySet<String>()
    private var audioTrack: AudioTrack? = null

    override fun supportsTapExchange(): Boolean {
        val a = adapter ?: return false
        if (missingBluetoothRuntimePermissions().isNotEmpty()) return true
        return a.isEnabled && advertiser != null && scanner != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || a.isLeExtendedAdvertisingSupported || a.isMultipleAdvertisementSupported)
    }

    override fun capabilityNote(): String {
        if (adapter == null) return "Bluetooth is not available on this device."
        if (advertiser == null) return "This device cannot send BLE advertisements needed for Tap to Connect."
        return "Uses Bluetooth Low Energy and short-range audio tones (including 18.5 kHz) to find nearby taps."
    }

    private fun enforceBluetoothRuntimePermissions() {
        val missing = missingHardwareRuntimePermissions()
        if (missing.isNotEmpty()) {
            throw ProximityHardwarePermissionException(
                "Missing proximity hardware permission: ${missing.joinToString()}",
            )
        }
    }

    private fun missingHardwareRuntimePermissions(): List<String> {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun missingBluetoothRuntimePermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        ).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
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
        enforceBluetoothRuntimePermissions()
        stopBroadcastOnly()
        val adv = advertiser ?: return
        val payload = runCatching { buildGattTokenPayload(ephemeralToken) }.getOrElse { return }
        val serviceUuid = ParcelUuid.fromString(CLICK_SERVICE_UUID)
        startGattServer(payload)

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
                    .addServiceUuid(serviceUuid)
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
        enforceBluetoothRuntimePermissions()
        val tokens = ConcurrentHashMap.newKeySet<String>()
        stopScanOnly()
        val sc = scanner
        val serviceUuid = ParcelUuid.fromString(CLICK_SERVICE_UUID)
        val cb = if (sc != null) {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val device = result?.device ?: return
                    connectAndReadToken(device, tokens)
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
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(serviceUuid)
                        .build(),
                )
                sc.startScan(filters, settings, cb)
            }
        }
        coroutineScope {
            val audio = async(Dispatchers.IO) { collectAudioToken(PROXIMITY_DEBOUNCE_WINDOW_MS, tokens) }
            delay(PROXIMITY_DEBOUNCE_WINDOW_MS)
            audio.await()
        }
        withContext(Dispatchers.Main) {
            stopScanOnly()
        }
        return tokens.sorted()
    }

    private fun startGattServer(tokenPayload: ByteArray) {
        val manager = bluetoothManager ?: return
        val serviceUuid = UUID.fromString(CLICK_SERVICE_UUID)
        val characteristicUuid = UUID.fromString(CLICK_TOKEN_CHARACTERISTIC_UUID)
        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            value = tokenPayload
        }
        service.addCharacteristic(characteristic)
        tokenCharacteristic = characteristic
        val callback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?,
            ) {
                val value = tokenCharacteristic?.value ?: ByteArray(0)
                val slice = if (offset in value.indices) {
                    value.copyOfRange(offset, value.size)
                } else if (offset == value.size) {
                    ByteArray(0)
                } else {
                    null
                }
                val status = if (slice != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
                gattServer?.sendResponse(device, requestId, status, offset, slice)
            }
        }
        gattServer = manager.openGattServer(context, callback)?.also { server ->
            server.addService(service)
        }
    }

    private fun connectAndReadToken(device: BluetoothDevice, sink: MutableSet<String>) {
        val address = device.address ?: return
        if (!connectingDeviceAddresses.add(address)) return
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    activeClientGatts.remove(gatt)
                    connectingDeviceAddresses.remove(address)
                    runCatching { gatt.close() }
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    activeClientGatts.remove(gatt)
                    connectingDeviceAddresses.remove(address)
                    runCatching { gatt.disconnect() }
                    runCatching { gatt.close() }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finishGatt(gatt, address)
                    return
                }
                val characteristic = gatt
                    .getService(UUID.fromString(CLICK_SERVICE_UUID))
                    ?.getCharacteristic(UUID.fromString(CLICK_TOKEN_CHARACTERISTIC_UUID))
                if (characteristic == null || !gatt.readCharacteristic(characteristic)) {
                    finishGatt(gatt, address)
                }
            }

            @Deprecated("Deprecated in Android API 33 but still called on older runtimes.")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (
                    status == BluetoothGatt.GATT_SUCCESS &&
                    characteristic.uuid == UUID.fromString(CLICK_TOKEN_CHARACTERISTIC_UUID)
                ) {
                    parseGattTokenPayload(characteristic.value)?.let { sink.add(it) }
                }
                finishGatt(gatt, address)
            }
        }
        val gatt = device.connectGatt(context, false, callback)
        if (gatt != null) {
            activeClientGatts.add(gatt)
        } else {
            connectingDeviceAddresses.remove(address)
        }
    }

    private fun finishGatt(gatt: BluetoothGatt, address: String) {
        activeClientGatts.remove(gatt)
        connectingDeviceAddresses.remove(address)
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
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

    private fun stopBroadcastOnly() {
        val adv = advertiser
        val cb = advertiseCallback
        if (adv != null && cb != null) {
            runCatching { adv.stopAdvertising(cb) }
        }
        advertiseCallback = null
        runCatching { gattServer?.close() }
        gattServer = null
        tokenCharacteristic = null
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
    }

    override fun stopAll() {
        stopScanOnly()
        activeClientGatts.forEach { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        activeClientGatts.clear()
        connectingDeviceAddresses.clear()
        stopBroadcastOnly()
    }
}

@Composable
actual fun rememberProximityManager(): ProximityManager {
    val context = LocalContext.current
    return remember(context) {
        if (isSimulatorOrEmulatorRuntime()) {
            MockProximityManager()
        } else {
            AndroidProximityManager(context)
        }
    }
}
