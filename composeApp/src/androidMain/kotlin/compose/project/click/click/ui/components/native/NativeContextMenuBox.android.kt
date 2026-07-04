package compose.project.click.click.ui.components.native

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import compose.project.click.click.PlatformHapticsPolicy

@Composable
actual fun NativeContextMenuBox(
    items: List<NativeContextMenuItem>,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        content()
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1f)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            PlatformHapticsPolicy.lightImpact()
                            expanded = true
                        },
                    ),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        if (item.enabled) {
                            PlatformHapticsPolicy.lightImpact()
                            item.onClick()
                        }
                    },
                )
            }
        }
    }
}
