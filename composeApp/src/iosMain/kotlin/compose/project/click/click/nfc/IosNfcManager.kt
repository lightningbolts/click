package compose.project.click.click.nfc

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreNFC.*
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.darwin.NSObject

/**
 * iOS NFC Manager implementation using Core NFC
 * Requires iPhone 7 or later running iOS 11+
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNfcManager : NfcManager {
    private val _nfcState = MutableStateFlow<NfcState>(NfcState.Idle)
    override val nfcState: StateFlow<NfcState> = _nfcState.asStateFlow()

    private var currentUserId: String? = null
    private var readerSession: NFCNDEFReaderSession? = null
    private var readerDelegate: NfcReaderDelegate? = null

    override fun isNfcAvailable(): Boolean {
        // Check if device supports NFC reading
        return NFCNDEFReaderSession.readingAvailable
    }

    override fun isNfcEnabled(): Boolean {
        // On iOS, if NFC is available, it's enabled (no separate toggle like Android)
        return isNfcAvailable()
    }

    override fun startNfcReader(userId: String) {
        currentUserId = userId

        if (!isNfcAvailable()) {
            _nfcState.value = NfcState.Error("NFC is not available on this device. This feature requires iPhone 7 or later.")
            return
        }

        _nfcState.value = NfcState.Scanning

        // Create delegate that handles NFC session callbacks
        readerDelegate = NfcReaderDelegate(
            onTagRead = { payload ->
                // Parse the payload - expecting format "click:user:<userId>"
                val prefix = "click:user:"
                if (payload.startsWith(prefix)) {
                    val scannedUserId = payload.removePrefix(prefix)
                    _nfcState.value = NfcState.DataReceived(scannedUserId, null)
                } else {
                    _nfcState.value = NfcState.Error("Invalid QR/NFC code format")
                }
            },
            onError = { errorMessage ->
                if (errorMessage.contains("Session invalidated") || 
                    errorMessage.contains("Session is invalidated") ||
                    errorMessage.contains("User canceled")) {
                    // User dismissed the NFC sheet - return to idle
                    _nfcState.value = NfcState.Idle
                } else {
                    _nfcState.value = NfcState.Error(errorMessage)
                }
            },
            onSessionStarted = {
                _nfcState.value = NfcState.Scanning
            }
        )

        // Create and start the NFC reader session
        readerSession = NFCNDEFReaderSession(
            delegate = readerDelegate!!,
            queue = null,
            invalidateAfterFirstRead = false
        )
        
        readerSession?.alertMessage = "Hold your iPhone near another device to connect"
        readerSession?.beginSession()
    }

    override fun stopNfcReader() {
        readerSession?.invalidateSession()
        readerSession = null
        readerDelegate = null
        _nfcState.value = NfcState.Idle
    }

    override fun startNfcWriter(userId: String) {
        currentUserId = userId

        if (!isNfcAvailable()) {
            _nfcState.value = NfcState.Error("NFC is not available on this device")
            return
        }

        // On iOS, NFC tag writing requires specific hardware (iPhone 7+, iOS 13+)
        // For now, we use the reader mode which can also detect and read tags
        // True peer-to-peer NFC (like Android Beam) is not supported on iOS
        // Instead, we'll display the user's info as a QR code for the other device to scan
        
        _nfcState.value = NfcState.Sending
        
        // Note: For iOS-to-iOS NFC connection, we need to use a different approach:
        // One device shows QR code, the other scans it
        // Or use Core NFC to write to an NFC tag that the other device reads
        
        _nfcState.value = NfcState.Error("iOS NFC writing requires writing to an NFC tag. Use QR code for direct phone-to-phone connection.")
    }

    override fun stopNfcWriter() {
        stopNfcReader()
    }

    override fun openNfcSettings() {
        // On iOS, there's no dedicated NFC settings page
        // NFC is always on if the hardware supports it
        // We can open the general Settings app
        val settingsUrl = NSURL.URLWithString("App-Prefs:root=General")
            ?: NSURL.URLWithString("app-settings:")
            ?: return
        
        if (UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
            UIApplication.sharedApplication.openURL(settingsUrl)
        }
    }
}

/**
 * Delegate class for handling NFC reader session callbacks
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class NfcReaderDelegate(
    private val onTagRead: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onSessionStarted: () -> Unit
) : NSObject(), NFCNDEFReaderSessionDelegateProtocol {
    
    override fun readerSessionDidBecomeActive(session: NFCNDEFReaderSession) {
        onSessionStarted()
    }
    
    override fun readerSession(session: NFCNDEFReaderSession, didInvalidateWithError: NSError) {
        val errorMessage = didInvalidateWithError.localizedDescription
        onError(errorMessage)
    }
    
    override fun readerSession(session: NFCNDEFReaderSession, didDetectNDEFs: List<*>) {
        // Process NDEF messages
        for (message in didDetectNDEFs) {
            val ndefMessage = message as? NFCNDEFMessage ?: continue
            
            for (record in ndefMessage.records) {
                val ndefRecord = record as? NFCNDEFPayload ?: continue
                
                // Try to get the payload as text
                val payload = ndefRecord.payload ?: continue
                
                // For text records, the first byte is the language code length
                // Skip the language code prefix to get the actual text
                @Suppress("UNCHECKED_CAST")
                val payloadString = NSString.create(
                    data = payload,
                    encoding = NSUTF8StringEncoding
                ) as? String
                
                if (payloadString != null) {
                    // Remove any language code prefix (for well-known text records)
                    val cleanPayload = if (payloadString.length > 3 && 
                        payloadString[0].code < 32) {
                        // Text record with language code - skip prefix
                        val langCodeLen = payloadString[0].code
                        payloadString.substring(langCodeLen + 1)
                    } else {
                        payloadString
                    }
                    
                    onTagRead(cleanPayload)
                    session.invalidateSession()
                    return
                }
            }
        }
        
        // No valid payload found
        session.alertMessage = "No valid Click data found. Try again."
    }
}

actual fun createNfcManager(): NfcManager {
    return IosNfcManager()
}
