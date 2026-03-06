package compose.project.click.click

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import compose.project.click.click.data.storage.initTokenStorage
import compose.project.click.click.calls.initCallManager
import compose.project.click.click.notifications.initPushNotificationService
import compose.project.click.click.utils.initLocationService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize token storage with application context
        initTokenStorage(applicationContext)

        // Initialize location service with application context
        initLocationService(applicationContext)

        initCallManager(applicationContext, this)

        initPushNotificationService(applicationContext, this)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}