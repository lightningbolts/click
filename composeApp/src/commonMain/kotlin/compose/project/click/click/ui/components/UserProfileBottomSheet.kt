@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.ProvideSheetSwipeDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberSheetScrollAtTop // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileBottomSheet(
    userId: String?,
    /** Logged-in user; used to load the mutual `connections` row. */
    viewerUserId: String?,
    onDismiss: () -> Unit,
) {
    if (userId.isNullOrBlank()) return

    val scope = rememberCoroutineScope()
    val repository = remember { SupabaseRepository() }
    var profile by remember(userId) { mutableStateOf(repository.getCachedUserPublicProfile(userId)) }
    var loading by remember(userId) { mutableStateOf(profile == null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId, viewerUserId) {
        profile = repository.getCachedUserPublicProfile(userId)
        loading = profile == null
        error = null
        val result =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshUserPublicProfile(viewerUserId, userId)
                }
            }
        result.getOrNull()?.let { profile = it }
        if (profile == null) {
            error = result.exceptionOrNull()?.message
        }
        loading = false
    }

    val proximityEncounterEpoch by AppDataManager.proximityEncounterEpoch.collectAsState()
    LaunchedEffect(proximityEncounterEpoch, userId, viewerUserId) {
        if (proximityEncounterEpoch <= 0L || userId.isNullOrBlank()) return@LaunchedEffect
        val refreshed =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshUserPublicProfile(viewerUserId, userId)
                }
            }.getOrNull()
        if (refreshed != null) {
            profile = refreshed
            error = null
            loading = false
        }
    }

    fun dismiss() {
        onDismiss()
    }

    val profileScroll = rememberScrollState()
    val profileScrollAtTop = rememberSheetScrollAtTop(profileScroll)
    ClickFormBottomSheet(
        onDismissRequest = onDismiss,
        useUiKitScrollHost = true,
    ) {
        ProvideSheetSwipeDismiss(
            onDismissRequest = onDismiss,
            scrollAtTop = profileScrollAtTop,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(sheetPageBackground()),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .sheetBodyScroll(profileScroll)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .padding(bottom = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassSheetTokens.OnOled(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { dismiss() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = GlassSheetTokens.OnOled())
                        }
                    }

                    when {
                        loading -> {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp)
                                        .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue)
                            }
                        }
                        error != null && profile == null -> {
                            Text(
                                text = error ?: "Could not load profile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                            TextButton(onClick = { dismiss() }) { Text("Close", color = GlassSheetTokens.OnOled()) }
                        }
                        profile != null -> {
                            val p = profile!!
                            val u = p.user
                            val age = ageFromBirthdayIso(u.birthday)
                            val title =
                                buildString {
                                    append(u.name ?: "Member")
                                    if (age != null) append(", $age")
                                }
                            val mutual = sharedInterestTags(p.viewerInterestTags, p.interestTags)
                            val conn = p.sharedConnection
                            val capsuleLines =
                                if (conn != null) {
                                    listOfNotNull(
                                        conn.profileContextLine(),
                                        conn.profilePlaceLine(),
                                        conn.profileWhenLine(),
                                        conn.profileWeatherLine(),
                                        conn.profileNoiseLine(),
                                    ).map { it.trim() }.filter { it.isNotEmpty() }
                                } else {
                                    emptyList()
                                }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    usePrimaryBorder = true,
                                    contentPadding = 16.dp,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(88.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryBlue),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text =
                                                    u.name
                                                        ?.firstOrNull()
                                                        ?.toString()
                                                        ?.uppercase() ?: "?",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 32.sp,
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (!u.email.isNullOrBlank()) {
                                                Text(
                                                    text = u.email ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (mutual.isNotEmpty()) {
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = 14.dp,
                                    ) {
                                        Text(
                                            text = "Mutual interests",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            mutual.forEach { tag ->
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier =
                                                        Modifier
                                                            .clip(RoundedCornerShape(GlassSheetTokens.BentoInteriorCorner))
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                            ).padding(horizontal = 10.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (capsuleLines.isNotEmpty() && conn != null) {
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = 0.dp,
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            ChatAmbientMeshBackground(
                                                connection = conn,
                                                isHubNeutral = false,
                                                animateMesh = true,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = "Memory capsule",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                capsuleLines.take(6).forEach { line ->
                                                    Text(
                                                        text = line,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                ProfileLegacyTimelineContent(
                                    profile = p,
                                    loading = false,
                                    error = null,
                                )
                            }
                        }
                        else -> {
                            Text("Profile unavailable", color = GlassSheetTokens.OnOledMuted())
                        }
                    }
                }
            }
        }
    }
}
