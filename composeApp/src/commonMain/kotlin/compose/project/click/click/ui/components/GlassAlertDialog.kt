package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Centered OLED dialog replacing rigid Material [androidx.compose.material3.AlertDialog] chrome.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(shape)
                .border(1.dp, GlassSheetTokens.GlassBorder, shape)
                .background(GlassSheetTokens.OledBlack)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(color = GlassSheetTokens.OnOled),
            ) {
                title()
            }
            if (text != null) {
                androidx.compose.material3.ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(color = GlassSheetTokens.OnOledMuted),
                ) {
                    text()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissButton != null) {
                    dismissButton()
                }
                confirmButton()
            }
        }
    }
}

/** TextButton tuned for OLED glass dialogs. */
@Composable
fun GlassDialogTextButton(
    label: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick) {
        Text(label, color = contentColor)
    }
}
