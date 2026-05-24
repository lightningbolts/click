package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.BackgroundLight

@Composable
fun AppShimmerScreen(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (isDarkMode) BackgroundDark else BackgroundLight
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base),
    ) {
        ClickLogoPulse(modifier = Modifier.fillMaxSize())
    }
}
