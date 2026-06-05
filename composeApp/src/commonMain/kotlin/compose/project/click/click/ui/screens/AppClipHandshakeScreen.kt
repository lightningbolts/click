package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.models.UserProfile
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter
import compose.project.click.click.qr.CLICK_IOS_APP_STORE_URL
import compose.project.click.click.ui.components.ConnectionContextPresentation
import compose.project.click.click.ui.components.ConnectionContextSheet

/**
 * Stripped-down handshake surface for the iOS App Clip target.
 * Loads public profile data and presents the QR handshake context sheet without full app chrome.
 */
@Composable
fun AppClipHandshakeScreen(invocationUrl: String?) {
    var targetUserId by remember(invocationUrl) {
        mutableStateOf(invocationUrl?.let { ConnectionDeepLinkRouter.parseConnectionUserId(it) })
    }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var handshakeComplete by remember { mutableStateOf(false) }

    LaunchedEffect(targetUserId) {
        val userId = targetUserId
        if (userId.isNullOrBlank()) {
            loading = false
            error = "Invalid connection link."
            return@LaunchedEffect
        }
        loading = true
        error = null
        val api = ApiClient()
        val result = api.getPublicProfileUnauthenticated(userId)
        result.fold(
            onSuccess = { body ->
                profile = UserProfile(
                    id = userId,
                    displayName = body.displayName,
                    avatarUrl = body.avatarUrl,
                )
                loading = false
            },
            onFailure = {
                profile = UserProfile(id = userId, displayName = "Click member")
                loading = false
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> {
                AdaptiveCircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFF8338EC),
                )
            }
            error != null -> {
                Text(
                    text = error.orEmpty(),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            handshakeComplete -> {
                AppClipDownloadCta()
            }
            profile != null -> {
                val user = profile!!
                ConnectionContextSheet(
                    connectedUsers = listOf(user),
                    locationName = null,
                    initialNoiseOptIn = false,
                    noisePermissionGranted = false,
                    onDismiss = { handshakeComplete = true },
                    onSkip = { handshakeComplete = true },
                    presentation = ConnectionContextPresentation.QrFlow,
                    onConfirm = { _, _ -> handshakeComplete = true },
                )
            }
        }
    }
}

@Composable
private fun AppClipDownloadCta() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF8338EC), Color(0xFF3A86FF)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = "Connection started!",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Download the full Click app to save this connection and keep chatting.",
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { uriHandler.openUri(CLICK_IOS_APP_STORE_URL) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Text(
                text = "Download Full App to Save Connection",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
