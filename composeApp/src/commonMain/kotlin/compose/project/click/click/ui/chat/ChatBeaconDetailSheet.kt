package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.repository.MapBeaconRepository
import compose.project.click.click.ui.components.ClickSheetDefaults
import compose.project.click.click.ui.components.ClickSheetDialogChrome
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.ui.components.UnifiedToastHost
import compose.project.click.click.ui.components.rememberUnifiedToastState
import compose.project.click.click.ui.screens.BeaconDetailSheetContent
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot
import compose.project.click.click.ui.utils.haversineDistance
import compose.project.click.click.viewmodel.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full map/nearby beacon detail sheet opened from a chat beacon card.
 */
@Composable
internal fun ChatBeaconDetailSheet(
    beaconId: String,
    mapViewModel: MapViewModel,
    knownBeacons: List<MapBeacon>,
    onDismissRequest: () -> Unit,
    onShareBeaconToChat: ((MapBeacon) -> Unit)? = null,
) {
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    var resolved by remember(beaconId) {
        mutableStateOf(
            knownBeacons.firstOrNull { it.id == beaconId }
                ?: mapBeacons.firstOrNull { it.id == beaconId },
        )
    }
    var loadError by remember(beaconId) { mutableStateOf<String?>(null) }
    val toastState = rememberUnifiedToastState()

    LaunchedEffect(beaconId) {
        if (resolved != null) return@LaunchedEffect
        loadError = null
        val fetched = withContext(Dispatchers.Default) {
            MapBeaconRepository().fetchBeacon(beaconId).getOrNull()
        }
        if (fetched != null) {
            resolved = fetched
        } else {
            loadError = "Couldn't load this beacon."
        }
    }

    val detailSurface = GlassSheetTokens.OledBlack()
    val onDetailSurface = GlassSheetTokens.OnOled()
    val beacon = resolved
    val distanceMeters = beacon?.let { b ->
        AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
            haversineDistance(lat, lon, b.latitude, b.longitude)
        }
    }

    MapBeaconSheetRoot(
        visible = true,
        onDismissRequest = onDismissRequest,
        containerColor = detailSurface,
        contentColor = onDetailSurface,
        scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        appColorScheme = MaterialTheme.colorScheme,
        appTypography = MaterialTheme.typography,
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            ClickSheetDialogChrome(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                sheetColor = detailSurface,
                onSurface = onDetailSurface,
                alignSemanticColorsToSheet = true,
            ) {
                when {
                    beacon != null -> {
                        BeaconDetailSheetContent(
                            beacon = beacon,
                            distanceMeters = distanceMeters,
                            currentUserId = currentUser?.id,
                            viewModel = mapViewModel,
                            onShareBeaconToChat = onShareBeaconToChat,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                    loadError != null -> {
                        Text(
                            text = loadError ?: "Couldn't load this beacon.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            UnifiedToastHost(
                state = toastState,
                opaque = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .zIndex(100f),
            )
        }
    }
}
