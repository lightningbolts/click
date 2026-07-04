package compose.project.click.click.ui.components.native

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.LiquidGlassPill
import compose.project.click.click.ui.theme.LocalPlatformStyle

@Composable
actual fun NativeContextMenuChip(
    label: String,
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
) {
    val style = LocalPlatformStyle.current
    val glassStrength = if (style.isIOS) 0.64f else 0.4f

    NativeContextMenuBox(
        items = items,
        modifier = modifier,
        enabled = enabled,
    ) {
        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 48.dp),
            cornerRadiusDp = 20,
            backgroundStrength = glassStrength,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
