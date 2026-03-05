package compose.project.click.click.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

/**
 * Interest tagging onboarding screen.
 * Shows a grid of interest chips; user selects 3-7 to populate users.tags[].
 * This data powers the B2B "Tribe Analysis" analytics layer.
 */

private data class InterestCategory(
    val emoji: String,
    val label: String,
    val subcategories: List<String>
)

private val INTEREST_CATEGORIES = listOf(
    InterestCategory("🎵", "Music", listOf("Live Shows", "DJing", "Producing", "Guitar", "Piano", "Singing", "Alto Sax", "Tenor Sax", "Drums", "Violin", "Bass", "Songwriting")),
    InterestCategory("🎼", "Instruments", listOf("Alto Sax", "Tenor Sax", "Trumpet", "Clarinet", "Cello", "Flute", "Ukulele", "Synth", "Beat Making")),
    InterestCategory("🥾", "Hiking", listOf("Day Hikes", "Backpacking", "Trail Running", "Rock Climbing", "Scrambling", "Nature Walks")),
    InterestCategory("☕", "Coffee", listOf("Espresso", "Pour Over", "Cafe Hopping", "Latte Art", "Home Brewing")),
    InterestCategory("🎮", "Gaming", listOf("PC", "Console", "Indie", "Board Games", "VR", "Competitive", "Co-op", "RPG", "Strategy")),
    InterestCategory("📚", "Reading", listOf("Fiction", "Non-Fiction", "Sci-Fi", "Fantasy", "Book Clubs", "Poetry")),
    InterestCategory("💪", "Fitness", listOf("Gym", "Yoga", "CrossFit", "Running", "Swimming", "Martial Arts", "Pilates", "Cycling")),
    InterestCategory("💻", "Tech", listOf("AI/ML", "Web Dev", "Mobile Dev", "Cybersecurity", "Hardware", "Open Source", "Cloud", "Data Science")),
    InterestCategory("🎨", "Art", listOf("Painting", "Sketching", "Digital Art", "Sculpture", "Ceramics", "Street Art", "Calligraphy", "Graphic Design")),
    InterestCategory("🎬", "Film", listOf("Indie Film", "Horror", "Documentaries", "Animation", "Film Making")),
    InterestCategory("🍕", "Food", listOf("Cooking", "Baking", "Food Trucks", "Fine Dining", "Vegan", "BBQ", "Sushi", "Meal Prep")),
    InterestCategory("✈️", "Travel", listOf("Backpacking", "Road Trips", "City Breaks", "Solo Travel", "Camping", "Digital Nomad", "Hostels")),
    InterestCategory("👨‍💻", "Coding", listOf("Python", "JavaScript", "Rust", "Hackathons", "Side Projects", "Kotlin", "TypeScript", "Game Dev")),
    InterestCategory("⚽", "Sports", listOf("Basketball", "Soccer", "Baseball", "Football", "Tennis", "Volleyball", "Skiing", "Surfing")),
    InterestCategory("🏈", "Team Sports", listOf("Baseball", "Football", "Softball", "Flag Football", "Rugby", "Ultimate Frisbee")),
    InterestCategory("🏃", "Outdoor Sports", listOf("Running", "Cycling", "Triathlon", "Climbing", "Skiing", "Snowboarding", "Surfing")),
    InterestCategory("🤝", "Volunteering", listOf("Environment", "Education", "Community", "Animal Welfare", "Mentoring")),
    InterestCategory("🚀", "Startups", listOf("Founding", "VC/Finance", "Product", "Growth", "Social Impact")),
    InterestCategory("📸", "Photography", listOf("Street", "Portrait", "Landscape", "Film Photography", "Drone", "Concert Photography", "Editing")),
    InterestCategory("🧘", "Wellness", listOf("Meditation", "Mindfulness", "Breathwork", "Journaling", "Mental Health")),
    InterestCategory("🗣️", "Languages", listOf("Spanish", "French", "Mandarin", "Japanese", "Korean", "Language Exchange")),
    InterestCategory("🎭", "Performing Arts", listOf("Theater", "Improv", "Acting", "Stand-up Comedy", "Dance")),
    InterestCategory("🐶", "Animals", listOf("Dogs", "Cats", "Birds", "Animal Rescue", "Pet Training")),
    InterestCategory("🧩", "Puzzles & Strategy", listOf("Chess", "Sudoku", "Escape Rooms", "Crosswords", "Go"))
)

private const val MIN_TAGS = 3
private const val MAX_TAGS = 12

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestTaggingScreen(
    onTagsSelected: (List<String>) -> Unit,
    onSkip: () -> Unit,
    canSkip: Boolean = true
) {
    val selectedTags = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    var customInterestInput by remember { mutableStateOf("") }

    val predefinedTags = remember {
        INTEREST_CATEGORIES.flatMap { category ->
            listOf(category.label) + category.subcategories
        }.map { it.lowercase() }.toSet()
    }
    val customSelectedTags = selectedTags.filter { it.lowercase() !in predefinedTags }

    fun toggleTag(tag: String) {
        if (tag in selectedTags) {
            selectedTags.remove(tag)
        } else if (selectedTags.size < MAX_TAGS) {
            selectedTags.add(tag)
        }
    }

    fun addCustomInterest() {
        val raw = customInterestInput.trim()
        if (raw.isEmpty()) return
        if (selectedTags.size >= MAX_TAGS) return
        if (selectedTags.none { it.equals(raw, ignoreCase = true) }) {
            selectedTags.add(raw)
        }
        customInterestInput = ""
    }

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

            INTEREST_CATEGORIES.forEach { category ->
                val isCategorySelected = category.label in selectedTags
                val hasSelectedSubs = category.subcategories.any { it in selectedTags }
                val isExpanded = expandedCategory == category.label
                val borderColor by animateColorAsState(
                    targetValue = when {
                        isCategorySelected -> PrimaryBlue
                        hasSelectedSubs -> PrimaryBlue.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleTag(category.label) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCategorySelected) PrimaryBlue.copy(alpha = 0.14f) else Color.Transparent,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = category.emoji)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.label,
                            color = if (isCategorySelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isCategorySelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        IconButton(onClick = {
                            expandedCategory = if (isExpanded) null else category.label
                        }) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        if (isCategorySelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        category.subcategories.forEach { sub ->
                            val isSubSelected = sub in selectedTags
                            FilterChip(
                                selected = isSubSelected,
                                onClick = { toggleTag(sub) },
                                label = { Text(sub, fontSize = 12.sp) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSubSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    selectedContainerColor = PrimaryBlue.copy(alpha = 0.18f),
                                    selectedLabelColor = PrimaryBlue,
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Custom interests",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customInterestInput,
                    onValueChange = { customInterestInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add your own interest") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                FilledTonalButton(
                    onClick = { addCustomInterest() },
                    enabled = customInterestInput.trim().isNotEmpty() && selectedTags.size < MAX_TAGS,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (customSelectedTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customSelectedTags.forEach { custom ->
                        AssistChip(
                            onClick = { selectedTags.remove(custom) },
                            label = { Text(custom) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    onTagsSelected(selectedTags.toList())
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
