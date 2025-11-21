package compose.project.click.click.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Composable function to provide platform-specific NFC manager
 */
@Composable
expect fun rememberNfcManager(): NfcManager

