package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.data.models.UserPublicProfile
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

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

    val style = LocalPlatformStyle.current
    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = if (style.isIOS) Color.Transparent else BottomSheetDefaults.ContainerColor,
        dragHandle = if (style.isIOS) null else { { BottomSheetDefaults.DragHandle() } },
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
                            color = Color.White,
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
                        conn.profileGeoHintLine(),
                        conn.profileWhenLine(),
                        conn.profileWeatherLine(),
                        conn.profileNoiseLine(),
                    ).isNotEmpty()

                    if (hasMoment && conn != null) {
                        Text(
                            text = "When you connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            tonalElevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val placeBits = listOfNotNull(
                                    conn.profileContextLine(),
                                    conn.profilePlaceLine(),
                                    conn.profileAddressDetailLine(),
                                    conn.profileGeoHintLine(),
                                )
                                if (placeBits.isNotEmpty()) {
                                    MomentRow(
                                        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        label = "Place",
                                        lines = placeBits,
                                    )
                                }
                                conn.profileWhenLine()?.let { whenLine ->
                                    MomentRow(
                                        icon = { Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        label = "Time",
                                        lines = listOf(whenLine),
                                    )
                                }
                                conn.profileWeatherLine()?.let { w ->
                                    MomentRow(
                                        icon = { Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                        label = "Weather",
                                        lines = listOf(w),
                                    )
                                }
                                conn.profileNoiseLine()?.let { n ->
                                    MomentRow(
                                        icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                                        label = "Ambience",
                                        lines = listOf(n),
                                    )
                                }
                            }
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

                    Text(
                        text = "Availability",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val av = p.availability
                    if (av == null) {
                        Text(
                            text = "No availability shared",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (av.isFreeThisWeek) "Free this week" else "Not marked free this week",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (av.availableDays.isNotEmpty()) {
                            Text(
                                text = "Days: ${av.availableDays.joinToString { d -> d.replaceFirstChar { ch -> ch.titlecase() } }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                        }
                        if (av.preferredActivities.isNotEmpty()) {
                            Text(
                                text = "Activities: ${av.preferredActivities.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                        }
                        if (!av.customStatus.isNullOrBlank()) {
                            Text(
                                text = av.customStatus ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }
                }
                else -> {
                    Text("Profile unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MomentRow(
    icon: @Composable () -> Unit,
    label: String,
    lines: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
