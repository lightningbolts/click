import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import theme.ClickTheme
import ui.screens.HomeScreen

@Composable
@Preview
fun App() {
    ClickTheme {
        HomeScreen(
            onNFCTapClick = {
                println("NFC Tap initiated - Ready to connect!")
            },
            onConnectionsClick = {
                println("Navigate to Connections")
            },
            onMapClick = {
                println("Navigate to Map View")
            },
            onVibeCheckClick = {
                println("Start Vibe Check")
            },
            onReconnectClick = {
                println("Reconnect feature")
            },
            onSettingsClick = {
                println("Navigate to Settings")
            }
        )
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Click - Turn encounters into connections"
    ) {
        App()
    }
}
