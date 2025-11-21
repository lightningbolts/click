package compose.project.click.click.nfc

import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for NFC functionality across platforms
 */
interface NfcManager {
    /**
     * State flow that emits NFC status and received data
     */
    val nfcState: StateFlow<NfcState>

    /**
     * Check if NFC is available on the device
     */
    fun isNfcAvailable(): Boolean

    /**
     * Check if NFC is enabled on the device
     */
    fun isNfcEnabled(): Boolean

    /**
     * Start listening for NFC tags (reader mode)
     * @param userId The current user's ID to share
     */
    fun startNfcReader(userId: String)

    /**
     * Stop listening for NFC tags
     */
    fun stopNfcReader()

    /**
     * Start NFC host card emulation mode (writer mode)
     * @param userId The current user's ID to share
     */
    fun startNfcWriter(userId: String)

    /**
     * Stop NFC writer mode
     */
    fun stopNfcWriter()

    /**
     * Open system NFC settings
     */
    fun openNfcSettings()
}

/**
 * NFC state representing the current status
 */
sealed class NfcState {
    object Idle : NfcState()
    object Scanning : NfcState()
    object Sending : NfcState()
    data class DataReceived(val userId: String, val userName: String?) : NfcState()
    data class Success(val otherUserId: String) : NfcState()
    data class Error(val message: String) : NfcState()
}

/**
 * Data class for NFC exchange payload
 */
data class NfcUserData(
    val userId: String,
    val userName: String?,
    val email: String?,
    val timestamp: Long
)

/**
 * Factory function to create platform-specific NFC manager
 */
expect fun createNfcManager(): NfcManager

