package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import compose.project.click.click.qr.buildConnectionUniversalLink
import compose.project.click.click.qr.buildOfflineQrPayload
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import compose.project.click.click.telemetry.TelemetryBatcher
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.utils.LocationService
import compose.project.click.click.utils.toImageBitmap
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import qrcode.QRCode
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator

private val httpClient = HttpClient {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
}

private const val QR_API_URL = "$CLICK_WEB_BASE_URL/api/qr"
private const val QR_TOKEN_TTL_MS = 90_000L

internal fun formatRefreshCountdown(secondsRemaining: Int): String {
    val clamped = secondsRemaining.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

private fun parseJsonEpochMs(element: JsonElement?): Long? {
    val raw = element?.jsonPrimitive ?: return null
    return raw.longOrNull
        ?: raw.contentOrNull?.trim()?.toLongOrNull()
        ?: raw.contentOrNull?.trim()?.toDoubleOrNull()?.toLong()
}

/**
 * Displays the user's QR code for scanning.
 *
 * Encodes a Universal Link (`https://click-us.vercel.app/c/{userId}`) so native OS cameras
 * route through the web/App Clip layer instead of misinterpreting raw JSON as phone numbers.
 * Optionally registers initiator GPS with the server in the background for proximity verification.
 */
@Composable
fun UserQrCode(
    user: User,
    locationService: LocationService? = null,
    size: Dp = 200.dp,
    onShare: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    val universalLink = remember(user.id) { buildConnectionUniversalLink(user.id) }
    val fallbackUrl = remember(user.id, user.name) { buildOfflineQrPayload(user.id, user.name) }

    var qrPayload by remember(user.id) {
        mutableStateOf(universalLink.ifBlank { fallbackUrl })
    }
    var gpsRegistered by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf(false) }
    var tokenExpiresAtMs by remember { mutableLongStateOf(0L) }
    var secondsUntilRefresh by remember { mutableIntStateOf(90) }

    suspend fun registerGpsWithServer(): Long {
        fetchError = false
        val fallbackExpiry = Clock.System.now().toEpochMilliseconds() + QR_TOKEN_TTL_MS
        try {
            val session = SupabaseConfig.client.auth.currentSessionOrNull()
            val token = session?.accessToken

            val location = try {
                locationService?.getHighAccuracyLocation(4000L)
            } catch (_: Exception) {
                null
            }

            var url = QR_API_URL
            if (location != null && location.latitude.isFinite() && location.longitude.isFinite()
                && !(location.latitude == 0.0 && location.longitude == 0.0)) {
                url += "?lat=${location.latitude}&lon=${location.longitude}"
            }

            val response = httpClient.get(url) {
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
                } else {
                    TelemetryBatcher.recordQrFallback()
                    qrPayload = universalLink.ifBlank { fallbackUrl }
                }
                val expiry = parseJsonEpochMs(data?.get("expiresAt")) ?: fallbackExpiry
                tokenExpiresAtMs = expiry
                gpsRegistered = true
                return expiry
            } else {
                if (!gpsRegistered) {
                    TelemetryBatcher.recordQrFallback()
                }
                if (gpsRegistered) fetchError = true
            }
        } catch (e: Exception) {
            println("UserQrCode: Failed to register GPS: ${e.redactedRestMessage()}")
            TelemetryBatcher.recordQrFallback()
            if (gpsRegistered) fetchError = true
        }
        if (tokenExpiresAtMs <= 0L) {
            tokenExpiresAtMs = fallbackExpiry
        }
        return tokenExpiresAtMs
    }

    LaunchedEffect(user.id) {
        qrPayload = universalLink.ifBlank { fallbackUrl }
        launch(Dispatchers.Default) {
            runCatching { locationService?.getHighAccuracyLocation(4000L) }
        }

        tokenExpiresAtMs = Clock.System.now().toEpochMilliseconds() + QR_TOKEN_TTL_MS
        secondsUntilRefresh = (QR_TOKEN_TTL_MS / 1_000L).toInt()

        registerGpsWithServer()

        while (isActive) {
            val now = Clock.System.now().toEpochMilliseconds()
            val remainingSec = ((tokenExpiresAtMs - now) / 1_000L).toInt()
            secondsUntilRefresh = remainingSec.coerceAtLeast(0)
            if (remainingSec <= 0) {
                registerGpsWithServer()
            }
            delay(1_000L)
        }
    }

    val qrImageBitmap = remember(qrPayload) {
        try {
            val qrCode = QRCode.ofSquares().build(qrPayload)
            val content = qrCode.renderToBytes()
            content.toImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                AdaptiveCircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF8338EC),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    onClick = { coroutineScope.launch { registerGpsWithServer() } },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            } else {
                Text(
                    text = "Scan to connect · ${formatRefreshCountdown(secondsUntilRefresh)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AdaptiveButton(onClick = onShare) {
            Text("Share QR Code")
        }
    }
}
