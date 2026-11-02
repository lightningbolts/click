@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.appVersionLabel
import compose.project.click.click.data.flags.FeatureFlag
import compose.project.click.click.data.flags.FeatureFlags
import compose.project.click.click.data.places.ClickWebPlacesRepository
import compose.project.click.click.data.places.EnergyLabel
import compose.project.click.click.data.places.PlaceCheckInPhase
import compose.project.click.click.data.places.PlaceCopy
import compose.project.click.click.data.places.PlaceDetail
import compose.project.click.click.data.places.PlaceDetailController
import compose.project.click.click.data.places.PlaceDetailUiState
import compose.project.click.click.data.places.PlaceError
import compose.project.click.click.data.places.PlaceLoadState
import compose.project.click.click.data.places.PlaceLocationOutcome
import compose.project.click.click.data.places.PlacePulsePhase
import compose.project.click.click.data.places.PulseQuestionDto
import compose.project.click.click.openInAppBrowser
import compose.project.click.click.platform.shareText
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppPermission
import compose.project.click.click.ui.utils.AppPermissionController
import compose.project.click.click.ui.utils.AppPermissionState
import compose.project.click.click.ui.utils.rememberAppPermissionController
import compose.project.click.click.util.formatClockTime
import compose.project.click.click.util.formatShortDateTime
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import kotlin.coroutines.resume

/** Energy colors, Chill … Packed (iOS `EnergyLabel.color`). */
internal fun EnergyLabel.color(): Color =
    when (this) {
        EnergyLabel.CHILL -> Color(0xFF4FA3E0)
        EnergyLabel.STEADY -> Color(0xFF34A853)
        EnergyLabel.LIVELY -> Color(0xFFF29D38)
        EnergyLabel.PACKED -> Color(0xFFE5484D)
    }

/** One foreground fix for this tap, like event check-in (no monitoring, nothing stored). */
private suspend fun locatePlaceOnce(permissions: AppPermissionController): PlaceLocationOutcome {
    if (permissions.state(AppPermission.Location) == AppPermissionState.CanRequest) {
        suspendCancellableCoroutine { cont -> permissions.request(AppPermission.Location) { if (cont.isActive) cont.resume(Unit) } }
    }
    if (permissions.state(AppPermission.Location) != AppPermissionState.Granted) return PlaceLocationOutcome.Denied
    val service = LocationService()
    val fix = service.getHighAccuracyLocation(timeoutMs = 8000L) ?: service.getCurrentLocation() ?: return PlaceLocationOutcome.Unavailable
    return PlaceLocationOutcome.Fix(fix.latitude, fix.longitude, fix.accuracyMeters)
}

/**
 * A Click Place page (spec §6.4). Mirrors iOS `Click/Features/Places/PlaceDetailView.swift`: Now,
 * Your visit (check-in + Pulse), Happening here, Your Clicks, Your history, Community, About.
 * Hidden unless `click_places` is on.
 */
@Composable
internal fun PlaceDetailSheet(
    idOrSlug: String,
    onDismiss: () -> Unit,
    source: String? = null,
    anchorToken: String? = null,
    onOpenEvent: ((String) -> Unit)? = null,
    onOpenHub: ((String) -> Unit)? = null,
) {
    val flags by FeatureFlags.state.collectAsState()
    val permissions = rememberAppPermissionController()
    val controller =
        remember(idOrSlug) {
            PlaceDetailController(
                idOrSlug = idOrSlug,
                anchorToken = anchorToken,
                repository = ClickWebPlacesRepository(),
                source = source ?: if (anchorToken == null) "link" else "qr",
                appVersion = appVersionLabel().substringBefore(' ').takeIf { it.isNotBlank() },
                locate = { locatePlaceOnce(permissions) },
            )
        }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val enabled = flags.isEnabled(FeatureFlag.CLICK_PLACES)

    LaunchedEffect(controller, enabled) { if (enabled) controller.load() }
    // Refresh while checked in and on screen: a foreground loop only, no background work.
    LaunchedEffect(controller, state.isCheckedIn) {
        while (state.isCheckedIn) {
            delay(60_000L)
            controller.load()
        }
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss, fillBody = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.detail?.summary?.name ?: "Place",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            state.detail?.let { detail ->
                // Never includes an anchor token.
                IconButton(onClick = { shareText(PlaceCopy.shareUrl(detail.summary.slugOrId), detail.summary.name) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share")
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        val detail = state.detail
        val load = state.load
        when {
            !enabled -> PlaceMessage("Not available")
            detail != null ->
                PlaceDetailBody(
                    detail = detail,
                    state = state,
                    nowMs = Clock.System.now().toEpochMilliseconds(),
                    onCheckIn = { scope.launch { controller.checkIn() } },
                    onCheckOut = { scope.launch { controller.checkOut() } },
                    onShareChanged = controller::setShareWithConnections,
                    onOpenSettings = { permissions.openSettings(AppPermission.Location) },
                    onEnergy = { scope.launch { controller.submitEnergy(it) } },
                    onFollowUp = { q, v -> scope.launch { controller.answerFollowUp(q, v) } },
                    onWouldReturn = { scope.launch { controller.answerWouldReturn(it) } },
                    onOpenEvent = onOpenEvent,
                    onOpenHub = onOpenHub,
                )
            load is PlaceLoadState.Failed -> PlaceMessage(load.error.message())
            else ->
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                }
        }
    }

    if (state.showAnchorConfirmation && state.detail != null) {
        AlertDialog(
            onDismissRequest = controller::dismissAnchorConfirmation,
            title = { Text("Check in at ${state.detail?.summary?.name ?: "this Place"}?") },
            confirmButton = { TextButton(onClick = { scope.launch { controller.confirmAnchorCheckIn() } }) { Text("Check in") } },
            dismissButton = { TextButton(onClick = controller::dismissAnchorConfirmation) { Text("Not now") } },
        )
    }
}

@Composable
private fun PlaceMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaceSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SupportingText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    contentColor: Color = Color.White,
) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun PlaceDetailBody(
    detail: PlaceDetail,
    state: PlaceDetailUiState,
    nowMs: Long,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onShareChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onEnergy: (Int) -> Unit,
    onFollowUp: (PulseQuestionDto, Int) -> Unit,
    onWouldReturn: (Int?) -> Unit,
    onOpenEvent: ((String) -> Unit)?,
    onOpenHub: ((String) -> Unit)?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") { PlaceHeader(detail) }
        item(key = "now") { PlaceNowSection(detail, nowMs) }
        item(key = "visit") {
            PlaceVisitSection(
                detail,
                state,
                nowMs,
                onCheckIn,
                onCheckOut,
                onShareChanged,
                onOpenSettings,
                onEnergy,
                onFollowUp,
                onWouldReturn,
            )
        }
        placeEventsSection(detail, onOpenEvent)
        item(key = "clicks") { PlaceClicksSection(detail) }
        item(key = "history") { PlaceHistorySection(detail) }
        detail.hub?.let { hub ->
            item(key = "community") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceSectionTitle("Community")
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable(enabled = onOpenHub != null) { onOpenHub?.invoke(hub.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Place Hub", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (!hub.joined) SupportingText("Check in to join")
                    }
                }
            }
        }
        item(key = "about") { PlaceAboutSection(detail) }
        if (detail.isManager) {
            item(key = "manager") {
                TextButton(onClick = { openInAppBrowser(PlaceCopy.MANAGER_URL) }) { Text(PlaceCopy.MANAGER_NOTE) }
            }
        }
    }
}

@Composable
private fun PlaceHeader(detail: PlaceDetail) {
    val summary = detail.summary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val photo = summary.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    summary.placeCategory.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(summary.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SupportingText(PlaceCopy.categoryCity(summary))
        detail.todayHoursLabel?.let { hours ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SupportingText(hours)
                summary.openNow?.let { open ->
                    if (open) {
                        Pill("Open", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
                    } else {
                        Pill("Closed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceNowSection(
    detail: PlaceDetail,
    nowMs: Long,
) {
    val pulse = detail.summary.pulse
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceSectionTitle("Now")
        val label = pulse.energy
        if (pulse.isLive && label != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(label.title, label.color())
                SupportingText(PlaceCopy.liveDetail(pulse, nowMs))
                PlaceCopy.confidence(pulse.confidence)?.let {
                    Pill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EnergyBars(pulse.bars)
            val category = pulse.category
            if (category?.question != null && category.counts.isNotEmpty()) {
                SupportingText(PlaceCopy.categoryLine(category.question, category.counts))
            }
            PlaceCopy.easyToTalk(pulse)?.let { SupportingText(it) }
        } else {
            Text(PlaceCopy.pulseLine(pulse, nowMs), style = MaterialTheme.typography.bodyLarge)
        }
        detail.pattern?.let(PlaceCopy::pattern)?.let { SupportingText(it) }
        PlaceCopy.hereNow(detail.summary.hereNowCount)?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        PlaceCopy.hereNowConnections(detail.hereNowConnections)?.let { SupportingText(it) }
    }
}

/** Raw report counts, Chill … Packed. */
@Composable
private fun EnergyBars(distribution: List<Int>) {
    val total = distribution.sum().coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        EnergyLabel.entries.forEachIndexed { index, label ->
            val count = distribution.getOrElse(index) { 0 }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((36f * count / total).coerceAtLeast(4f).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(label.color().copy(alpha = if (count > 0) 1f else 0.2f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${label.title} $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaceVisitSection(
    detail: PlaceDetail,
    state: PlaceDetailUiState,
    nowMs: Long,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onShareChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onEnergy: (Int) -> Unit,
    onFollowUp: (PulseQuestionDto, Int) -> Unit,
    onWouldReturn: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceSectionTitle("Your visit")
        val checkIn = detail.checkIn
        if (checkIn != null && checkIn.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PlaceCopy.checkedIn(checkIn.expiresAtMs),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCheckOut) { Text("Leave") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Let my Clicks see I'm here", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.shareWithConnections, onCheckedChange = onShareChanged)
            }
            Button(onClick = onCheckIn, enabled = !state.checkIn.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (state.checkIn.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("I'm here", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        (state.checkIn as? PlaceCheckInPhase.Failed)?.let { failed ->
            SupportingText(failed.error.message(), MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = onCheckIn) { Text("Try again") }
                if (failed.error == PlaceError.NoLocation) TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }
        val wouldReturn = state.wouldReturnQuestion
        if (state.showWouldReturn && wouldReturn != null) WouldReturnRow(wouldReturn, onWouldReturn)
        if (state.showsPulseCard) PulseCard(state, nowMs, onEnergy, onFollowUp)
    }
}

/** §6.8: four energy chips (one tap submits), then optional follow-ups while editable. */
@Composable
private fun PulseCard(
    state: PlaceDetailUiState,
    nowMs: Long,
    onEnergy: (Int) -> Unit,
    onFollowUp: (PulseQuestionDto, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        if (state.pulseSubmitted) {
            Text("Thanks — Pulse updated", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            state.remainingFollowUps(nowMs).forEach { question ->
                SupportingText(question.prompt)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEach { option ->
                        OutlinedButton(onClick = { onFollowUp(question, option.value) }) { Text(option.label) }
                    }
                }
            }
        } else {
            Text(state.energyPrompt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnergyLabel.entries.forEach { label ->
                    OutlinedButton(
                        onClick = { onEnergy(label.value) },
                        enabled = state.energyChipsEnabled,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(label.title, color = if (state.energyChipsEnabled) label.color() else Color.Unspecified, maxLines = 1)
                    }
                }
            }
            val eligibility = state.detail?.pulseEligibility
            val until = eligibility?.cooldownUntilMs
            if (until != null && eligibility.reason == "cooldown") {
                SupportingText("You can update your Pulse at ${formatClockTime(until)}")
            }
            (state.pulse as? PlacePulsePhase.Failed)?.let { SupportingText(it.error.message(), MaterialTheme.colorScheme.error) }
        }
    }
}

/** "Come back at this time?" after Leave: shown for 30 s or until answered or dismissed. */
@Composable
private fun WouldReturnRow(
    question: PulseQuestionDto,
    onAnswer: (Int?) -> Unit,
) {
    LaunchedEffect(question.key) {
        delay(30_000L)
        onAnswer(null)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question.prompt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        question.options.forEach { option ->
            OutlinedButton(onClick = { onAnswer(option.value) }) { Text(option.label) }
        }
        IconButton(onClick = { onAnswer(null) }) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
    }
}

private fun LazyListScope.placeEventsSection(
    detail: PlaceDetail,
    onOpenEvent: ((String) -> Unit)?,
) {
    if (detail.upcomingEvents.isEmpty()) return
    item(key = "events_title") { PlaceSectionTitle("Happening here") }
    detail.upcomingEvents.forEach { event ->
        item(key = "event_${event.beaconId}") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = onOpenEvent != null) { onOpenEvent?.invoke(event.beaconId) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    event.imageUrl?.takeIf { it.isNotBlank() }?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    event.startsAtMs?.let { SupportingText(formatShortDateTime(it)) }
                }
                if (event.isLive) Pill("Live", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlaceClicksSection(detail: PlaceDetail) {
    val been = detail.clicksBeenHere?.let(PlaceCopy::clicksBeenHere)
    val met = detail.youMetHere?.let(PlaceCopy::youMetHere)
    if (been == null && met == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceSectionTitle("Your Clicks")
        been?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        met?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun PlaceHistorySection(detail: PlaceDetail) {
    val history = detail.ownHistory ?: return
    val checkIns = PlaceCopy.checkInHistory(history)
    val clicks = PlaceCopy.clicksMadeHere(history.encounterCount)
    if (checkIns == null && clicks == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceSectionTitle("Your history")
        checkIns?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        clicks?.let { SupportingText(it) }
    }
}

@Composable
private fun PlaceAboutSection(detail: PlaceDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaceSectionTitle("About")
        detail.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        detail.websiteUrl?.let { url ->
            TextButton(onClick = { openInAppBrowser(url) }) { Text("Website") }
        }
        detail.directionsUrl?.let { url ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable { openInAppBrowser(url) }.padding(vertical = 6.dp),
            ) {
                Text("Directions", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                detail.summary.addressLine?.let { SupportingText(it) }
            }
        }
    }
}
