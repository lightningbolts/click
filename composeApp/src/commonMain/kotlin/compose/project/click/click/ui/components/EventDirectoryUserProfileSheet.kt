@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.deeplink.AppDeepLink // pragma: allowlist secret
import compose.project.click.click.deeplink.AppDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.events.AttendeeRelationship // pragma: allowlist secret
import compose.project.click.click.events.DirectoryAttendee // pragma: allowlist secret
import compose.project.click.click.events.allowsDirectoryConnectActions // pragma: allowlist secret
import compose.project.click.click.events.relationshipSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight profile sheet opened from the event people directory.
 * Mirrors the connections profile header + interests timeline, without chat-derived
 * tabs (Media / Links / Files / Beacons) that require an existing connection thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDirectoryUserProfileSheet(
    attendee: DirectoryAttendee,
    viewerUserId: String?,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null,
) {
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val cached: User? = connectedUsers[attendee.userId]
    var resolved by remember(attendee.userId) {
        mutableStateOf<User?>(
            cached ?: User(
                id = attendee.userId,
                name = attendee.name,
                image = attendee.avatarUrl,
            ),
        )
    }
    var legacyProfile by remember(attendee.userId) { mutableStateOf<UserPublicProfile?>(null) }
    val notConnected =
        attendee.relationship != AttendeeRelationship.Connection && attendee.relationship != AttendeeRelationship.Self
    // Aura ring for people the viewer hasn't Clicked with (iOS `PublicProfileView`).
    var auraColors by remember(attendee.userId) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(attendee.userId, notConnected) {
        if (!notConnected) return@LaunchedEffect
        auraColors =
            ApiClient()
                .getPublicProfileUnauthenticated(attendee.userId)
                .getOrNull()
                ?.auraColors
                .orEmpty()
                .mapNotNull(::parseAuraHex)
    }
    var legacyLoading by remember(attendee.userId) { mutableStateOf(true) }
    var legacyError by remember(attendee.userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(attendee.userId, viewerUserId, cached) {
        cached?.let { resolved = it }
        legacyLoading = true
        legacyError = null
        val profile =
            runCatching {
                withContext(Dispatchers.Default) {
                    SupabaseRepository().fetchUserPublicProfile(viewerUserId, attendee.userId)
                }
            }.getOrElse {
                legacyError = it.message
                null
            }
        legacyProfile = profile
        profile?.user?.let { resolved = it }
        legacyLoading = false
    }

    val displayName =
        resolved?.name?.takeIf { it.isNotBlank() }
            ?: attendee.name.takeIf { it.isNotBlank() }
            ?: "Attendee"
    val canMessage = onMessage != null && allowsDirectoryConnectActions(attendee.relationship)
    val subtitle =
        relationshipSubtitle(attendee)
            ?: when (attendee.relationship) {
                AttendeeRelationship.Self -> "You"
                AttendeeRelationship.Connection -> "Connection"
                AttendeeRelationship.Mutual -> "Mutual"
                else -> "At this event"
            }
    val border = clickBorderColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    ClickFormBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    if (notConnected) {
                        val ring = auraColors.ifEmpty { listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary) }
                        Box(
                            modifier =
                                Modifier
                                    .size(80.dp)
                                    .alpha(0.55f)
                                    .background(Brush.linearGradient(if (ring.size == 1) ring + ring else ring), CircleShape),
                        )
                    }
                    ConnectionListUserAvatarFace(
                        displayName = displayName,
                        email = resolved?.email,
                        avatarUrl = resolved?.image ?: attendee.avatarUrl,
                        userId = attendee.userId,
                        modifier =
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(clickBorderWidth(), border, CircleShape),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onVariant,
                    )
                    if (notConnected) {
                        Text(
                            text = "You haven't Clicked yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = onVariant,
                        )
                    }
                }
            }

            if (canMessage) {
                Button(
                    onClick = {
                        onMessage?.invoke()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(clickBorderWidth(), border),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text("Message", fontWeight = FontWeight.SemiBold)
                }
            } else if (notConnected) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Connect in person", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Click connects people who've actually met. When you're together, open Add Click and tap " +
                            "phones, or scan each other's QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = onVariant,
                    )
                    OutlinedButton(onClick = {
                        onDismiss()
                        AppDeepLinkRouter.open(AppDeepLink.AddClick)
                    }) { Text("Open Add Click") }
                }
            }

            if (attendee.sharedInterests.isNotEmpty()) {
                Text(
                    text = "Shared interests",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                )
                Text(
                    text = attendee.sharedInterests.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            // Interests (+ connection moment when they are already a connection).
            ProfileLegacyTimelineContent(
                profile = legacyProfile,
                loading = legacyLoading,
                error = legacyError,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "#RRGGBB" / "RRGGBB" / "#AARRGGBB" → [Color]; null for anything else. */
internal fun parseAuraHex(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}
