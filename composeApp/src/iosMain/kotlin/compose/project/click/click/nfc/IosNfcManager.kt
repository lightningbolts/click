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
import platform.darwin.dispatch_get_main_queue

/**
 * iOS NFC Manager implementation using Core NFC Tag Reader Session
 * Uses NFCTagReaderSession for iOS 18.2+ compatibility
 * Requires iPhone 7 or later running iOS 13+
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNfcManager : NfcManager {
    private val _nfcState = MutableStateFlow<NfcState>(NfcState.Idle)
    override val nfcState: StateFlow<NfcState> = _nfcState.asStateFlow()

    private var currentUserId: String? = null
    private var tagReaderSession: NFCTagReaderSession? = null
    private var tagReaderDelegate: NfcTagReaderDelegate? = null

    override fun isNfcAvailable(): Boolean {
        // Check if device supports NFC tag reading
        return NFCTagReaderSession.readingAvailable
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

        // Create delegate that handles NFC tag session callbacks
        tagReaderDelegate = NfcTagReaderDelegate(
            onTagRead = { payload ->
                // Try to parse as JSON first (QR code format)
                val parsedUserId = parsePayload(payload)
                if (parsedUserId != null) {
                    _nfcState.value = NfcState.DataReceived(parsedUserId, null)
                } else {
                    _nfcState.value = NfcState.Error("Invalid NFC data format. Expected Click user data.")
                }
            },
            onError = { errorMessage ->
                println("NFC Error: $errorMessage") // Debug logging
                when {
                    errorMessage.contains("Session invalidated") || 
                    errorMessage.contains("Session is invalidated") ||
                    errorMessage.contains("invalidated by user") ||
                    errorMessage.contains("User canceled") ||
                    errorMessage.contains("cancelled") -> {
                        // User dismissed the NFC sheet - return to idle
                        _nfcState.value = NfcState.Idle
                    }
                    errorMessage.contains("Feature not supported") -> {
                        _nfcState.value = NfcState.Error("NFC Tag Reading is not supported. Please use QR code to connect.")
                    }
                    errorMessage.contains("System resource unavailable") -> {
                        _nfcState.value = NfcState.Error("NFC is currently busy. Please try again.")
                    }
                    else -> {
                        // Show the full error for debugging
                        _nfcState.value = NfcState.Error("NFC Error: $errorMessage")
                    }
                }
            },
            onSessionStarted = {
                _nfcState.value = NfcState.Scanning
            }
        )

        try {
            // Create and start the NFC tag reader session
            // Use ISO14443 which covers most NFC tags including those formatted with NDEF
            // Note: Only use ISO14443 for maximum compatibility
            tagReaderSession = NFCTagReaderSession(
                pollingOption = NFCPollingISO14443,
                delegate = tagReaderDelegate!!,
                queue = null
            )
            
            tagReaderSession?.alertMessage = "Hold your iPhone near an NFC tag to connect"
            tagReaderSession?.beginSession()
        } catch (e: Exception) {
            println("NFC Session creation failed: ${e.message}")
            _nfcState.value = NfcState.Error("Failed to start NFC: ${e.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Parse payload from NFC tag
     * Supports both JSON format (from QR codes) and click:user: prefix format
     */
    private fun parsePayload(payload: String): String? {
        // Try JSON format first: {"userId":"xxx","name":"xxx"}
        if (payload.startsWith("{")) {
            try {
                // Simple JSON parsing for userId field
                val userIdMatch = Regex(""""userId"\s*:\s*"([^"]+)"""").find(payload)
                if (userIdMatch != null) {
                    return userIdMatch.groupValues[1]
                }
            } catch (e: Exception) {
                // Fall through to try other formats
            }
        }
        
        // Try click:user: prefix format
        val prefix = "click:user:"
        if (payload.startsWith(prefix)) {
            return payload.removePrefix(prefix)
        }
        
        // If it looks like a UUID, return it directly
        if (payload.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            return payload
        }
        
        return null
    }

    override fun stopNfcReader() {
        tagReaderSession?.invalidateSession()
        tagReaderSession = null
        tagReaderDelegate = null
        _nfcState.value = NfcState.Idle
    }

    override fun startNfcWriter(userId: String) {
        currentUserId = userId

        if (!isNfcAvailable()) {
            _nfcState.value = NfcState.Error("NFC is not available on this device")
            return
        }

        // iOS cannot do peer-to-peer NFC like Android
        // For phone-to-phone connection, QR code is the recommended method
        _nfcState.value = NfcState.Error("For phone-to-phone connection, please use QR code scanning. NFC is for reading NFC tags only.")
    }

    override fun stopNfcWriter() {
        stopNfcReader()
    }

    override fun openNfcSettings() {
        // On iOS, there's no dedicated NFC settings page
        // NFC is always on if the hardware supports it
        // Open the general Settings app
        val settingsUrl = NSURL.URLWithString("App-Prefs:root=General")
            ?: NSURL.URLWithString("app-settings:")
            ?: return
        
        if (UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
            UIApplication.sharedApplication.openURL(settingsUrl)
        }
    }
}

/**
 * Delegate class for handling NFC tag reader session callbacks
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class NfcTagReaderDelegate(
    private val onTagRead: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onSessionStarted: () -> Unit
) : NSObject(), NFCTagReaderSessionDelegateProtocol {
    
    override fun tagReaderSessionDidBecomeActive(session: NFCTagReaderSession) {
        onSessionStarted()
    }
    
    override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
        val errorMessage = didInvalidateWithError.localizedDescription
        val errorCode = didInvalidateWithError.code
        val domain = didInvalidateWithError.domain
        println("NFC Session invalidated - Domain: $domain, Code: $errorCode, Message: $errorMessage")
        onError(errorMessage)
    }
    
    override fun tagReaderSession(session: NFCTagReaderSession, didDetectTags: List<*>) {
        // Process detected tags
        val tag = didDetectTags.firstOrNull() ?: run {
            session.alertMessage = "No NFC tag detected. Try again."
            return
        }
        
        // Connect to the tag
        @Suppress("UNCHECKED_CAST")
        session.connectToTag(tag as NFCTagProtocol) { error ->
            if (error != null) {
                platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                    onError("Failed to connect to NFC tag: ${error.localizedDescription}")
                }
                return@connectToTag
            }
            
            // Try to read NDEF data from the tag
            val mifareTag = (tag as? NFCTagProtocol)?.asNFCMiFareTag()
            if (mifareTag != null) {
                // Query NDEF status for NDEF formatted tags
                mifareTag.queryNDEFStatusWithCompletionHandler { status, capacity, queryError ->
                    if (queryError != null || status == NFCNDEFStatusNotSupported) {
                        platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                            onError("This NFC tag doesn't contain readable data")
                        }
                        session.invalidateSession()
                        return@queryNDEFStatusWithCompletionHandler
                    }
                    
                    // Read NDEF message
                    mifareTag.readNDEFWithCompletionHandler { message, readError ->
                        if (readError != null || message == null) {
                            platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                                onError("Failed to read NFC tag data")
                            }
                            session.invalidateSession()
                            return@readNDEFWithCompletionHandler
                        }
                        
                        // Process NDEF message
                        processNdefMessage(message, session)
                    }
                }
            } else {
                // For other tag types, try to get identifier or other data
                platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                    onError("This NFC tag type is not supported")
                }
                session.invalidateSession()
            }
        }
    }
    
    private fun processNdefMessage(message: NFCNDEFMessage, session: NFCTagReaderSession) {
        for (record in message.records) {
            val ndefRecord = record as? NFCNDEFPayload ?: continue
            
            // Try to get the payload as text
            val payload = ndefRecord.payload ?: continue
            
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
                
                platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                    onTagRead(cleanPayload)
                }
                session.alertMessage = "Tag read successfully!"
                session.invalidateSession()
                return
            }
        }
        
        // No valid payload found
        platform.darwin.dispatch_async(dispatch_get_main_queue()) {
            onError("No valid Click data found on this NFC tag")
        }
        session.invalidateSession()
    }
}

actual fun createNfcManager(): NfcManager {
    return IosNfcManager()
}
