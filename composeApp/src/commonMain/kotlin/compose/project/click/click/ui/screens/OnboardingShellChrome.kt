@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.clickBorderColor

@Composable
fun OnboardingShellChrome(
    stepIndex: Int,
    stepCount: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 4.dp, start = 8.dp, end = 16.dp)
                .testTag("onboarding-progress"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (canGoBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("onboarding-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Box(modifier = Modifier.size(48.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stepCount) { index ->
                val active = index <= stepIndex
                Box(
                    modifier =
                        Modifier
                            .size(if (index == stepIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    clickBorderColor()
                                },
                            ),
                )
            }
        }
        Box(modifier = Modifier.size(48.dp))
    }
}
