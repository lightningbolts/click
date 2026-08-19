@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.contacts.ContactDiscoveryHelper // pragma: allowlist secret
import compose.project.click.click.data.contacts.KnownSinceBucket // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassCard // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.ProvideSheetSwipeDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberSheetScrollAtTop // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.plus

/**
 * Phase 2 — C13 regression fix: legacy profile content (moment cards, interests, shared
 * interests, availability intents, "Our timeline") lifted out of [UserProfileBottomSheet]
 * so the same rendering is shared with the Timeline subtab of [ProfileBottomSheet].
 *
 * This composable only renders **below-header** sections; the enclosing sheet provides
 * its own avatar / name / subtitle row. When [profile] is `null` we show a compact
 * loading state so the Timeline subtab is never empty while fetch is in flight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileLegacyTimelineContent(
    profile: UserPublicProfile?,
    loading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    if (loading && profile == null) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }
    if (profile == null) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = error ?: "Profile unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val p = profile
        val conn = p.sharedConnection
        val isPrior = conn?.isPriorConnection() == true
        val viewerId =
            AppDataManager.currentUser
                .collectAsState()
                .value
                ?.id
        val scope = rememberCoroutineScope()
        var priorBusy by remember { mutableStateOf(false) }
        var priorError by remember { mutableStateOf<String?>(null) }
        val canRespondPrior =
            isPrior &&
                conn?.isPending() == true &&
                !viewerId.isNullOrBlank() &&
                (conn.responderId == viewerId || (conn.initiatorId != viewerId && viewerId in conn.user_ids))
        val hasMoment =
            conn != null &&
                listOfNotNull(
                    conn.profileContextLine(),
                    conn.profilePlaceLine(),
                    conn.profileAddressDetailLine(),
                    conn.profileWhenLine(),
                    conn.profileWeatherLine(),
                    conn.profileNoiseLine(),
                    conn.profileBarometricLine(),
                ).isNotEmpty()

        if (canRespondPrior && conn != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClickButton(
                    onClick = {
                        scope.launch {
                            priorBusy = true
                            priorError = null
                            ApiClient().respondPriorConnection(conn.id, "accept").fold(
                                onSuccess = { AppDataManager.refresh(force = true) },
                                onFailure = { priorError = it.message },
                            )
                            priorBusy = false
                        }
                    },
                    enabled = !priorBusy,
                    variant = ClickButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                ) { Text("Accept") }
                ClickButton(
                    onClick = {
                        scope.launch {
                            priorBusy = true
                            priorError = null
                            ApiClient().respondPriorConnection(conn.id, "decline").fold(
                                onSuccess = { AppDataManager.refresh(force = true) },
                                onFailure = { priorError = it.message },
                            )
                            priorBusy = false
                        }
                    },
                    enabled = !priorBusy,
                    variant = ClickButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                ) { Text("Decline") }
            }
            if (priorError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(priorError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isPrior && conn != null) {
            Text(
                text = "How you know them",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            val body = MaterialTheme.colorScheme.onSurface
            val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegacyMomentCard(
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = Color(0xFFF59E0B),
                    label = "Source",
                    value =
                        if (conn.connection_method == "prior" || conn.connection_method == "contacts") {
                            "Added from Contacts"
                        } else {
                            "Added by Search"
                        },
                    cardBorder = cardBorder,
                    cardBg = cardBg,
                    muted = muted,
                    body = body,
                )
                LegacyMomentCard(
                    icon = Icons.Outlined.Schedule,
                    iconTint = Color(0xFFF59E0B),
                    label = "Status",
                    value = if (conn.isPriorConfirmedByBoth()) "Confirmed by both" else "Waiting for confirmation",
                    cardBorder = cardBorder,
                    cardBg = cardBg,
                    muted = muted,
                    body = body,
                )
                LegacyMomentCard(
                    icon = Icons.Outlined.Schedule,
                    iconTint = Color(0xFFF59E0B),
                    label = "Known since",
                    value = KnownSinceBucket.fromApi(conn.knownSince).label,
                    cardBorder = cardBorder,
                    cardBg = cardBg,
                    muted = muted,
                    body = body,
                )
                conn.priorContextTag?.trim()?.takeIf { it.isNotEmpty() }?.let { tag ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
        } else if (hasMoment && conn != null) {
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

                conn.profileContextLine()?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Filled.AutoAwesome,
                        iconTint = PrimaryBlue.copy(alpha = 0.9f),
                        label = "Moment",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
                    )
                }
                val placeLine =
                    listOfNotNull(
                        conn.profilePlaceLine(),
                        conn.profileAddressDetailLine(),
                    ).joinToString(" · ").takeIf { it.isNotEmpty() }
                placeLine?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Outlined.LocationOn,
                        iconTint = LightBlue.copy(alpha = 0.95f),
                        label = "Place",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
                    )
                }
                conn.profileWhenLine()?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Outlined.Schedule,
                        iconTint = Color(0xFFFFCC80).copy(alpha = 0.95f),
                        label = "Time",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
                    )
                }
                conn.profileWeatherLine()?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Outlined.Cloud,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        label = "Weather",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
                    )
                }
                conn.profileNoiseLine()?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Outlined.GraphicEq,
                        iconTint = Color(0xFF69F0AE).copy(alpha = 0.9f),
                        label = "Ambience",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
                    )
                }
                conn.profileBarometricLine()?.let { line ->
                    LegacyMomentCard(
                        icon = Icons.Outlined.Terrain,
                        iconTint = LightBlue.copy(alpha = 0.95f),
                        label = "Elevation",
                        value = line,
                        cardBorder = cardBorder,
                        cardBg = cardBg,
                        muted = muted,
                        body = body,
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (p.interestTags.isEmpty()) {
            Text(
                text = "No interests shared yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                p.interestTags.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Personality",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val personality = p.personalityTags.ifEmpty { p.user.personalityTags }
        if (personality.isEmpty()) {
            Text(
                text = "No personality traits shared yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                personality.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (sharedTags.isEmpty()) {
            Text(
                text = "No overlap with your interests yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sharedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val intentBubbles = p.profileAvailabilityIntents.filter { it.displayLabel().isNotEmpty() }
        if (intentBubbles.isEmpty()) {
            Text(
                text = "No active availability intents",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        },
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
            OurTimelineSection(
                encounters = conn.connectionEncounters,
                emptyCopy =
                    if (isPrior) {
                        ContactDiscoveryHelper.TIMELINE_EMPTY_COPY
                    } else {
                        "No crossing history on file yet."
                    },
            )
        }
    }
}

@Composable
internal fun LegacyMomentCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    cardBorder: Color,
    cardBg: Color,
    muted: Color,
    body: Color,
) {
    Row(
        modifier =
            Modifier
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
