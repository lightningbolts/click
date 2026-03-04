package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.User
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import compose.project.click.click.utils.toImageBitmap
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import qrcode.QRCode

private val httpClient = HttpClient {
    install(ContentNegotiation) { json() }
}

private const val QR_API_URL = "$CLICK_WEB_BASE_URL/api/qr"
private const val TOKEN_TTL_SECONDS = 90

/**
 * Displays the user's QR code for scanning.
 *
 * Fetches a time-bounded, single-use token from the web API and encodes it in
 * the QR. Auto-refreshes the token 5 seconds before expiry with a live countdown.
 * Falls back to a static URL if the API is unreachable.
 */
@Composable
fun UserQrCode(
    user: User,
    size: Dp = 200.dp,
    onShare: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    // Static fallback URL — renders instantly so user never sees a blank
    val fallbackUrl = "$CLICK_WEB_BASE_URL/connect/${user.id}"

    var qrPayload by remember { mutableStateOf(fallbackUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(TOKEN_TTL_SECONDS) }
    var fetchError by remember { mutableStateOf(false) }
    var hasEverFetched by remember { mutableStateOf(false) }

    // Fetch a new token from the web API
    suspend fun fetchToken() {
        isLoading = true
        fetchError = false
        try {
            val session = SupabaseConfig.client.auth.currentSessionOrNull()
            val token = session?.accessToken

            val response = httpClient.get(QR_API_URL) {
                if (token != null) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            if (response.status.isSuccess()) {
                val body = response.body<JsonObject>()
                val data = body["data"]?.jsonObject
                val payload = data?.get("qrPayload")?.jsonPrimitive?.contentOrNull

                if (!payload.isNullOrBlank()) {
                    qrPayload = payload
                    secondsLeft = TOKEN_TTL_SECONDS
                } else {
                    // API returned success but no token — use fallback silently
                    qrPayload = fallbackUrl
                    secondsLeft = TOKEN_TTL_SECONDS
                }
            } else {
                // Only show error if we've ever had a successful fetch
                // On first load, silently fall back to static URL
                if (hasEverFetched) fetchError = true
            }
        } catch (e: Exception) {
            println("UserQrCode: Failed to fetch token: ${e.message}")
            if (hasEverFetched) fetchError = true
            // Keep showing the last valid QR rather than going blank
        } finally {
            isLoading = false
            hasEverFetched = true
        }
    }

    // Initial fetch + auto-refresh loop
    LaunchedEffect(user.id) {
        fetchToken()

        // Countdown and auto-refresh
        while (true) {
            delay(1_000L)
            if (secondsLeft > 0) {
                secondsLeft--
            }
            // Refresh 5 seconds before expiry
            if (secondsLeft <= 5) {
                fetchToken()
            }
        }
    }

    // Generate QR bitmap from payload
    val qrImageBitmap = remember(qrPayload) {
        try {
            val qrCode = QRCode.ofSquares().build(qrPayload)
            val content = qrCode.renderToBytes()
            content.toImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // Timer color: green → amber → red
    val timerColor = when {
        secondsLeft > 45 -> Color(0xFF22C55E)
        secondsLeft > 20 -> Color(0xFFF59E0B)
        else             -> Color(0xFFEF4444)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // QR Code box
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrImageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = qrImageBitmap,
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF8338EC),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Countdown row — only show after first successful fetch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (fetchError) {
                Text(
                    text = "Offline — tap to retry",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp
                )
                IconButton(
                    onClick = { coroutineScope.launch { fetchToken() } },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            } else if (hasEverFetched) {
                Text(
                    text = if (secondsLeft > 0) "${secondsLeft}s" else "Refreshing…",
                    color = timerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "· renews automatically",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                // Initial load — show nothing or subtle text
                Text(
                    text = "Generating secure code…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AdaptiveButton(onClick = onShare) {
            Text("Share QR Code")
        }
    }
}
