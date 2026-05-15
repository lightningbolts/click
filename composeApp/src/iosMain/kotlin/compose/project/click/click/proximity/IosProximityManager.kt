package compose.project.click.click.proximity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBUUID
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.memcpy
import kotlin.coroutines.resume

/** `kAudioFormatLinearPCM` — four-char code `lpcm`. */
private const val K_AUDIO_FORMAT_LINEAR_PCM: UInt = 1819304813u
private const val PROXIMITY_DEBOUNCE_WINDOW_MS: Long = 4_000L

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    platform.posix.memcpy(out.refTo(0), base, len.convert())
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        val m = NSMutableData()
        m.setLength(size.convert())
        val dest = m.mutableBytes ?: error("mutableBytes")
        memcpy(dest, pinned.addressOf(0), size.convert())
        m
    }

@OptIn(ExperimentalForeignApi::class)
private fun prepareProximityAudioSession() {
    runCatching<Unit> {
        val session = AVAudioSession.sharedInstance()
        val options: ULong = AVAudioSessionCategoryOptionMixWithOthers or
            AVAudioSessionCategoryOptionDefaultToSpeaker
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            withOptions = options,
            error = null,
        )
        session.setActive(true, error = null)
    }
}

private fun enforceProximityAudioPermission() {
    if (AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionDenied) {
        throw ProximityHardwarePermissionException("Missing proximity hardware permission: microphone")
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun writeBytesToPath(path: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    bytes.usePinned { pinned ->
        val f = fopen(path, "wb") ?: return
        try {
            fwrite(pinned.addressOf(0), 1u, bytes.size.convert(), f)
        } finally {
            fclose(f)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun readBytesFromPath(path: String): ByteArray? {
    val f = fopen(path, "rb") ?: return null
    try {
        fseek(f, 0, SEEK_END)
        val size = ftell(f).toInt()
        if (size <= 0) return null
        fseek(f, 0, SEEK_SET)
        val out = ByteArray(size)
        out.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.convert(), f)
        }
        return out
    } finally {
        fclose(f)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosProximityManager : ProximityManager {

    private var peripheralManager: CBPeripheralManager? = null
    private var centralManager: CBCentralManager? = null
    private var peripheralDelegate: NSObject? = null
    private var centralDelegate: NSObject? = null
    private var discoveredPeripheralDelegate: NSObject? = null
    private var tokenCharacteristic: CBMutableCharacteristic? = null
    private val connectingPeripheralIds = mutableSetOf<String>()
    private var audioPlayer: AVAudioPlayer? = null
    private var audioRecorder: AVAudioRecorder? = null

    /** Heard tokens (main-thread only mutations from BLE + Main post from audio). */
    private val heardTokens = mutableSetOf<String>()

    override fun supportsTapExchange(): Boolean = true

    override fun capabilityNote(): String =
        "Uses Bluetooth Low Energy and short-range audio tones (including 18.5 kHz) to find nearby taps."

    override fun openRadiosSettings() {
        // iOS does not expose a public deep-link to the Bluetooth radio toggle —
        // per directive Q4, fall back to the app's own Settings page via the shared
        // helper (which bypasses the `canOpenURL` probe).
        compose.project.click.click.ui.utils.openApplicationSystemSettings()
    }

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        enforceProximityAudioPermission()
        stopPeripheralOnly()
        val payload = runCatching { buildGattTokenPayload(ephemeralToken).toNSData() }.getOrElse { return }
        val serviceUuid = runCatching {
            CBUUID.UUIDWithString(CLICK_SERVICE_UUID)
        }.getOrElse { return }
        val characteristicUuid = CBUUID.UUIDWithString(CLICK_TOKEN_CHARACTERISTIC_UUID)
        prepareProximityAudioSession()

        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                fun finish() {
                    if (!resumed) {
                        resumed = true
                        cont.resume(Unit)
                    }
                }
                val del = object : NSObject(), CBPeripheralManagerDelegateProtocol {
                    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
                        if (peripheral.state == CBManagerStatePoweredOn) {
                            val characteristic = CBMutableCharacteristic(
                                type = characteristicUuid,
                                properties = CBCharacteristicPropertyRead,
                                value = payload,
                                permissions = CBAttributePermissionsReadable,
                            )
                            tokenCharacteristic = characteristic
                            val service = CBMutableService(type = serviceUuid, primary = true)
                            service.setCharacteristics(listOf(characteristic))
                            peripheral.addService(service)
                            val adv = NSMutableDictionary()
                            adv.setObject(
                                listOf(serviceUuid),
                                forKey = NSString.create(string = CBAdvertisementDataServiceUUIDsKey),
                            )
                            peripheral.startAdvertising(adv as Map<Any?, *>)
                        } else {
                            finish()
                        }
                    }

                    override fun peripheralManagerDidStartAdvertising(
                        peripheral: CBPeripheralManager,
                        error: NSError?,
                    ) {
                        finish()
                    }

                    override fun peripheralManager(
                        peripheral: CBPeripheralManager,
                        didReceiveReadRequest: CBATTRequest,
                    ) {
                        if (didReceiveReadRequest.characteristic.UUID.UUIDString != CLICK_TOKEN_CHARACTERISTIC_UUID.uppercase()) {
                            peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorInvalidOffset)
                            return
                        }
                        val value = tokenCharacteristic?.value ?: payload
                        val offset = didReceiveReadRequest.offset.toInt()
                        if (offset > value.length.toInt()) {
                            peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorInvalidOffset)
                            return
                        }
                        if (offset != 0) {
                            peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorInvalidOffset)
                            return
                        }
                        didReceiveReadRequest.value = value
                        peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorSuccess)
                    }
                }
                peripheralDelegate = del
                peripheralManager = CBPeripheralManager(delegate = del, queue = null)
                cont.invokeOnCancellation {
                    peripheralManager?.stopAdvertising()
                }
            }
        }

        val pcm = buildHandshakeAudioPcm(ephemeralToken)
        if (pcm.isEmpty()) return
        val wav = wrapPcmAsWav(pcm)
        withContext(Dispatchers.Default) {
            val path = NSTemporaryDirectory().trimEnd('/') + "/click_proximity_${kotlin.random.Random.nextLong()}.wav"
            writeBytesToPath(path, wav)
            val url = NSURL.fileURLWithPath(path)
            val player = AVAudioPlayer(contentsOfURL = url, error = null)
            audioPlayer = player
            player?.prepareToPlay()
            player?.play()
            val ms = (pcm.size * 1000L / 44_100L) + 120L
            delay(ms)
            player?.stop()
            audioPlayer = null
        }
    }

    override suspend fun startHandshakeListening(): List<String> {
        enforceProximityAudioPermission()
        heardTokens.clear()
        prepareProximityAudioSession()
        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                var started = false
                val serviceUuid = CBUUID.UUIDWithString(CLICK_SERVICE_UUID)
                val characteristicUuid = CBUUID.UUIDWithString(CLICK_TOKEN_CHARACTERISTIC_UUID)
                val peripheralDel = object : NSObject(), CBPeripheralDelegateProtocol {
                    override fun peripheral(
                        peripheral: CBPeripheral,
                        didDiscoverServices: NSError?,
                    ) {
                        peripheral.services?.forEach { service ->
                            val svc = service as? CBService ?: return@forEach
                            if (svc.UUID.UUIDString == CLICK_SERVICE_UUID.uppercase()) {
                                peripheral.discoverCharacteristics(listOf(characteristicUuid), forService = svc)
                            }
                        }
                    }

                    override fun peripheral(
                        peripheral: CBPeripheral,
                        didDiscoverCharacteristicsForService: CBService,
                        error: NSError?,
                    ) {
                        didDiscoverCharacteristicsForService.characteristics?.forEach { ch ->
                            val characteristic = ch as? CBCharacteristic ?: return@forEach
                            if (characteristic.UUID.UUIDString == CLICK_TOKEN_CHARACTERISTIC_UUID.uppercase()) {
                                peripheral.readValueForCharacteristic(characteristic)
                            }
                        }
                    }

                    override fun peripheral(
                        peripheral: CBPeripheral,
                        didUpdateValueForCharacteristic: CBCharacteristic,
                        error: NSError?,
                    ) {
                        if (didUpdateValueForCharacteristic.UUID.UUIDString == CLICK_TOKEN_CHARACTERISTIC_UUID.uppercase()) {
                            val value = didUpdateValueForCharacteristic.value as? NSData
                            parseGattTokenPayload(value?.toByteArray())?.let { heardTokens.add(it) }
                        }
                        centralManager?.cancelPeripheralConnection(peripheral)
                        connectingPeripheralIds.remove(peripheral.identifier.UUIDString)
                    }
                }
                discoveredPeripheralDelegate = peripheralDel
                val del = object : NSObject(), CBCentralManagerDelegateProtocol {
                    override fun centralManagerDidUpdateState(central: CBCentralManager) {
                        if (central.state == CBManagerStatePoweredOn && !started) {
                            started = true
                            val opts = mutableMapOf<Any?, Any?>(
                                CBCentralManagerScanOptionAllowDuplicatesKey to NSNumber(bool = true),
                            )
                            central.scanForPeripheralsWithServices(listOf(serviceUuid), options = opts)
                            cont.resume(Unit)
                        }
                    }

                    override fun centralManager(
                        central: CBCentralManager,
                        didDiscoverPeripheral: CBPeripheral,
                        advertisementData: Map<Any?, *>,
                        RSSI: NSNumber,
                    ) {
                        val id = didDiscoverPeripheral.identifier.UUIDString
                        if (connectingPeripheralIds.add(id)) {
                            didDiscoverPeripheral.delegate = peripheralDel
                            central.connectPeripheral(didDiscoverPeripheral, options = null)
                        }
                    }

                    override fun centralManager(
                        central: CBCentralManager,
                        didConnectPeripheral: CBPeripheral,
                    ) {
                        didConnectPeripheral.discoverServices(listOf(serviceUuid))
                    }

                    @kotlinx.cinterop.ObjCSignatureOverride
                    override fun centralManager(
                        central: CBCentralManager,
                        didFailToConnectPeripheral: CBPeripheral,
                        error: NSError?,
                    ) {
                        connectingPeripheralIds.remove(didFailToConnectPeripheral.identifier.UUIDString)
                    }

                    @kotlinx.cinterop.ObjCSignatureOverride
                    override fun centralManager(
                        central: CBCentralManager,
                        didDisconnectPeripheral: CBPeripheral,
                        error: NSError?,
                    ) {
                        connectingPeripheralIds.remove(didDisconnectPeripheral.identifier.UUIDString)
                    }
                }
                centralDelegate = del
                centralManager = CBCentralManager(delegate = del, queue = null)
                cont.invokeOnCancellation {
                    centralManager?.stopScan()
                }
            }
        }
        coroutineScope {
            val audio = async(Dispatchers.Default) { recordAudioSampleToSink() }
            delay(PROXIMITY_DEBOUNCE_WINDOW_MS)
            centralManager?.stopScan()
            audio.await()
        }
        centralManager = null
        return heardTokens.sorted()
    }

    private suspend fun recordAudioSampleToSink() {
        val path = NSTemporaryDirectory().trimEnd('/') + "/click_prox_listen_${kotlin.random.Random.nextLong()}.wav"
        val url = NSURL.fileURLWithPath(path)
        val settings = mutableMapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = K_AUDIO_FORMAT_LINEAR_PCM),
            AVSampleRateKey to 44_100,
            AVNumberOfChannelsKey to 1,
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsBigEndianKey to false,
            AVLinearPCMIsFloatKey to false,
            AVEncoderAudioQualityKey to 0x7F,
        )
        val recorder = AVAudioRecorder(uRL = url, settings = settings as Map<Any?, *>, error = null)
        audioRecorder = recorder
        if (!recorder.prepareToRecord()) return
        recorder.record()
        delay(PROXIMITY_DEBOUNCE_WINDOW_MS)
        recorder.stop()
        audioRecorder = null
        val bytes = readBytesFromPath(path) ?: return
        if (bytes.size < 1000) return
        val pcm = extractPcm16MonoFromWav(bytes) ?: return
        if (pcmRms(pcm) < 0.002) return
        val decoded = decodeAllHandshakeTokensFromPcmMono(pcm)
        if (decoded.isEmpty()) return
        withContext(Dispatchers.Main) {
            decoded.forEach { heardTokens.add(it) }
        }
    }

    override fun stopAll() {
        centralManager?.stopScan()
        centralManager = null
        centralDelegate = null
        discoveredPeripheralDelegate = null
        connectingPeripheralIds.clear()
        stopPeripheralOnly()
    }

    private fun stopPeripheralOnly() {
        peripheralManager?.stopAdvertising()
        peripheralManager = null
        audioPlayer?.stop()
        audioPlayer = null
        audioRecorder?.stop()
        audioRecorder = null
        tokenCharacteristic = null
        peripheralDelegate = null
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun wrapPcmAsWav(pcm: ShortArray): ByteArray {
    val sampleRate = 44_100
    val bitsPerSample = 16
    val channels = 1
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcm.size * 2
    val riffSize = 36 + dataSize
    val out = ByteArray(44 + dataSize)
    var o = 0
    fun writeStr(s: String) {
        for (c in s) out[o++] = c.code.toByte()
    }
    fun writeLe32(v: Int) {
        out[o++] = (v and 0xFF).toByte()
        out[o++] = ((v shr 8) and 0xFF).toByte()
        out[o++] = ((v shr 16) and 0xFF).toByte()
        out[o++] = ((v shr 24) and 0xFF).toByte()
    }
    fun writeLe16(v: Int) {
        out[o++] = (v and 0xFF).toByte()
        out[o++] = ((v shr 8) and 0xFF).toByte()
    }
    writeStr("RIFF")
    writeLe32(riffSize)
    writeStr("WAVE")
    writeStr("fmt ")
    writeLe32(16)
    writeLe16(1)
    writeLe16(channels)
    writeLe32(sampleRate)
    writeLe32(sampleRate * blockAlign)
    writeLe16(blockAlign)
    writeLe16(bitsPerSample)
    writeStr("data")
    writeLe32(dataSize)
    pcm.usePinned { pinned ->
        platform.posix.memcpy(out.refTo(o), pinned.addressOf(0), dataSize.convert())
    }
    return out
}

private fun extractPcm16MonoFromWav(file: ByteArray): ShortArray? {
    if (file.size < 44) return null
    if (file[0] != 'R'.code.toByte() || file[1] != 'I'.code.toByte()) return null
    val dataSize = (file[40].toInt() and 0xFF) or
        ((file[41].toInt() and 0xFF) shl 8) or
        ((file[42].toInt() and 0xFF) shl 16) or
        ((file[43].toInt() and 0xFF) shl 24)
    val start = 44
    if (start + dataSize > file.size || dataSize <= 0 || dataSize % 2 != 0) return null
    val samples = dataSize / 2
    val out = ShortArray(samples)
    var p = start
    for (i in 0 until samples) {
        val lo = file[p++].toInt() and 0xFF
        val hi = file[p++].toInt() and 0xFF
        out[i] = ((hi shl 8) or lo).toShort()
    }
    return out
}

@Composable
actual fun rememberProximityManager(): ProximityManager {
    return remember {
        if (isSimulatorOrEmulatorRuntime()) {
            MockProximityManager()
        } else {
            IosProximityManager()
        }
    }
}
