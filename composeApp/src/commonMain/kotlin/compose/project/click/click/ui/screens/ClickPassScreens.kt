@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.getEventPass // pragma: allowlist secret
import compose.project.click.click.data.api.scanEventPass // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.heroImageUrl // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.deeplink.EventPassDestination // pragma: allowlist secret
import compose.project.click.click.deeplink.EventPassRouter // pragma: allowlist secret
import compose.project.click.click.events.CLICK_PASS_POLL_MS // pragma: allowlist secret
import compose.project.click.click.events.ClickPass // pragma: allowlist secret
import compose.project.click.click.events.ClickPassFetch // pragma: allowlist secret
import compose.project.click.click.events.ClickPassStore // pragma: allowlist secret
import compose.project.click.click.events.EventChatOpenState // pragma: allowlist secret
import compose.project.click.click.events.EventChatResolver // pragma: allowlist secret
import compose.project.click.click.events.PassScanGate // pragma: allowlist secret
import compose.project.click.click.events.PassScanOutcome // pragma: allowlist secret
import compose.project.click.click.events.PassScanTone // pragma: allowlist secret
import compose.project.click.click.events.clickPassShouldPoll // pragma: allowlist secret
import compose.project.click.click.events.clickPassStatusLine // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.events.isLive // pragma: allowlist secret
import compose.project.click.click.events.passScanHaptic // pragma: allowlist secret
import compose.project.click.click.events.passScanLook // pragma: allowlist secret
import compose.project.click.click.events.passScannerSubtitle // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.ui.components.*
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Shows the Click Pass or the door scanner over the app shell when [EventPassRouter] has a
 * destination (deep links `click://pass/{id}[/scan]`, the ongoing notification, the event page).
 */
@Composable
fun BoxScope.EventPassOverlayHost() {
    val destination by EventPassRouter.destination.collectAsState()
    val user by AppDataManager.currentUser.collectAsState()
    LaunchedEffect(user?.id) { ClickPassStore.bindUser(user?.id) }
    val current = destination ?: return
    if (user?.id.isNullOrBlank()) return
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(60f)
                .background(MaterialTheme.colorScheme.background),
    ) {
        PlatformBackHandler(enabled = true) { EventPassRouter.close() }
        when (current) {
            is EventPassDestination.Pass -> ClickPassScreen(beaconId = current.beaconId, onBack = EventPassRouter::close)
            is EventPassDestination.Scanner -> PassScannerScreen(beaconId = current.beaconId, onBack = EventPassRouter::close)
        }
    }
}

/** The beacon for a full-screen event surface: the map's copy at once, then the server's. */
@Composable
internal fun rememberEventBeacon(beaconId: String): MapBeacon? {
    val prefetched by AppDataManager.prefetchedMapBeacons.collectAsState()
    var fetched by remember(beaconId) { mutableStateOf<MapBeacon?>(null) }
    LaunchedEffect(beaconId) {
        ApiClient().getMapBeacon(beaconId).onSuccess { fetched = it }
    }
    return fetched ?: prefetched.firstOrNull { it.id == beaconId }
}

/** True while the hosting screen is resumed (polls run only then). */
@Composable
internal fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

@Composable
internal fun PassAvatar(
    url: String?,
    name: String?,
    seed: String,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(stableAvatarPlaceholderColor(seed)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsForAvatar(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp,
        )
        url?.trim()?.takeIf { it.isNotEmpty() }?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** The ticket's tear line: a dashed rule between two notches cut into the card's edges. */
@Composable
private fun PerforatedDivider(notchColor: Color) {
    val dash = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
        val r = size.height / 2
        drawCircle(notchColor, radius = r, center = Offset(0f, r))
        drawCircle(notchColor, radius = r, center = Offset(size.width, r))
        drawLine(
            color = dash,
            start = Offset(r + 6f, r),
            end = Offset(size.width - r - 6f, r),
            strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f)),
        )
    }
}

private sealed interface PassUiState {
    data object Loading : PassUiState

    data class Showing(
        val pass: ClickPass,
    ) : PassUiState

    data object NotGoing : PassUiState

    data class Error(
        val message: String,
    ) : PassUiState
}

/**
 * Your Click Pass (sheet 08 G3-15; iOS `ClickPassView`): a ticket with the QR your host scans,
 * your name and face, and the things you need on the way there. Kept on the device, so it opens
 * at once and works offline; while the event is near and you're not in yet, it re-checks every 4 s.
 */
@Composable
fun ClickPassScreen(
    beaconId: String,
    onBack: () -> Unit,
) {
    val beacon = rememberEventBeacon(beaconId)
    val me by AppDataManager.currentUser.collectAsState()
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var state by remember(beaconId) {
        mutableStateOf<PassUiState>(ClickPassStore.cached(beaconId)?.let { PassUiState.Showing(it) } ?: PassUiState.Loading)
    }

    fun show(fresh: ClickPass) {
        val previous = (state as? PassUiState.Showing)?.pass
        if (previous != null && !previous.isCheckedIn && fresh.isCheckedIn) {
            // The host just scanned you in.
            PlatformHapticsPolicy.successNotification()
        }
        state = PassUiState.Showing(fresh)
    }

    suspend fun load() {
        val fetch = api.getEventPass(beaconId)
        ClickPassStore.apply(beaconId, fetch)
        when (fetch) {
            is ClickPassFetch.Loaded -> show(fetch.pass)
            ClickPassFetch.NotGoing -> state = PassUiState.NotGoing
            // A pass already on the device stays usable offline.
            is ClickPassFetch.Failed -> if (state !is PassUiState.Showing) state = PassUiState.Error(fetch.message)
        }
    }

    LaunchedEffect(beaconId) { load() }

    val resumed = rememberIsResumed()
    val schedule = beacon?.eventSchedule()
    val showing = (state as? PassUiState.Showing)?.pass
    val watching = clickPassShouldPoll(showing, schedule, nowMs())
    LaunchedEffect(beaconId, watching, resumed) {
        if (!watching || !resumed) return@LaunchedEffect
        while (true) {
            delay(CLICK_PASS_POLL_MS)
            val fetch = api.getEventPass(beaconId)
            ClickPassStore.apply(beaconId, fetch)
            when (fetch) {
                is ClickPassFetch.Loaded -> {
                    show(fetch.pass)
                    if (fetch.pass.isCheckedIn) return@LaunchedEffect
                }
                ClickPassFetch.NotGoing -> {
                    state = PassUiState.NotGoing
                    return@LaunchedEffect
                }
                is ClickPassFetch.Failed -> Unit
            }
        }
    }

    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        AppScreenWithFloatingHeader(
            title = "Click Pass",
            navigationIcon = { HeaderBackIconButton(onClick = onBack) },
        ) { contentModifier ->
            Column(
                modifier = contentModifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val s = state) {
                    PassUiState.Loading ->
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    PassUiState.NotGoing ->
                        PassEmptyState(
                            title = "No pass yet",
                            message = "RSVP to the event and your Click Pass appears here.",
                            actionLabel = "View Event",
                            onAction = {
                                EventDeepLinkRouter.setPendingBeaconId(beaconId)
                                onBack()
                            },
                        )
                    is PassUiState.Error ->
                        PassEmptyState(
                            title = "Couldn't load your pass",
                            message = s.message,
                            actionLabel = "Try Again",
                            onAction = {
                                state = PassUiState.Loading
                                scope.launch { load() }
                            },
                        )
                    is PassUiState.Showing -> {
                        // A dim screen is the usual reason a scan fails; a sleeping one is the other.
                        KeepScreenBrightAndAwake()
                        ClickPassTicket(
                            beaconId = beaconId,
                            beacon = beacon,
                            pass = s.pass,
                            holderName = me?.name,
                            holderAvatar = me?.image,
                            holderSeed = me?.id.orEmpty(),
                        )
                        beacon?.let { ClickPassActions(it, onBack) }
                        Text(
                            text = "Your host scans this at the door. It's yours alone: if it's shared, the host sees your name and photo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.ConfirmationNumber,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ClickButton(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ClickPassTicket(
    beaconId: String,
    beacon: MapBeacon?,
    pass: ClickPass,
    holderName: String?,
    holderAvatar: String?,
    holderSeed: String,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val schedule = beacon?.eventSchedule()
    Column(
        modifier =
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(surface),
    ) {
        CardVisualHero(
            id = beaconId,
            imageUrl = beacon?.metadata?.heroImageUrl(),
            scrim = false,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = beacon?.displayDynamicTitle() ?: " ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            schedule?.let { PassInfoLine(Icons.Filled.CalendarMonth, formatEventScheduleRange(it)) }
            val place = beacon?.metadata?.locationName ?: beacon?.metadata?.formattedAddress
            place?.let { PassInfoLine(Icons.Filled.Place, it) }
        }
        PerforatedDivider(notchColor = MaterialTheme.colorScheme.background)
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .alpha(if (pass.isCheckedIn) 0.35f else 1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(16.dp),
                ) {
                    QrMatrixCode(
                        data = pass.credentialUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Click Pass QR code, ${pass.code}",
                    )
                }
                if (pass.isCheckedIn) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Checked in",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(72.dp).background(Color.White, CircleShape),
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = pass.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = 3.sp,
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PassAvatar(url = holderAvatar, name = holderName, seed = holderSeed, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(holderName?.takeIf { it.isNotBlank() } ?: "You", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        text = clickPassStatusLine(pass, live = schedule?.isLive() == true),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pass.isCheckedIn) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PassInfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Calendar, Directions and a Contact menu (Place / Message the host / Ask in Event Chat). */
@Composable
private fun ClickPassActions(
    beacon: MapBeacon,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { runCatching { uriHandler.openUri(it) } }
    var showCalendar by remember { mutableStateOf(false) }
    var showDirections by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    val title = beacon.displayDynamicTitle()
    Row(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (beacon.eventSchedule() != null) {
            EventActionTile("Calendar", EventCalendarIcon, { showCalendar = true }, Modifier.weight(1f))
        }
        EventActionTile("Directions", Icons.Filled.Directions, { showDirections = true }, Modifier.weight(1f))
        Box(Modifier.weight(1f)) {
            EventActionTile("Contact", Icons.Filled.Forum, { showContact = true }, Modifier.fillMaxWidth())
            EventHostContactMenu(
                beacon = beacon,
                expanded = showContact,
                onDismiss = { showContact = false },
                onNavigated = onClose,
            )
        }
    }
    if (showCalendar) EventCalendarSheet(beacon, title, { showCalendar = false }, openUri)
    if (showDirections) {
        EventDirectionsSheet(
            beacon = beacon,
            label = beacon.metadata.locationName ?: title,
            onDismiss = { showDirections = false },
            openUri = openUri,
        )
    }
}

/**
 * Ways to reach an event's host: their Place page when a Place hosts it, a direct message when
 * they're your Click, and always the event chat (iOS `HostContactMenu`).
 */
@Composable
internal fun EventHostContactMenu(
    beacon: MapBeacon,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connections by AppDataManager.connections.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val me by AppDataManager.currentUser.collectAsState()
    val hostId = beacon.createdByUserId
    val hostConnection =
        hostId?.takeIf { it != me?.id }?.let { id -> connections.firstOrNull { id in it.user_ids } }
    val hostFirstName =
        hostId?.let { connectedUsers[it] }?.let { u ->
            u.firstName?.takeIf { it.isNotBlank() } ?: u.name?.trim()?.substringBefore(' ')
        }
    val items =
        buildList {
            beacon.place?.let { place ->
                // The Place page is G5-3; until then the row is inert.
                add(ClickMenuItem("View ${place.name}", { onDismiss() }, Icons.Filled.Storefront))
            }
            if (hostConnection != null) {
                add(
                    ClickMenuItem("Message ${hostFirstName ?: "host"}", {
                        onDismiss()
                        ChatDeepLinkManager.setPendingChat(hostConnection.id)
                        onNavigated()
                    }, Icons.Filled.ChatBubble),
                )
            }
            add(
                ClickMenuItem("Ask in Event Chat", {
                    onDismiss()
                    scope.launch {
                        val resolved = EventChatResolver().resolve(beacon.id)
                        if (resolved is EventChatOpenState.Ready) {
                            ChatDeepLinkManager.setPendingEventHub(resolved.hubId, resolved.title, resolved.creatorId)
                            onNavigated()
                        }
                    }
                }, Icons.Filled.Forum),
            )
        }
    ClickDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, items = items)
}

/**
 * The host's door (sheet 08 G3-16; iOS `PassScannerView`): scan Click Passes to check people in.
 * Every scan shows whose pass it is and what that means; the server decides.
 */
@Composable
fun PassScannerScreen(
    beaconId: String,
    onBack: () -> Unit,
) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val gate = remember { PassScanGate() }
    var outcome by remember { mutableStateOf<PassScanOutcome?>(null) }
    var outcomeSerial by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var checkedInCount by remember { mutableStateOf<Int?>(null) }

    fun check(value: String) {
        if (checking) return
        if (!gate.shouldSend(value, nowMs())) return
        checking = true
        scope.launch {
            val result = api.scanEventPass(beaconId, value)
            checking = false
            result
                .onSuccess { scan ->
                    scan.checkInCount?.let { checkedInCount = it }
                    val next = PassScanOutcome.Scanned(scan)
                    passScanHaptic(passScanLook(next).tone)
                    outcome = next
                }.onFailure { error ->
                    // Let the same pass be tried again straight away.
                    gate.reset()
                    passScanHaptic(PassScanTone.ERROR)
                    outcome = PassScanOutcome.Failed(error.message ?: "Check your connection and try again.")
                }
            outcomeSerial += 1
        }
    }

    KeepScreenAwake()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        QRScanner(
            modifier = Modifier.fillMaxSize(),
            isActive = true,
            deniedMessage = "Camera access is required to scan Click Passes.",
            deliverRepeats = true,
            onResult = { check(it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Text("Scan Passes", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
        ) {
            AnimatedContent(targetState = outcome to outcomeSerial, label = "passScanOutcome") { (current, _) ->
                if (current == null) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(if (checking) "Checking…" else "Scan a Click Pass", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            passScannerSubtitle(checkedInCount),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    PassScanCard(current, checking)
                }
            }
        }
    }
}

@Composable
private fun PassScanCard(
    outcome: PassScanOutcome,
    checking: Boolean,
) {
    val look = passScanLook(outcome)
    val tint =
        when (look.tone) {
            PassScanTone.SUCCESS -> Color(0xFF22C55E)
            PassScanTone.WARNING -> Color(0xFFF59E0B)
            PassScanTone.ERROR -> Color(0xFFEF4444)
        }
    val icon =
        when (look.tone) {
            PassScanTone.SUCCESS -> Icons.Filled.CheckCircle
            PassScanTone.WARNING -> Icons.Filled.Warning
            PassScanTone.ERROR -> Icons.Filled.Cancel
        }
    val holder = (outcome as? PassScanOutcome.Scanned)?.scan?.holder
    val tierName = (outcome as? PassScanOutcome.Scanned)?.scan?.tierName
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        holder?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PassAvatar(url = it.avatarUrl, name = it.name, seed = it.userId, size = 64.dp)
                Column {
                    Text(it.name ?: "Guest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    tierName?.let { tier ->
                        Text(tier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.copy(alpha = 0.14f))
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(look.title, fontWeight = FontWeight.SemiBold)
                look.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/** The event page's "Your Click Pass" card (shown while going and not expired). */
@Composable
internal fun ClickPassEntryCard(
    beaconId: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable { EventPassRouter.open(EventPassDestination.Pass(beaconId)) }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Your Click Pass", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
