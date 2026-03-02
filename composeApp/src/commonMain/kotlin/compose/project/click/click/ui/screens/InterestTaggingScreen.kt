package compose.project.click.click.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Interest tagging onboarding screen.
 * Shows a grid of interest chips; user selects 3-7 to populate users.tags[].
 * This data powers the B2B "Tribe Analysis" analytics layer.
 */

private val INTEREST_OPTIONS = listOf(
    "🎵 Music", "🥾 Hiking", "☕ Coffee", "🎮 Gaming",
    "📚 Reading", "💪 Fitness", "💻 Tech", "🎨 Art",
    "🎬 Film", "🍕 Food", "✈️ Travel", "👨‍💻 Coding",
    "⚽ Sports", "🤝 Volunteering", "🚀 Startups", "📸 Photography"
)

private const val MIN_TAGS = 3
private const val MAX_TAGS = 7

@Composable
fun InterestTaggingScreen(
    onTagsSelected: (List<String>) -> Unit,
    onSkip: () -> Unit,
    canSkip: Boolean = true
) {
    val selectedTags = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Text(
                text = "What are you into?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick $MIN_TAGS–$MAX_TAGS interests to help find common ground with your connections",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Selection count indicator
            Text(
                text = "${selectedTags.size} / $MAX_TAGS selected" +
                    if (selectedTags.size < MIN_TAGS) " (min $MIN_TAGS)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedTags.size >= MIN_TAGS) PrimaryBlue 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Interest chip grid — wrapped flow layout using rows
            val chunkedOptions = INTEREST_OPTIONS.chunked(2)
            chunkedOptions.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { interest ->
                        val isSelected = interest in selectedTags
                        val chipColor by animateColorAsState(
                            targetValue = if (isSelected) PrimaryBlue else Color.Transparent
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedTags.remove(interest)
                                } else if (selectedTags.size < MAX_TAGS) {
                                    selectedTags.add(interest)
                                }
                            },
                            label = {
                                Text(
                                    interest,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, borderColor),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = PrimaryBlue.copy(alpha = 0.2f),
                                selectedLabelColor = PrimaryBlue,
                                selectedLeadingIconColor = PrimaryBlue,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )
                    }
                    // Pad if odd number in last row
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    // Strip emoji prefix for clean tag storage
                    val cleanTags = selectedTags.map { it.substringAfter(" ").trim() }
                    onTagsSelected(cleanTags)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = selectedTags.size >= MIN_TAGS
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            // Skip button
            if (canSkip) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Skip for now",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
