@file:OptIn(ExperimentalForeignApi::class)

package compose.project.click.click.proximity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
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
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBUUID
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

private fun logNsError(where: String, error: NSError?) {
    if (error == null) return
    println("IosProximityManager: $where — code=${error.code} domain=${error.domain} desc=${error.localizedDescription}")
}

@OptIn(ExperimentalForeignApi::class)
private fun configureProximityAudioSession() {
    val session: AVAudioSession = AVAudioSession.sharedInstance()
    try {
        val options = AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionDefaultToSpeaker
        memScoped {
            val catErr = alloc<ObjCObjectVar<NSError?>>()
            catErr.value = null
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, options, catErr.ptr)
            catErr.value?.let { logNsError("setCategory", it) }
            val actErr = alloc<ObjCObjectVar<NSError?>>()
            actErr.value = null
            session.setActive(true, actErr.ptr)
            actErr.value?.let { logNsError("setActive", it) }
        }
    } catch (e: Throwable) {
        println("IosProximityManager: configureProximityAudioSession — ${e.message}")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    platform.posix.memcpy(out.refTo(0), base, len.convert())
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun buildIosManufacturerBlock(token: String): NSData {
    val payload = buildBleManufacturerPayload(token)
    val id = CLICK_BLE_MANUFACTURER_ID
    val full = ByteArray(2 + payload.size)
    full[0] = (id and 0xFF).toByte()
    full[1] = ((id shr 8) and 0xFF).toByte()
    payload.copyInto(full, destinationOffset = 2)
    return full.usePinned { pinned ->
        val m = NSMutableData()
        m.setLength(full.size.convert())
        val dest = m.mutableBytes ?: error("mutableBytes")
        memcpy(dest, pinned.addressOf(0), full.size.convert())
        m
    }
}

private fun parseManufacturerFromIosAdvertisement(data: NSData): String? {
    val bytes = data.toByteArray()
    if (bytes.size < 2 + 6) return null
    val id = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    if (id != CLICK_BLE_MANUFACTURER_ID) return null
    val rest = bytes.copyOfRange(2, bytes.size)
    return parseBleManufacturerPayload(rest)
}

@OptIn(ExperimentalForeignApi::class)
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

@OptIn(ExperimentalForeignApi::class)
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

@OptIn(ExperimentalForeignApi::class)
private fun byteArrayToNsData(bytes: ByteArray): NSData {
    if (bytes.isEmpty()) {
        return NSMutableData()
    }
    return bytes.usePinned { pinned ->
        val m = NSMutableData()
        m.setLength(bytes.size.convert())
        val dest = m.mutableBytes ?: error("mutableBytes")
        memcpy(dest, pinned.addressOf(0), bytes.size.convert())
        m
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosProximityManager : ProximityManager {

    private var peripheralManager: CBPeripheralManager? = null
    private var centralManager: CBCentralManager? = null
    private var audioPlayer: AVAudioPlayer? = null
    private var audioRecorder: AVAudioRecorder? = null

    /** Heard tokens (main-thread only mutations from BLE + Main post from audio). */
    private val heardTokens = mutableSetOf<String>()

    override fun supportsTapExchange(): Boolean = true

    override fun capabilityNote(): String =
        "Uses Bluetooth Low Energy and short-range audio tones (including 18.5 kHz) to find nearby taps."

    override fun openRadiosSettings() {
        compose.project.click.click.ui.utils.openApplicationSystemSettings()
    }

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        stopAll()
        val payload = runCatching { buildIosManufacturerBlock(ephemeralToken) }.getOrElse { return }
        val tokenBytes = buildBleManufacturerPayload(ephemeralToken)
        val tokenUuidStr = encodeBleTokenIntoAdvertisedUuidString(ephemeralToken)
        val primaryUuid = CBUUID.UUIDWithString(CLICK_PRIMARY_SERVICE_UUID_STRING)
        val tokenUuid = CBUUID.UUIDWithString(tokenUuidStr)
        val charUuid = CBUUID.UUIDWithString(CLICK_TOKEN_CHARACTERISTIC_UUID_STRING)
        val charData = byteArrayToNsData(tokenBytes)
        val characteristic = CBMutableCharacteristic(
            type = charUuid,
            properties = CBCharacteristicPropertyRead,
            value = charData,
            permissions = platform.CoreBluetooth.CBAttributePermissionsReadable,
        )
        val service = CBMutableService(type = primaryUuid, primary = true)
        val chars = mutableListOf<Any?>()
        chars.add(characteristic)
        service.setCharacteristics(chars)

        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                fun finish() {
                    if (!resumed) {
                        resumed = true
                        cont.resume(Unit)
                    }
                }
                var advertisingStarted = false
                val del = object : NSObject(), CBPeripheralManagerDelegateProtocol {
                    override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
                        if (peripheral.state == CBManagerStatePoweredOn) {
                            peripheral.removeAllServices()
                            peripheral.addService(service)
                        } else {
                            finish()
                        }
                    }

                    override fun peripheralManager(
                        peripheral: CBPeripheralManager,
                        didAddService: platform.CoreBluetooth.CBService,
                        error: NSError?,
                    ) {
                        if (error != null) {
                            logNsError("didAddService", error)
                            finish()
                            return
                        }
                        if (advertisingStarted) return
                        advertisingStarted = true
                        val adv = NSMutableDictionary()
                        val mfgKey = NSString.create(string = "kCBAdvDataManufacturerData")
                        adv.setObject(payload, forKey = mfgKey)
                        val uuidKey = NSString.create(string = "kCBAdvDataServiceUUIDsKey")
                        adv.setObject(listOf(tokenUuid, primaryUuid), forKey = uuidKey)
                        peripheral.startAdvertising(adv as Map<Any?, *>)
                    }

                    override fun peripheralManagerDidStartAdvertising(
                        peripheral: CBPeripheralManager,
                        error: NSError?,
                    ) {
                        if (error != null) logNsError("didStartAdvertising", error)
                        finish()
                    }
                }
                peripheralManager = CBPeripheralManager(delegate = del, queue = null)
                cont.invokeOnCancellation {
                    peripheralManager?.stopAdvertising()
                    peripheralManager?.removeAllServices()
                }
            }
        }

        configureProximityAudioSession()
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
        heardTokens.clear()
        configureProximityAudioSession()
        val primaryFilterUuid = CBUUID.UUIDWithString(CLICK_PRIMARY_SERVICE_UUID_STRING)
        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                var started = false
                val del = object : NSObject(), CBCentralManagerDelegateProtocol {
                    override fun centralManagerDidUpdateState(central: CBCentralManager) {
                        if (central.state == CBManagerStatePoweredOn && !started) {
                            started = true
                            val opts = mutableMapOf<Any?, Any?>(
                                CBCentralManagerScanOptionAllowDuplicatesKey to NSNumber(bool = true),
                            )
                            central.scanForPeripheralsWithServices(
                                serviceUUIDs = listOf(primaryFilterUuid),
                                options = opts,
                            )
                            cont.resume(Unit)
                        }
                    }

                    override fun centralManager(
                        central: CBCentralManager,
                        didDiscoverPeripheral: CBPeripheral,
                        advertisementData: Map<Any?, *>,
                        RSSI: NSNumber,
                    ) {
                        val mfgRaw = advertisementData["kCBAdvDataManufacturerData"] as? NSData
                        if (mfgRaw != null) {
                            parseManufacturerFromIosAdvertisement(mfgRaw)?.let { heardTokens.add(it) }
                        }
                        val uuidList = advertisementData["kCBAdvDataServiceUUIDsKey"] as? List<*>
                        uuidList?.forEach { u ->
                            val uuidStr = (u as? CBUUID)?.UUIDString ?: return@forEach
                            decodeBleTokenFromAdvertisedUuidString(uuidStr)?.let { heardTokens.add(it) }
                        }
                    }
                }
                centralManager = CBCentralManager(delegate = del, queue = null)
                cont.invokeOnCancellation {
                    centralManager?.stopScan()
                }
            }
        }
        coroutineScope {
            val audio = async(Dispatchers.Default) { recordAudioSampleToSink() }
            delay(3000L)
            centralManager?.stopScan()
            audio.await()
        }
        centralManager = null
        return heardTokens.sorted()
    }

    private suspend fun recordAudioSampleToSink() {
        configureProximityAudioSession()
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
        delay(3000L)
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
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()
        peripheralManager = null
        centralManager?.stopScan()
        centralManager = null
        audioPlayer?.stop()
        audioPlayer = null
        audioRecorder?.stop()
        audioRecorder = null
    }
}

@OptIn(ExperimentalForeignApi::class)
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
    // Directive C11: do not inject [MockProximityManager] in the simulator build —
    // simulators must show real (or empty) database state so the production UI graph
    // isn't poisoned by mock pins. The mock impl still exists for unit tests only.
    return remember { IosProximityManager() }
}
