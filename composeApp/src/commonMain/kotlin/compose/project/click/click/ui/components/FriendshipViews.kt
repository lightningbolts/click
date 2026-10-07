@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalLayoutApi::class)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.data.api.PendingHangoutDto // pragma: allowlist secret
import compose.project.click.click.data.models.FriendshipEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.FriendshipLevel // pragma: allowlist secret
import compose.project.click.click.data.models.FriendshipStats // pragma: allowlist secret
import compose.project.click.click.data.models.GroupHangout // pragma: allowlist secret
import compose.project.click.click.data.models.HangoutHighlights // pragma: allowlist secret
import compose.project.click.click.data.models.UpcomingPlan // pragma: allowlist secret
import compose.project.click.click.ui.chat.planWhenText // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareDecryptedImage // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret
import compose.project.click.click.util.formatClockTime // pragma: allowlist secret
import compose.project.click.click.util.formatWeekdayDate // pragma: allowlist secret
import compose.project.click.click.utils.toPngBytes // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/*
 * Friendship UI (iOS `Features/Profile/FriendshipViews.swift`): the peer profile "Together"
 * section, the encounter map, "Your story" and the post-tap souvenir card.
 */

// region palette + sharing

/** Five gradient pairs picked by a djb2 hash of a seed, so a friendship keeps its colors. */
object SouvenirPalette {
    private val pairs: List<Pair<Color, Color>> =
        listOf(
            Color(0xFF6D28D9) to Color(0xFFDB2777),
            Color(0xFF1D4ED8) to Color(0xFF0EA5E9),
            Color(0xFF047857) to Color(0xFF65A30D),
            Color(0xFFB45309) to Color(0xFFDC2626),
            Color(0xFF0F766E) to Color(0xFF4F46E5),
        )

    fun djb2(seed: String): Long {
        var hash = 5381L
        seed.forEach { hash = ((hash shl 5) + hash + it.code) and 0xFFFFFFFFL }
        return hash
    }

    fun colors(seed: String): Pair<Color, Color> = pairs[(djb2(seed) % pairs.size).toInt()]

    fun brush(seed: String): Brush = colors(seed).let { (a, b) -> Brush.linearGradient(listOf(a, b)) }
}

/** Records [content] into [layer] so it can be exported as an image. */
internal fun Modifier.recordInto(layer: GraphicsLayer): Modifier =
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

internal suspend fun shareLayerAsPng(
    layer: GraphicsLayer,
    fileName: String,
) {
    val bytes = runCatching { layer.toImageBitmap().toPngBytes() }.getOrNull() ?: return
    shareDecryptedImage(bytes, fileName)
}

// endregion

// region formatting

private fun levelIcon(level: FriendshipLevel): ImageVector =
    when (level.rank) {
        1 -> Icons.Filled.AutoAwesome
        2 -> Icons.Filled.WavingHand
        3 -> Icons.Filled.Coffee
        4 -> Icons.Filled.Favorite
        else -> Icons.Filled.AllInclusive
    }

private fun monthYear(epochMs: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    val month =
        dt.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .take(3)
    return "$month ${dt.year}"
}

private fun plural(
    count: Int,
    one: String,
    many: String = "${one}s",
) = if (count == 1) "1 $one" else "$count $many"

private fun ordinal(n: Int): String {
    val suffix =
        if (n % 100 in 11..13) {
            "th"
        } else {
            when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
    return "$n$suffix"
}

// endregion

// region Together section

/**
 * The peer profile "Together" section: level + progress, stats, hangouts waiting on a confirmation,
 * the encounter map, upcoming plans and actions. Shown only for a real connection.
 */
@Composable
fun FriendshipSection(
    peerFirstName: String,
    peerSeed: String,
    encounters: List<FriendshipEncounter>,
    pending: List<PendingHangoutDto>,
    upcomingPlans: List<UpcomingPlan>,
    onConfirm: (String) -> Unit,
    onDecline: (String) -> Unit,
    onLogHangout: () -> Unit,
    onPlan: (() -> Unit)?,
    onOpenStory: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenPlan: (UpcomingPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Clock.System.now() }
    val stats = remember(encounters) { FriendshipStats.compute(encounters, now) }
    val highlights = remember(encounters) { HangoutHighlights.of(encounters, now) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Together", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (stats.isEmpty) {
            Text(
                "Log a hangout with $peerFirstName to start your shared story.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LevelHeader(stats)
            StatsRow(stats)
        }

        pending.forEach { hangout ->
            PendingHangoutRow(hangout, peerFirstName, onConfirm = { onConfirm(hangout.id) }, onDecline = { onDecline(hangout.id) })
        }

        if (stats.spots.any { it.latitude != null }) {
            EncounterMapPreview(
                encounters = encounters,
                spotsCount = stats.spots.size,
                newestIsNewSpot = highlights?.isNewSpot == true,
                onClick = onOpenMap,
            )
        }

        if (upcomingPlans.isNotEmpty()) {
            UpcomingPlansList(upcomingPlans, onOpenPlan)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AssistChip(onClick = onLogHangout, label = { Text("Log hangout") })
            if (onPlan != null) AssistChip(onClick = onPlan, label = { Text("Plan") })
            if (stats.hangouts >= 2) AssistChip(onClick = onOpenStory, label = { Text("Your story") })
        }
    }
}

@Composable
private fun LevelHeader(stats: FriendshipStats) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(levelIcon(stats.level), contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stats.level.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            stats.firstMet?.let {
                Text(
                    "Since ${monthYear(it.at.toEpochMilliseconds())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    LinearProgressIndicator(progress = { stats.levelProgress.toFloat() }, modifier = Modifier.fillMaxWidth())
    val next = stats.nextLevel
    Text(
        if (next != null) {
            "${plural(stats.toNextLevel, "more hangout")} to ${next.name}"
        } else {
            "The highest level. You two are inseparable."
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatsRow(stats: FriendshipStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCell(stats.hangouts.toString(), if (stats.hangouts == 1) "hangout" else "hangouts")
        StatCell(stats.spots.size.toString(), if (stats.spots.size == 1) "spot" else "spots")
        when {
            stats.weekStreak >= 2 -> StatCell("🔥 ${stats.weekStreak}", "week streak")
            stats.neighborhoods > 1 -> StatCell(stats.neighborhoods.toString(), "neighborhoods")
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PendingHangoutRow(
    hangout: PendingHangoutDto,
    peerFirstName: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val title =
        when {
            hangout.confirmedByMe -> "Waiting for $peerFirstName to confirm"
            hangout.source == "nearby" -> "Hanging out with $peerFirstName?"
            else -> "$peerFirstName logged a hangout"
        }
    val occurredMs = runCatching { Instant.parse(hangout.occurredAt).toEpochMilliseconds() }.getOrNull()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        val detail =
            listOfNotNull(
                occurredMs?.let { "${formatWeekdayDate(it)}, ${formatClockTime(it)}" },
                hangout.locationName,
            ).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hangout.awaitingMyAnswer) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                ClickSheetPrimaryButton(text = "Confirm", onClick = onConfirm, modifier = Modifier.weight(1f))
                ClickSheetSecondaryButton(text = "Not us", onClick = onDecline, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UpcomingPlansList(
    plans: List<UpcomingPlan>,
    onOpenPlan: (UpcomingPlan) -> Unit,
) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    Column {
        Text("Coming up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        plans.take(3).forEach { upcoming ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlan(upcoming) }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upcoming.plan.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(planWhenText(upcoming.plan, now), upcoming.plan.placeName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (upcoming.goingCount > 0) {
                    Text("${upcoming.goingCount} going", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// endregion

// region encounter map

private fun encounterPins(
    encounters: List<FriendshipEncounter>,
    newestIsNewSpot: Boolean,
): List<MapPin> {
    val located = encounters.sortedBy { it.at }.filter { it.latitude != null && it.longitude != null }
    return located.mapIndexed { index, encounter ->
        val isNewest = index == located.lastIndex
        MapPin(
            id = "encounter-${encounter.id}",
            title = encounter.placeName ?: "Hangout ${index + 1}",
            latitude = encounter.latitude!!,
            longitude = encounter.longitude!!,
            avatarInitials = "${index + 1}",
            caption = if (isNewest && newestIsNewSpot) "NEW" else encounter.placeName,
            zIndex = index.toFloat(),
        )
    }
}

private fun centerOf(pins: List<MapPin>): Pair<Double, Double>? =
    if (pins.isEmpty()) null else pins.map { it.latitude }.average() to pins.map { it.longitude }.average()

/** A still map with numbered pins (oldest first) and an "N spots" capsule; tap for the full map. */
@Composable
fun EncounterMapPreview(
    encounters: List<FriendshipEncounter>,
    spotsCount: Int,
    newestIsNewSpot: Boolean,
    onClick: () -> Unit,
) {
    val pins = remember(encounters, newestIsNewSpot) { encounterPins(encounters, newestIsNewSpot) }
    val center = centerOf(pins) ?: return
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(shape)
                .border(clickBorderWidth(), clickBorderColor(), shape),
    ) {
        PlatformMap(
            modifier = Modifier.fillMaxSize(),
            pins = pins,
            zoom = if (pins.size == 1) 14.0 else 11.0,
            centerLat = center.first,
            centerLon = center.second,
            mapGesturesEnabled = false,
            showCompass = false,
        )
        // Transparent catcher: the preview is static, tapping opens the interactive map.
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
        Text(
            plural(spotsCount, "spot"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Full-screen interactive "Where you've met" map. */
@Composable
fun EncounterMapDialog(
    encounters: List<FriendshipEncounter>,
    newestIsNewSpot: Boolean,
    onDismiss: () -> Unit,
) {
    val pins = remember(encounters, newestIsNewSpot) { encounterPins(encounters, newestIsNewSpot) }
    val center = centerOf(pins)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (center != null) {
                PlatformMap(
                    modifier = Modifier.fillMaxSize(),
                    pins = pins,
                    zoom = 12.0,
                    centerLat = center.first,
                    centerLon = center.second,
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                Text("Where you've met", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// endregion

// region Your story

/** One page of "Your story"; only pages backed by real data are built. */
internal data class StoryPage(
    val eyebrow: String,
    val headline: String,
    val detail: String?,
)

internal fun storyPages(
    stats: FriendshipStats,
    peerFirstName: String,
): List<StoryPage> {
    val pages = mutableListOf<StoryPage>()
    stats.firstMet?.let { first ->
        val date = formatWeekdayDate(first.at.toEpochMilliseconds())
        pages +=
            StoryPage(
                "Where it started",
                date,
                first.placeName?.let { "You first Clicked at $it." } ?: "You first Clicked with $peerFirstName.",
            )
    }
    val spotsLine =
        buildString {
            append(plural(stats.spots.size, "spot"))
            if (stats.neighborhoods > 0) append(" across ${plural(stats.neighborhoods, "neighborhood")}")
            append(".")
        }
    pages += StoryPage(stats.level.name, plural(stats.hangouts, "hangout"), spotsLine)
    stats.topSpot?.let { spot ->
        val name = spot.name ?: return@let
        pages += StoryPage("Your spot", name, "${spot.visits} times and counting.")
    }
    if (stats.longestWeekStreak >= 2 || stats.favoriteTime != null) {
        pages +=
            StoryPage(
                "Your rhythm",
                stats.favoriteTime?.let { "You're ${it.label} people." } ?: "Week after week",
                stats.longestWeekStreak.takeIf { it >= 2 }?.let { "Longest run: $it weeks in a row." },
            )
    }
    val cold = stats.coldest?.temperatureCelsius
    val warm = stats.warmest?.temperatureCelsius
    if (cold != null && warm != null && warm - cold >= 5) {
        pages += StoryPage("Rain or shine", "${cold.roundToInt()}° to ${warm.roundToInt()}°", "You've shown up in every kind of weather.")
    }
    return pages
}

@Composable
private fun StoryCard(
    page: StoryPage,
    seed: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(24.dp))
                .background(SouvenirPalette.brush(seed))
                .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(page.eyebrow.uppercase(), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        Text(page.headline, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        page.detail?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A paged recap of one friendship, shareable card by card. */
@Composable
fun FriendshipStorySheet(
    peerFirstName: String,
    peerSeed: String,
    encounters: List<FriendshipEncounter>,
    onPlanNext: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val stats = remember(encounters) { FriendshipStats.compute(encounters, Clock.System.now()) }
    val pages = remember(stats, peerFirstName) { storyPages(stats, peerFirstName) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    ClickFormBottomSheet(onDismissRequest = onDismiss, fillBody = true) {
        ClickSheetChrome(title = "You & $peerFirstName", modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
                val page = pages[index]
                val cardModifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                StoryCard(
                    page = page,
                    seed = peerSeed + index,
                    modifier = if (index == pagerState.currentPage) cardModifier.recordInto(layer) else cardModifier,
                )
            }
            Text(
                "${pagerState.currentPage + 1} of ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClickSheetSecondaryButton(
                    text = "Share",
                    onClick = { scope.launch { shareLayerAsPng(layer, "click_story_${pagerState.currentPage + 1}.png") } },
                    modifier = Modifier.weight(1f),
                )
                if (onPlanNext != null) {
                    ClickSheetPrimaryButton(text = "Plan next", onClick = onPlanNext, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// endregion

// region souvenir

/** Post-tap souvenir: "First Click" / "Hangout #N", when and where, and what this one unlocked. */
@Composable
fun SouvenirCard(
    peerName: String,
    peerSeed: String,
    latest: FriendshipEncounter,
    highlights: HangoutHighlights,
    weatherCondition: String?,
    modifier: Modifier = Modifier,
) {
    val atMs = latest.at.toEpochMilliseconds()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SouvenirPalette.brush(peerSeed))
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (highlights.ordinal <= 1) "First Click" else "Hangout #${highlights.ordinal}",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("with $peerName", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        latest.placeName?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text("${formatWeekdayDate(atMs)} · ${formatClockTime(atMs)}", color = Color.White.copy(alpha = 0.9f))
        latest.temperatureCelsius?.let { temp ->
            Text(
                listOfNotNull("${temp.roundToInt()}°", weatherCondition?.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        val badges =
            listOfNotNull(
                highlights.leveledUpTo?.let { "Now ${it.name}" },
                "New spot".takeIf { highlights.isNewSpot },
                "${highlights.weekStreak}-week streak".takeIf { highlights.weekStreak >= 2 },
                "${ordinal(highlights.ordinal)} hangout".takeIf { highlights.isMilestone },
            )
        if (badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                badges.forEach { badge ->
                    Text(
                        badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier =
                            Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Souvenir card with a "Share souvenir" action (rendered to PNG). */
@Composable
fun ShareableSouvenir(
    peerName: String,
    peerSeed: String,
    latest: FriendshipEncounter,
    highlights: HangoutHighlights,
    weatherCondition: String?,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SouvenirCard(peerName, peerSeed, latest, highlights, weatherCondition, Modifier.recordInto(layer))
        TextButton(
            onClick = { scope.launch { shareLayerAsPng(layer, "click_souvenir.png") } },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Share souvenir") }
    }
}

// endregion

/** "Clicked Sep 12 at Café Allegro · 4 encounters" (iOS PeerProfileModel relationship line). */
fun relationshipLine(encounters: List<FriendshipEncounter>): String? {
    val first = encounters.minByOrNull { it.at } ?: return null
    val dt = first.at.toLocalDateTime(TimeZone.currentSystemDefault())
    val month =
        dt.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .take(3)
    val place = first.placeName?.let { " at $it" }.orEmpty()
    return "Clicked $month ${dt.dayOfMonth}$place · ${plural(encounters.size, "encounter")}"
}

/**
 * Group "Together" (iOS `GroupTogetherSection`): group hangouts (2+ members within two hours),
 * spots, who you see most, streak, the map and upcoming plans.
 */
@Composable
fun GroupTogetherSection(
    groupName: String,
    hangouts: List<GroupHangout>,
    memberName: (String) -> String,
    upcomingPlans: List<UpcomingPlan>,
    onPlan: (() -> Unit)?,
    onOpenPlan: (UpcomingPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { Clock.System.now() }
    val representatives = remember(hangouts) { hangouts.map { it.representative } }
    val stats = remember(representatives) { FriendshipStats.compute(representatives, now) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(clickCardSurface())
                .border(clickBorderWidth(), clickBorderColor(), shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Together", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (hangouts.isEmpty()) {
            Text(
                "When two or more of you Click together, it shows up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LevelHeader(stats)
            Text(
                "${plural(hangouts.size, "group hangout")} · ${plural(stats.spots.size, "spot")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val regulars =
                hangouts
                    .flatMap { it.memberIds }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { memberName(it.key) }
            if (regulars.isNotEmpty()) {
                Text(
                    "Most often with ${regulars.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats.weekStreak >= 2) {
                Text("🔥 ${stats.weekStreak} week streak", style = MaterialTheme.typography.bodySmall)
            }
            if (representatives.any { it.latitude != null }) {
                EncounterMapPreview(
                    encounters = representatives,
                    spotsCount = stats.spots.size,
                    newestIsNewSpot = false,
                    onClick = {},
                )
            }
        }
        if (upcomingPlans.isNotEmpty()) UpcomingPlansList(upcomingPlans, onOpenPlan)
        if (onPlan != null) {
            AssistChip(onClick = onPlan, label = { Text(if (upcomingPlans.isEmpty()) "Plan something" else "Plan another") })
        }
    }
}
