package compose.project.click.click.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import compose.project.click.click.ui.theme.DeepBlue
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Phase 2 — C13: shared profile bottom sheet displayed when a map pin is tapped.
 *
 * Four subtabs: **Timeline · Media · Links · Files**. The sheet is deliberately
 * rendering-only — data fetching and persistence are the caller's job. This keeps
 * the component trivially previewable and lets MapScreen + ConnectionsScreen reuse
 * the same sheet. Empty tabs render a polite empty state rather than a blank panel.
 */
@Composable
fun ProfileBottomSheet(
    state: ProfileSheetState,
    onMessage: () -> Unit,
    onNudge: () -> Unit,
    onOpenLink: (String) -> Unit = {},
    onDownloadFile: (ProfileSheetFile) -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(ProfileSheetTab.Timeline) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ProfileSheetHeader(
            displayName = state.displayName,
            subtitle = state.subtitle,
            avatarUrl = state.avatarUrl,
            statusBadge = state.statusBadge,
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onMessage,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Message, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Message", fontWeight = FontWeight.SemiBold)
            }
            if (state.canNudge) {
                OutlinedButton(
                    onClick = onNudge,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nudge", fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        ProfileTabRow(selected = selectedTab, onSelect = { selectedTab = it })
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                ProfileSheetTab.Timeline -> TimelinePanel(items = state.timeline)
                ProfileSheetTab.Media -> MediaPanel(items = state.media)
                ProfileSheetTab.Links -> LinksPanel(items = state.links, onOpen = onOpenLink)
                ProfileSheetTab.Files -> FilesPanel(items = state.files, onDownload = onDownloadFile)
            }
        }
    }
}

/** Immutable snapshot the sheet renders. Callers rebuild this when underlying data changes. */
data class ProfileSheetState(
    val displayName: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val statusBadge: ProfileSheetBadge? = null,
    val canNudge: Boolean = true,
    val timeline: List<ProfileSheetTimelineItem> = emptyList(),
    val media: List<ProfileSheetMedia> = emptyList(),
    val links: List<ProfileSheetLink> = emptyList(),
    val files: List<ProfileSheetFile> = emptyList(),
)

data class ProfileSheetBadge(
    val label: String,
    /** 0xAARRGGBB packed color — rendered via [Color]`(value)`. */
    val tint: Color,
)

data class ProfileSheetTimelineItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val timestamp: String,
)

data class ProfileSheetMedia(
    val id: String,
    val imageUrl: String,
    val captionedAt: String? = null,
)

data class ProfileSheetLink(
    val id: String,
    val url: String,
    val title: String?,
    val timestamp: String,
)

data class ProfileSheetFile(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val timestamp: String,
)

enum class ProfileSheetTab(val label: String, val icon: ImageVector) {
    Timeline("Timeline", Icons.Outlined.History),
    Media("Media", Icons.Outlined.Image),
    Links("Links", Icons.Outlined.Link),
    Files("Files", Icons.Outlined.AttachFile),
}

@Composable
private fun ProfileSheetHeader(
    displayName: String,
    subtitle: String?,
    avatarUrl: String?,
    statusBadge: ProfileSheetBadge?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(LightBlue, PrimaryBlue))),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(CircleShape),
                )
            } else {
                Text(
                    displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (statusBadge != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBadge.tint.copy(alpha = 0.14f),
                ) {
                    Text(
                        statusBadge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusBadge.tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTabRow(
    selected: ProfileSheetTab,
    onSelect: (ProfileSheetTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ProfileSheetTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.55f,
                animationSpec = tween(180),
                label = "tab_tint_${tab.name}",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(20.dp),
                    tint = (if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = tint),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = (if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = tint),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (isSelected) 26.dp else 0.dp)
                        .background(PrimaryBlue, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun TimelinePanel(items: List<ProfileSheetTimelineItem>) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.History,
            title = "No timeline yet",
            body = "Shared moments will show up here as you connect.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { TimelineRow(item = it) }
    }
}

@Composable
private fun TimelineRow(item: ProfileSheetTimelineItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimaryBlue)
                .padding(top = 6.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun MediaPanel(items: List<ProfileSheetMedia>) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.Image,
            title = "No shared photos",
            body = "Photos you exchange in chat will appear here.",
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { media ->
            AsyncImage(
                model = media.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun LinksPanel(items: List<ProfileSheetLink>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.Link,
            title = "No shared links",
            body = "URLs shared in chat show up here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onOpen(link.url) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = PrimaryBlue)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        link.title ?: link.url,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(items: List<ProfileSheetFile>, onDownload: (ProfileSheetFile) -> Unit) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.AttachFile,
            title = "No shared files",
            body = "Attachments sent in chat will appear here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onDownload(file) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = PrimaryBlue,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatFileSize(file.sizeBytes)} · ${file.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        file.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024L * 1_024 -> "${bytes / 1_024} KB"
    else -> "${(bytes * 10 / (1_024L * 1_024)) / 10.0} MB"
}
