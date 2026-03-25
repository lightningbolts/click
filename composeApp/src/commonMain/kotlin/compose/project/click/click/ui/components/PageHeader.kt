package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.project.click.click.ui.theme.LocalPlatformStyle

enum class HeaderDisplayMode {
    Large,
    Inline
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    displayMode: HeaderDisplayMode = if (navigationIcon != null) HeaderDisplayMode.Inline else HeaderDisplayMode.Large
) {
    val style = LocalPlatformStyle.current
    val radius = getAdaptiveCornerRadius()

    if (style.isIOS) {
        when (displayMode) {
            HeaderDisplayMode.Inline -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (actions != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        actions()
                    }
                }
            }
            HeaderDisplayMode.Large -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    if (actions != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            actions()
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(radius),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = getAdaptivePadding(), vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (actions != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    actions()
                }
            }
        }
    }
}
