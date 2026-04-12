package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

private fun sharedInterestTags(viewer: List<String>, other: List<String>): List<String> {
    if (viewer.isEmpty() || other.isEmpty()) return emptyList()
    val viewerNorm = viewer.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    return other.map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() in viewerNorm }.distinct()
}

private fun ProfileAvailabilityIntentBubble.displayLabel(): String {
    val tag = intentTag?.trim().orEmpty()
    val tf = timeframe?.trim().orEmpty()
    return when {
        tag.isNotEmpty() && tf.isNotEmpty() -> "$tag · $tf"
        tag.isNotEmpty() -> tag
        tf.isNotEmpty() -> tf
        else -> ""
    }.trim()
}

private fun ProfileAvailabilityIntentBubble.activeUntilShort(): String {
    val iso = expiresAt ?: return ""
    val instant = runCatching { kotlinx.datetime.Instant.parse(iso) }.getOrNull() ?: return ""
    val tz = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(tz)
    val today = Clock.System.todayIn(tz)
    val d = local.date
    fun pad(n: Int) = n.toString().padStart(2, '0')
    val timePart = "${pad(local.hour)}:${pad(local.minute)}"
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    return when {
        d == today -> "Today · $timePart"
        d == tomorrow -> "Tomorrow · $timePart"
        else -> "${d.monthNumber}/${d.dayOfMonth} · $timePart"
    }
}

private fun ageFromBirthdayIso(birthday: String?): Int? {
    if (birthday.isNullOrBlank()) return null
    return try {
        val d = LocalDate.parse(birthday.take(10))
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var age = today.year - d.year
        if (today.monthNumber < d.monthNumber ||
            (today.monthNumber == d.monthNumber && today.dayOfMonth < d.dayOfMonth)
        ) {
            age--
        }
        age.takeIf { it in 0..120 }
    } catch (_: Exception) {
        null
    }
}

private fun ConnectionEncounter.metricNoiseLabel(): String? {
    val parts = mutableListOf<String>()
    noiseLevel?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val friendly = runCatching { NoiseLevelCategory.valueOf(raw.uppercase().replace(' ', '_')) }
            .getOrNull()?.let { formatNoiseCategoryForTimeline(it) }
            ?: raw.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        parts.add(friendly)
    }
    exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { parts.add("${it.roundToInt()} dB") }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun formatNoiseCategoryForTimeline(cat: NoiseLevelCategory): String = when (cat) {
    NoiseLevelCategory.VERY_QUIET -> "Very quiet"
    NoiseLevelCategory.QUIET -> "Quiet"
    NoiseLevelCategory.MODERATE -> "Moderate"
    NoiseLevelCategory.LOUD -> "Loud"
    NoiseLevelCategory.VERY_LOUD -> "Very loud"
}

private fun ConnectionEncounter.metricElevationLabel(): String? {
    val parts = mutableListOf<String>()
    elevationCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        val friendly = runCatching { HeightCategory.valueOf(raw.uppercase().replace(' ', '_')) }
            .getOrNull()?.let { hc ->
                when (hc) {
                    HeightCategory.BELOW_GROUND -> "Below ground"
                    HeightCategory.GROUND_LEVEL -> "Ground level"
                    HeightCategory.ELEVATED -> "Elevated"
                    HeightCategory.HIGH_RISE -> "High rise"
                }
            } ?: raw.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        parts.add(friendly)
    }
    exactBarometricElevationM?.takeIf { it.isFinite() }?.let { parts.add("${it.roundToInt()} m") }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun ConnectionEncounter.metricWindLabel(): String? {
    val ws = weatherSnapshot ?: return null
    val kph = ws.windSpeedKph ?: return null
    if (!kph.isFinite()) return null
    val deg = ws.windDirectionDegrees
    val suffix = deg?.takeIf { it in 0..359 }?.let { d ->
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val x = ((d % 360) + 360) % 360
        val idx = (kotlin.math.floor((x + 22.5) / 45.0).toInt() % 8 + 8) % 8
        " ${dirs[idx]}"
    } ?: ""
    return "${kph.roundToInt()} km/h$suffix"
}

private fun ConnectionEncounter.metricPressureLabel(): String? =
    weatherSnapshot?.pressureMslHpa?.takeIf { it.isFinite() }?.let { "${it.roundToInt()} hPa" }

private fun ConnectionEncounter.metricConditionLabel(): String? =
    weatherSnapshot?.condition?.trim()?.takeIf { it.isNotEmpty() }
        ?: weatherSnapshot?.iconCode?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.titlecase() }

private fun ConnectionEncounter.metricTemperatureLabel(): String? {
    val c = weatherSnapshot?.temperatureCelsius ?: return null
    if (!c.isFinite()) return null
    val f = (c * 9.0 / 5.0) + 32.0
    if (!f.isFinite()) return null
    return "${f.roundToInt()}°F (${c.roundToInt()}°C)"
}

@Composable
private fun TimelineMetricPill(
    icon: ImageVector,
    iconTint: Color,
    text: String,
    cardBorder: Color,
    cardBg: Color,
    body: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, cardBorder, RoundedCornerShape(50))
            .background(cardBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OurTimelineSection(encounters: List<ConnectionEncounter>) {
    if (encounters.isEmpty()) {
        Text(
            text = "No crossing history on file yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val sorted = encounters.sortedWith(
        compareByDescending<ConnectionEncounter> { it.encounteredAt }
            .thenByDescending { it.id },
    )
    val oldestId = sorted.lastOrNull()?.id
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val dotBorder = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 2.dp.toPx()
                val x = 16.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                )
            },
    ) {
            sorted.forEach { enc ->
                val isOldest = enc.id == oldestId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .border(2.dp, dotBorder, CircleShape)
                                .background(PrimaryBlue),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (isOldest) {
                            Text(
                                text = "Where it started",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = LightBlue.copy(alpha = 0.95f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            text = formatEncounterTimelineWhenLine(enc.encounteredAt)
                                ?: enc.encounteredAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                        Text(
                            text = enc.displayLocation?.trim()?.takeIf { it.isNotEmpty() }
                                ?: enc.locationName?.trim()?.takeIf { it.isNotEmpty() }
                                ?: "Unknown place",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = body,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val pills = buildList {
                            enc.metricConditionLabel()?.let { add(Triple(Icons.Outlined.Cloud, Color(0xFFB0BEC5), it)) }
                            enc.metricTemperatureLabel()?.let { add(Triple(Icons.Outlined.Thermostat, Color(0xFFFFCC80), it)) }
                            enc.metricWindLabel()?.let { add(Triple(Icons.Outlined.Air, Color(0xFF81D4FA), it)) }
                            enc.metricPressureLabel()?.let { add(Triple(Icons.Outlined.Speed, Color(0xFFCE93D8), it)) }
                            enc.metricNoiseLabel()?.let { add(Triple(Icons.Outlined.GraphicEq, Color(0xFF69F0AE), it)) }
                            enc.metricElevationLabel()?.let { add(Triple(Icons.Outlined.Terrain, LightBlue.copy(alpha = 0.95f), it)) }
                            enc.metricLuxLabel()?.let { lbl ->
                                val dim = enc.luxLevel != null && enc.luxLevel!! < 15.0
                                val ic = if (dim) Icons.Outlined.NightsStay else Icons.Outlined.WbSunny
                                val tint = if (dim) Color(0xFF90CAF9) else Color(0xFFFFE082)
                                add(Triple(ic, tint, lbl))
                            }
                            enc.metricBatteryLabel()?.let { add(Triple(Icons.Outlined.BatteryStd, Color(0xFFA5D6A7), it)) }
                            enc.metricCompassAzimuthLabel()?.let { add(Triple(Icons.Outlined.Explore, Color(0xFFB39DDB), it)) }
                            enc.metricMotionVarianceLabel()?.let { add(Triple(Icons.Outlined.DirectionsRun, Color(0xFFFFAB91), it)) }
                        }
                        if (pills.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                pills.forEach { (ic, tint, lbl) ->
                                    TimelineMetricPill(ic, tint, lbl, cardBorder, cardBg, body)
                                }
                            }
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileBottomSheet(
    userId: String?,
    /** Logged-in user; used to load the mutual `connections` row. */
    viewerUserId: String?,
    onDismiss: () -> Unit,
) {
    if (userId.isNullOrBlank()) return

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseRepository() }
    var profile by remember(userId) { mutableStateOf<UserPublicProfile?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId, viewerUserId) {
        loading = true
        error = null
        profile = null
        val result = runCatching {
            withContext(Dispatchers.Default) {
                repository.fetchUserPublicProfile(viewerUserId, userId)
            }
        }
        profile = result.getOrNull()
        error = result.exceptionOrNull()?.message
        loading = false
    }

    fun dismiss() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = sheetBg,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        // Fill sheet height so the home-indicator / safe-area gap is not UIKit white in dark mode.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(sheetBg)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
                error != null && profile == null -> {
                    Text(
                        text = error ?: "Could not load profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    TextButton(onClick = { dismiss() }) { Text("Close") }
                }
                profile != null -> {
                    val p = profile!!
                    val u = p.user
                    val age = ageFromBirthdayIso(u.birthday)
                    val title = buildString {
                        append(u.name ?: "Member")
                        if (age != null) append(", $age")
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = u.name?.firstOrNull()?.toString()?.uppercase() ?: "?",
                            color = LightBlue.copy(alpha = 0.96f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!u.email.isNullOrBlank()) {
                        Text(
                            text = u.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val conn = p.sharedConnection
                    val hasMoment = conn != null && listOfNotNull(
                        conn.profileContextLine(),
                        conn.profilePlaceLine(),
                        conn.profileAddressDetailLine(),
                        conn.profileWhenLine(),
                        conn.profileWeatherLine(),
                        conn.profileNoiseLine(),
                        conn.profileBarometricLine(),
                    ).isNotEmpty()

                    if (hasMoment && conn != null) {
                        Text(
                            text = "When you connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val muted = MaterialTheme.colorScheme.onSurfaceVariant
                            val body = MaterialTheme.colorScheme.onSurface
                            val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                            val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

                            @Composable
                            fun MomentCard(
                                icon: ImageVector,
                                iconTint: Color,
                                label: String,
                                value: String,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = muted,
                                            letterSpacing = 0.4.sp,
                                        )
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = body,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }

                            conn.profileContextLine()?.let { line ->
                                MomentCard(
                                    icon = Icons.Filled.AutoAwesome,
                                    iconTint = PrimaryBlue.copy(alpha = 0.9f),
                                    label = "Moment",
                                    value = line,
                                )
                            }
                            val placeLine = listOfNotNull(
                                conn.profilePlaceLine(),
                                conn.profileAddressDetailLine(),
                            ).joinToString(" · ").takeIf { it.isNotEmpty() }
                            placeLine?.let { line ->
                                MomentCard(
                                    icon = Icons.Outlined.LocationOn,
                                    iconTint = LightBlue.copy(alpha = 0.95f),
                                    label = "Place",
                                    value = line,
                                )
                            }
                            conn.profileWhenLine()?.let { line ->
                                MomentCard(
                                    icon = Icons.Outlined.Schedule,
                                    iconTint = Color(0xFFFFCC80).copy(alpha = 0.95f),
                                    label = "Time",
                                    value = line,
                                )
                            }
                            conn.profileWeatherLine()?.let { line ->
                                MomentCard(
                                    icon = Icons.Outlined.Cloud,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                    label = "Weather",
                                    value = line,
                                )
                            }
                            conn.profileNoiseLine()?.let { line ->
                                MomentCard(
                                    icon = Icons.Outlined.GraphicEq,
                                    iconTint = Color(0xFF69F0AE).copy(alpha = 0.9f),
                                    label = "Ambience",
                                    value = line,
                                )
                            }
                            conn.profileBarometricLine()?.let { line ->
                                MomentCard(
                                    icon = Icons.Outlined.Terrain,
                                    iconTint = LightBlue.copy(alpha = 0.95f),
                                    label = "Elevation",
                                    value = line,
                                )
                            }
                            ConnectionHardwareVibeBadgesRow(
                                encounter = conn.originEncounter,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "Interests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (p.interestTags.isEmpty()) {
                        Text(
                            text = "No interests shared yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.interestTags.forEach { tag ->
                                FilterChip(
                                    selected = false,
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val sharedTags = sharedInterestTags(p.viewerInterestTags, p.interestTags)

                    Text(
                        text = "Shared interests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (sharedTags.isEmpty()) {
                        Text(
                            text = "No overlap with your interests yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sharedTags.forEach { tag ->
                                FilterChip(
                                    selected = true,
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Availability intents",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val intentBubbles = p.profileAvailabilityIntents.filter { it.displayLabel().isNotEmpty() }
                    if (intentBubbles.isEmpty()) {
                        Text(
                            text = "No active availability intents",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intentBubbles.forEach { bubble ->
                                val until = bubble.activeUntilShort()
                                FilterChip(
                                    selected = false,
                                    onClick = { },
                                    label = {
                                        Column {
                                            Text(
                                                bubble.displayLabel(),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (until.isNotEmpty()) {
                                                Text(
                                                    until,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (conn != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Our timeline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Every time and place you’ve crossed paths",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OurTimelineSection(conn.connectionEncounters)
                    }
                }
                else -> {
                    Text("Profile unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
    }
}
