package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.project.click.click.ui.components.getAdaptiveCornerRadius
import compose.project.click.click.ui.components.getAdaptivePadding

@Composable
fun PageHeader(title: String, subtitle: String? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        // Match app cards' rounding
        shape = RoundedCornerShape(getAdaptiveCornerRadius()),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        // Match app cards' padding
        Column(modifier = Modifier.padding(horizontal = getAdaptivePadding(), vertical = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
