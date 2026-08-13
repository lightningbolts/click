@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.ui.components.PERSONALITY_REQUIRED_TAG_COUNT // pragma: allowlist secret
import compose.project.click.click.ui.components.PersonalityEditor // pragma: allowlist secret
import compose.project.click.click.ui.components.canonicalizePersonalityTags // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

@Composable
fun PersonalityTaggingScreen(
    onTagsSelected: (List<String>) -> Unit,
    initialTags: List<String> = emptyList(),
) {
    var selectedTags by remember {
        mutableStateOf(canonicalizePersonalityTags(initialTags))
    }
    val scrollState = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val ready = selectedTags.size == PERSONALITY_REQUIRED_TAG_COUNT

    Surface(
        modifier = Modifier.fillMaxSize().testTag("onboarding-personality"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "How do people describe you?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick exactly $PERSONALITY_REQUIRED_TAG_COUNT traits so connections get a feel for your vibe.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${selectedTags.size} of $PERSONALITY_REQUIRED_TAG_COUNT selected",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            PersonalityEditor(
                selectedTags = selectedTags,
                onSelectedTagsChange = { selectedTags = it },
            )

            Spacer(modifier = Modifier.height(28.dp))

            val tagStyle = LocalPlatformStyle.current
            Button(
                onClick = { onTagsSelected(selectedTags) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(if (tagStyle.isIOS) 14.dp else 28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation =
                    if (tagStyle.isIOS) {
                        ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                    } else {
                        ButtonDefaults.buttonElevation()
                    },
                enabled = ready,
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
