package compose.project.click.click.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import compose.project.click.click.ui.theme.LocalPlatformStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.ui.theme.BorderHardDark
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Full-screen explainer shown before the OS location permission dialog.
 * Lead with Memory Map benefit; clarify single snapshot + anonymized insights; CTAs Build my map / Not now.
 * Dark theme, violet accent #8338EC (PrimaryBlue).
 */
@Composable
fun LocationOnboardingScreen(
    mapPreviewContent: @Composable () -> Unit,
    onBuildMyMap: () -> Unit,
    onNotNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Headline
            Text(
                "Remember where you met",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your personal Memory Map shows every connection as a pin—only you see it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Map preview / teaser
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(2.dp, BorderHardDark, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    mapPreviewContent()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Single snapshot copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "We capture a single GPS snapshot at the moment you tap—no continuous tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Business insights copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Anonymous venue and campus trends are included by default, and you can opt out anytime in Your Data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTAs
            val locStyle = LocalPlatformStyle.current
            Button(
                onClick = onBuildMyMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(if (locStyle.isIOS) 14.dp else 28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation = if (locStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation()
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Build my map", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Not now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Minimal map teaser: real connection pins if available, else placeholder dots.
 */
@Composable
fun LocationOnboardingMapPreview() {
    val connections by AppDataManager.connections.collectAsState()
    val validPins = connections
        .filter { conn ->
            conn.isInActiveConnectionsChannel() &&
                conn.connectionMapGeo()?.let { g ->
                    g.lat.isFinite() && g.lon.isFinite() && !(g.lat == 0.0 && g.lon == 0.0)
                } == true
        }
        .take(12)
        .mapNotNull { c -> c.connectionMapGeo() }
    val density = LocalDensity.current
    val r1 = with(density) { 12.dp.toPx() }
    val r2 = with(density) { 10.dp.toPx() }
    val r3 = with(density) { 8.dp.toPx() }
    val rPin = with(density) { 14.dp.toPx() }
    val pinAccentColor = MaterialTheme.colorScheme.primaryContainer

    Box(modifier = Modifier.fillMaxSize()) {
        if (validPins.isEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(color = PrimaryBlue, radius = r1, center = center)
                drawCircle(color = PrimaryBlue, radius = r2, center = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.4f))
                drawCircle(color = PrimaryBlue, radius = r2, center = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.55f))
                drawCircle(color = pinAccentColor, radius = r3, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.25f))
                drawCircle(color = pinAccentColor, radius = r3, center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.7f))
                drawCircle(color = pinAccentColor, radius = r3, center = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.3f))
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minLat = validPins.minOf { it.lat }
                val maxLat = validPins.maxOf { it.lat }
                val minLon = validPins.minOf { it.lon }
                val maxLon = validPins.maxOf { it.lon }
                val spanLat = (maxLat - minLat).coerceAtLeast(0.0001)
                val spanLon = (maxLon - minLon).coerceAtLeast(0.0001)
                val pad = 0.15f
                validPins.forEach { g ->
                    val nx = ((g.lon - minLon) / spanLon).toFloat().coerceIn(0f, 1f)
                    val ny = 1f - ((g.lat - minLat) / spanLat).toFloat().coerceIn(0f, 1f)
                    val x = size.width * (pad + nx * (1f - 2 * pad))
                    val y = size.height * (pad + ny * (1f - 2 * pad))
                    drawCircle(color = PrimaryBlue, radius = rPin, center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }
    }
}
