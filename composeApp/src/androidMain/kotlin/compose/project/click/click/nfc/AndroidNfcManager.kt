package compose.project.click.click.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset

class AndroidNfcManager(private val context: Context) : NfcManager {
    private val _nfcState = MutableStateFlow<NfcState>(NfcState.Idle)
    override val nfcState: StateFlow<NfcState> = _nfcState.asStateFlow()

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private var currentUserId: String? = null
    private var isReaderMode = false

    override fun isNfcAvailable(): Boolean {
        return nfcAdapter != null
    }

    override fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    override fun startNfcReader(userId: String) {
        if (!isNfcAvailable()) {
            _nfcState.value = NfcState.Error("NFC not available on this device")
            return
        }

        if (!isNfcEnabled()) {
            _nfcState.value = NfcState.Error("NFC is disabled. Please enable it in settings.")
            return
        }

        currentUserId = userId
        isReaderMode = true
        _nfcState.value = NfcState.Scanning

        // Enable reader mode for the activity
        val activity = context as? Activity
        activity?.let {
            nfcAdapter?.enableReaderMode(
                it,
                { tag -> handleNfcTag(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        }
    }

    override fun stopNfcReader() {
        val activity = context as? Activity
        activity?.let {
            nfcAdapter?.disableReaderMode(it)
        }
        isReaderMode = false
        _nfcState.value = NfcState.Idle
    }

    override fun startNfcWriter(userId: String) {
        if (!isNfcAvailable()) {
            _nfcState.value = NfcState.Error("NFC not available on this device")
            return
        }

        if (!isNfcEnabled()) {
            _nfcState.value = NfcState.Error("NFC is disabled. Please enable it in settings.")
            return
        }

        currentUserId = userId
        isReaderMode = false
        _nfcState.value = NfcState.Sending

        // For Android, we'll use the same reader mode and also handle writing
        // when a tag is detected
        val activity = context as? Activity
        activity?.let {
            nfcAdapter?.enableReaderMode(
                it,
                { tag -> handleNfcTag(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        }
    }

    override fun stopNfcWriter() {
        stopNfcReader()
    }

    override fun openNfcSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Intent(Settings.ACTION_NFC_SETTINGS)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun handleNfcTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)

            if (ndef != null) {
                // Read mode: try to read existing data
                ndef.connect()
                val ndefMessage = ndef.ndefMessage

                if (ndefMessage != null) {
                    val records = ndefMessage.records
                    for (record in records) {
                        if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                            val mimeType = String(record.type, Charset.forName("US-ASCII"))
                            if (mimeType == "application/vnd.click.user") {
                                val payload = String(record.payload, Charset.forName("UTF-8"))
                                parseUserData(payload)
                            }
                        }
                    }
                }

                // Write mode: write our data to the tag
                if (!isReaderMode && currentUserId != null) {
                    writeToTag(ndef)
                }

                ndef.close()
            } else {
                _nfcState.value = NfcState.Error("Tag is not NDEF formatted")
            }
        } catch (e: IOException) {
            _nfcState.value = NfcState.Error("Failed to read/write NFC tag: ${e.message}")
        } catch (e: Exception) {
            _nfcState.value = NfcState.Error("Error processing NFC: ${e.message}")
        }
    }

    private fun writeToTag(ndef: Ndef) {
        currentUserId?.let { userId ->
            try {
                val json = JSONObject().apply {
                    put("userId", userId)
                    put("timestamp", System.currentTimeMillis())
                }

                val payload = json.toString().toByteArray(Charset.forName("UTF-8"))
                val mimeType = "application/vnd.click.user"
                val mimeRecord = NdefRecord.createMime(mimeType, payload)
                val ndefMessage = NdefMessage(arrayOf(mimeRecord))

                if (ndef.isWritable && ndef.maxSize >= ndefMessage.toByteArray().size) {
                    ndef.writeNdefMessage(ndefMessage)
                    _nfcState.value = NfcState.Success(userId)
                } else {
                    _nfcState.value = NfcState.Error("Tag is not writable or too small")
                }
            } catch (e: Exception) {
                _nfcState.value = NfcState.Error("Failed to write to tag: ${e.message}")
            }
        }
    }

    private fun parseUserData(payload: String) {
        try {
            val json = JSONObject(payload)
            val userId = json.getString("userId")
            val userName = if (json.has("userName")) json.getString("userName") else null

            _nfcState.value = NfcState.DataReceived(userId, userName)
        } catch (e: Exception) {
            _nfcState.value = NfcState.Error("Failed to parse user data: ${e.message}")
        }
    }
}

actual fun createNfcManager(): NfcManager {
    throw IllegalStateException("createNfcManager() should not be called from common code. Use rememberNfcManager() instead.")
}

@Composable
actual fun rememberNfcManager(): NfcManager {
    val context = LocalContext.current
    return remember(context) {
        AndroidNfcManager(context)
    }
}

