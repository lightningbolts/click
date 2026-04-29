package compose.project.click.click.proximity

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
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
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
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
        val missing = missingBluetoothRuntimePermissions()
        if (missing.isNotEmpty()) {
            throw SecurityException("Missing Bluetooth permission: ${missing.joinToString()}")
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
        stopAll()
        val adv = advertiser ?: return
        val payload = runCatching { buildBleManufacturerPayload(ephemeralToken) }.getOrElse { return }
        val dynamicServiceUuid = ParcelUuid.fromString(buildProximityServiceUuidString(ephemeralToken))

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
                    .setConnectable(false)
                    .build()
                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(CLICK_BLE_MANUFACTURER_ID, payload)
                    .addServiceUuid(dynamicServiceUuid)
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
        val cb = if (sc != null) {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.scanRecord?.serviceUuids?.forEach { uuid ->
                        parseProximityServiceUuidString(uuid?.uuid?.toString())?.let { tokens.add(it) }
                    }
                    val raw = result?.scanRecord?.getManufacturerSpecificData(CLICK_BLE_MANUFACTURER_ID)
                    parseBleManufacturerPayload(raw)?.let { tokens.add(it) }
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
                sc.startScan(emptyList(), settings, cb)
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
