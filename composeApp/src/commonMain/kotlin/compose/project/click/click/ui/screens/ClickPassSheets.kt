@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.events.ClickPass
import compose.project.click.click.events.ClickPassUnavailable
import compose.project.click.click.events.ClickPassUnavailableException
import compose.project.click.click.events.PassScan
import compose.project.click.click.events.getClickPass
import compose.project.click.click.events.scanClickPass
import compose.project.click.click.events.verdict
import compose.project.click.click.platform.setCodeDisplayBrightness
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.QRScanner // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import kotlinx.coroutines.launch
import qrcode.QRCode

private sealed interface PassLoad {
    data object Loading : PassLoad

    data class Ready(
        val pass: ClickPass,
    ) : PassLoad

    data class Unavailable(
        val message: String,
    ) : PassLoad
}

/**
 * The attendee's Click Pass (iOS `ClickPassView`): a QR the host scans at the door, the short code,
 * and "You're in" once scanned. The screen goes to full brightness while it's open.
 */
@Composable
internal fun ClickPassSheet(
    beaconId: String,
    eventTitle: String,
    onDismiss: () -> Unit,
    api: ApiClient = remember { ApiClient() },
) {
    var load by remember(beaconId) { mutableStateOf<PassLoad>(PassLoad.Loading) }
    var reload by remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        setCodeDisplayBrightness(true)
        onDispose { setCodeDisplayBrightness(false) }
    }
    LaunchedEffect(beaconId, reload) {
        load =
            api.getClickPass(beaconId).fold(
                onSuccess = { PassLoad.Ready(it) },
                onFailure = { err ->
                    PassLoad.Unavailable(
                        when ((err as? ClickPassUnavailableException)?.reason) {
                            ClickPassUnavailable.NOT_GOING -> "RSVP to the event and your Click Pass appears here."
                            ClickPassUnavailable.UNAVAILABLE -> "Click Pass isn't available right now."
                            null -> "Couldn't load your Click Pass."
                        },
                    )
                },
            )
    }
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Click Pass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(eventTitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            when (val current = load) {
                PassLoad.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                is PassLoad.Unavailable -> {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload++ }) { Text("Try Again") }
                }
                is PassLoad.Ready -> {
                    val pass = current.pass
                    val qr =
                        remember(pass.credentialUrl) {
                            runCatching {
                                QRCode
                                    .ofSquares()
                                    .build(pass.credentialUrl)
                                    .renderToBytes()
                                    .toImageBitmap()
                            }.getOrNull()
                        }
                    // Black on white in every theme, with a quiet zone, so door scanners can read it.
                    Box(
                        modifier =
                            Modifier
                                .widthIn(max = 260.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                                .semantics { contentDescription = "Click Pass QR code, ${pass.code}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qr != null) Image(bitmap = qr, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                    if (pass.code.isNotBlank()) {
                        Text(pass.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        pass.checkedInAtMs?.let { "You're in · checked in ${formatClockTime(it)}" }
                            ?: "Your host scans this at the door. It's yours alone: if it's shared, the host sees your name and photo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (pass.checkedInAtMs !=
                                null
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The host's door scanner (iOS `PassScannerView`): each scan shows the holder's name and a verdict.
 * The same code isn't sent twice in a row within a few seconds.
 */
@Composable
internal fun PassScannerSheet(
    beaconId: String,
    onDismiss: () -> Unit,
    api: ApiClient = remember { ApiClient() },
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastRead by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var outcome by remember { mutableStateOf<PassScan?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Scan Click Passes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                QRScanner(modifier = Modifier.fillMaxWidth().aspectRatio(1f), isActive = !busy) { text ->
                    val now =
                        kotlinx.datetime.Clock.System
                            .now()
                            .toEpochMilliseconds()
                    val previous = lastRead
                    if (previous != null && previous.first == text && now - previous.second < 4_000L) return@QRScanner
                    lastRead = text to now
                    busy = true
                    scope.launch {
                        api.scanClickPass(beaconId, text).fold(
                            onSuccess = {
                                outcome = it
                                failure = null
                            },
                            onFailure = {
                                outcome = null
                                failure = it.message ?: "Check your connection and try again."
                            },
                        )
                        busy = false
                    }
                }
            }
            val scan = outcome
            Column(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    failure != null -> {
                        Text("Couldn't check this pass", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            failure.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    scan != null -> {
                        val verdict = scan.verdict(::formatClockTime)
                        Icon(
                            if (verdict.good) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (verdict.good) Color(0xFF1FA855) else MaterialTheme.colorScheme.error,
                        )
                        scan.holderName?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        Text(verdict.title, style = MaterialTheme.typography.titleMedium)
                        verdict.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else ->
                        Text(
                            "Point the camera at a guest's Click Pass.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}
