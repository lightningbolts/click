@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret

/** Floating ↓ button with a count of incoming messages that arrived while scrolled up. */
@Composable
internal fun JumpToLatestButton(
    unseenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        SmallFloatingActionButton(onClick = onClick, containerColor = clickCardSurface()) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription =
                    if (unseenCount >
                        0
                    ) {
                        "$unseenCount new messages, jump to latest"
                    } else {
                        "Jump to latest"
                    },
            )
        }
        if (unseenCount > 0) {
            Text(
                text = if (unseenCount > 99) "99+" else unseenCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(PrimaryBlue, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** In-conversation search: query, "n of m", step older/newer, close. */
@Composable
internal fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    position: Int,
    total: Int,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(clickCardSurface(), RoundedCornerShape(14.dp))
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search this chat") },
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            modifier = Modifier.weight(1f),
        )
        if (query.trim().length >= 2) {
            Text(
                if (total == 0) "No results" else "$position of $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOlder, enabled = position < total) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Older match")
        }
        IconButton(onClick = onNewer, enabled = position > 1) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Newer match")
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
    }
}

/**
 * "Develop all (n)": one quiet action when several Click Drops are ready in this chat, never a
 * badge. Mirrors the iOS `ChatView` overlay.
 */
@Composable
internal fun DevelopAllDropsButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = clickCardSurface(),
        shadowElevation = 3.dp,
        modifier =
            modifier.semantics {
                onClick(label = "Develops every Click Drop that's ready in this chat") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
