package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ConnectionArchiveNotice // pragma: allowlist secret

@Composable
fun ConnectionArchiveWarningBanner(
    notice: ConnectionArchiveNotice,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val baseColors = if (notice.urgent) {
        listOf(Color(0xFF5B21B6), Color(0xFF2563EB))
    } else {
        listOf(Color(0xFF4C1D95).copy(alpha = 0.92f), Color(0xFF1E3A8A).copy(alpha = 0.88f))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(baseColors), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notice.headline,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = notice.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
