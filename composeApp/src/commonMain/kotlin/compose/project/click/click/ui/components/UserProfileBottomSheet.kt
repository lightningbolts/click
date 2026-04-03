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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.data.models.UserPublicProfile
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.ui.theme.LightBlue
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

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
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
                        conn.profileWhenLine(),
                        conn.profileWeatherLine(),
                        conn.profileNoiseLine(),
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
