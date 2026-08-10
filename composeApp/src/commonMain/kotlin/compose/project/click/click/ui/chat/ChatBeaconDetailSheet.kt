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
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.chatBeaconLooksLikeEvent
import compose.project.click.click.data.models.mapBeaconFromChatMetadata
import compose.project.click.click.data.models.resolveChatBeaconForDetail
import compose.project.click.click.data.repository.MapBeaconRepository
import compose.project.click.click.ui.components.BeaconShareToChatDialog
import compose.project.click.click.ui.components.ClickSheetDefaults
import compose.project.click.click.ui.components.ClickSheetDialogChrome
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.ui.components.sheetBodyScroll
import compose.project.click.click.ui.components.UnifiedToastHost
import compose.project.click.click.ui.components.rememberUnifiedToastState
import compose.project.click.click.ui.screens.BeaconDetailSheetContent
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot
import compose.project.click.click.ui.utils.haversineDistance
import compose.project.click.click.viewmodel.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Full map/nearby beacon detail sheet opened from a chat beacon card.
 * Loads expired / out-of-discovery beacons via authenticated GET; falls back to
 * chat message metadata so the sheet never dead-ends on "Couldn't load".
 */
@Composable
internal fun ChatBeaconDetailSheet(
    beaconId: String,
    mapViewModel: MapViewModel,
    knownBeacons: List<MapBeacon>,
    onDismissRequest: () -> Unit,
    onShareBeaconToChats: ((
        beacon: MapBeacon,
        chatIds: List<String>,
        openConnectionId: String?,
    ) -> Unit)? = null,
    /** Snapshot reconstructed from the chat card metadata when the live row is gone. */
    messageFallback: MapBeacon? = null,
    messageMetadata: JsonObject? = null,
    messageContent: String? = null,
) {
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val inboxChats by AppDataManager.inboxFeedChats.collectAsState()
    var resolved by remember(beaconId) {
        mutableStateOf(
            resolveChatBeaconForDetail(
                cached = knownBeacons.firstOrNull { it.id == beaconId }
                    ?: mapBeacons.firstOrNull { it.id == beaconId },
                messageFallback = messageFallback,
                messageMetadata = messageMetadata,
            ) ?: messageFallback,
        )
    }
    var loadError by remember(beaconId) { mutableStateOf<String?>(null) }
    var shareBeaconToChat by remember(beaconId) { mutableStateOf<MapBeacon?>(null) }
    val toastState = rememberUnifiedToastState()

    LaunchedEffect(beaconId, messageMetadata, messageContent) {
        val looksLikeEvent = chatBeaconLooksLikeEvent(messageFallback, messageMetadata)
        val cacheHit = knownBeacons.any { it.id == beaconId } || mapBeacons.any { it.id == beaconId }
        val current = resolved
        // Skip network only when cache already has the correct EVENT kind for event cards.
        if (current != null && current.id == beaconId && cacheHit &&
            (!looksLikeEvent || current.kind == MapBeaconKind.EVENT)
        ) {
            resolved = resolveChatBeaconForDetail(current, messageFallback, messageMetadata)
            return@LaunchedEffect
        }
        loadError = null
        val fetched = withContext(Dispatchers.Default) {
            MapBeaconRepository().fetchBeacon(beaconId).getOrNull()
        }
        if (fetched != null) {
            resolved = resolveChatBeaconForDetail(fetched, messageFallback, messageMetadata)
            return@LaunchedEffect
        }
        val fromMeta = messageFallback
            ?: mapBeaconFromChatMetadata(beaconId, messageMetadata, messageContent.orEmpty())
        if (fromMeta != null) {
            resolved = resolveChatBeaconForDetail(fromMeta, messageFallback, messageMetadata)
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
        Box(modifier = Modifier.fillMaxWidth()) {
            ClickSheetDialogChrome(
                modifier = Modifier.fillMaxWidth(),
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
                            onShareBeaconToChat = { shareBeaconToChat = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sheetBodyScroll()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                    loadError != null -> {
                        Text(
                            text = loadError ?: "Couldn't load this beacon.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onDetailSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                    .zIndex(20f)
                    .padding(bottom = 24.dp),
            )
            shareBeaconToChat?.let { beaconToShare ->
                BeaconShareToChatDialog(
                    beacon = beaconToShare,
                    chats = inboxChats,
                    onDismissRequest = { shareBeaconToChat = null },
                    onShare = { selectedChatIds, openChatConnectionId ->
                        onShareBeaconToChats?.invoke(
                            beaconToShare,
                            selectedChatIds,
                            openChatConnectionId,
                        )
                        shareBeaconToChat = null
                    },
                )
            }
        }
    }
}
