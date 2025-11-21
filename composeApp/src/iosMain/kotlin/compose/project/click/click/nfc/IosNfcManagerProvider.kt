package compose.project.click.click.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberNfcManager(): NfcManager {
    return remember {
        IosNfcManager()
    }
}

