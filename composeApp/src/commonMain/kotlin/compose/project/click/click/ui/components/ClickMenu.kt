@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.clickBorderStroke // pragma: allowlist secret

data class ClickMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
)

/**
 * Shared overflow / context menu. Use this instead of one-off [DropdownMenu] chrome.
 */
@Composable
fun ClickDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ClickMenuItem>,
    modifier: Modifier = Modifier,
) {
    val onMenu = MaterialTheme.colorScheme.onSurface
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 180.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = clickBorderStroke(),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier,
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
            val color = if (item.destructive) MaterialTheme.colorScheme.error else onMenu
            DropdownMenuItem(
                text = {
                    Text(
                        item.label,
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                onClick = {
                    onDismissRequest()
                    item.onClick()
                },
                leadingIcon =
                    item.icon?.let { icon ->
                        {
                            Icon(icon, contentDescription = null, tint = color)
                        }
                    },
                colors =
                    MenuDefaults.itemColors(
                        textColor = color,
                        leadingIconColor = color,
                    ),
            )
        }
    }
}
