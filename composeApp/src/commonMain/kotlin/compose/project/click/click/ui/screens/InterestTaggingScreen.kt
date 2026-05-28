package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.ui.components.INTEREST_ONBOARDING_MIN_TAGS
import compose.project.click.click.ui.components.InterestEditor
import compose.project.click.click.ui.theme.*
import compose.project.click.click.ui.theme.LocalPlatformStyle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

/**
 * Interest tagging onboarding screen.
 * Shows a grid of interest chips; user selects at least [INTEREST_ONBOARDING_MIN_TAGS] to persist.
 */
@Composable
fun InterestTaggingScreen(
    onTagsSelected: (List<String>) -> Unit,
    onSkip: () -> Unit = {},
    canSkip: Boolean = true,
) {
    var selectedTags by remember { mutableStateOf(emptyList<String>()) }
    val scrollState = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "What are you into?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick at least $INTEREST_ONBOARDING_MIN_TAGS interests to help find common ground with your connections",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            InterestEditor(
                selectedTags = selectedTags,
                onSelectedTagsChange = { selectedTags = it },
                minTags = INTEREST_ONBOARDING_MIN_TAGS,
                maxTags = null,
            )

            Spacer(modifier = Modifier.height(28.dp))

            val tagStyle = LocalPlatformStyle.current
            Button(
                onClick = { onTagsSelected(selectedTags) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(if (tagStyle.isIOS) 14.dp else 28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation = if (tagStyle.isIOS) {
                    ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                } else {
                    ButtonDefaults.buttonElevation()
                },
                enabled = selectedTags.size >= INTEREST_ONBOARDING_MIN_TAGS,
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            if (canSkip) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Skip for now",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
