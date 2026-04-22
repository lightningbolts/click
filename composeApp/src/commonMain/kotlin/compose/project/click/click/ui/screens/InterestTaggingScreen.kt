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
import compose.project.click.click.ui.theme.LocalPlatformStyle
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

/**
 * Interest tagging onboarding screen.
 * Shows a grid of interest chips; user selects at least 5 to persist to public.user_interests.
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

private const val MIN_TAGS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestTaggingScreen(
    onTagsSelected: (List<String>) -> Unit,
    onSkip: () -> Unit = {},
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
        } else {
            selectedTags.add(tag)
        }
    }

    fun addCustomInterest() {
        val raw = customInterestInput.trim()
        if (raw.isEmpty()) return
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
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "What are you into?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick at least $MIN_TAGS interests to help find common ground with your connections",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${selectedTags.size} selected" +
                    if (selectedTags.size < MIN_TAGS) " \u00b7 need ${MIN_TAGS - selectedTags.size} more" else " \u2713",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (selectedTags.size >= MIN_TAGS) PrimaryBlue
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            INTEREST_CATEGORIES.forEachIndexed { index, category ->
                val isCategorySelected = category.label in selectedTags
                val hasSelectedSubs = category.subcategories.any { it in selectedTags }
                val isExpanded = expandedCategory == category.label
                val borderColor by animateColorAsState(
                    targetValue = when {
                        isCategorySelected -> PrimaryBlue
                        hasSelectedSubs -> PrimaryBlue.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    }
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleTag(category.label) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCategorySelected) PrimaryBlue.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = category.emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCategorySelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isCategorySelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        IconButton(
                            onClick = { expandedCategory = if (isExpanded) null else category.label },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        if (isCategorySelected) {
                            Spacer(modifier = Modifier.width(4.dp))
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
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chipStyle = LocalPlatformStyle.current
                        category.subcategories.forEach { sub ->
                            val isSubSelected = sub in selectedTags
                            FilterChip(
                                selected = isSubSelected,
                                onClick = { toggleTag(sub) },
                                label = {
                                    Text(
                                        sub,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    if (chipStyle.isIOS) 0.5.dp else 1.dp,
                                    if (isSubSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (chipStyle.isIOS) 0.12f else 0.15f
                                    )
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    selectedContainerColor = PrimaryBlue.copy(alpha = if (chipStyle.isIOS) 0.12f else 0.14f),
                                    selectedLabelColor = PrimaryBlue,
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }

                if (index < INTEREST_CATEGORIES.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Custom interests",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customInterestInput,
                    onValueChange = { customInterestInput = it.take(25) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add your own interest") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                FilledTonalButton(
                    onClick = { addCustomInterest() },
                    enabled = customInterestInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            if (customSelectedTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val assistStyle = LocalPlatformStyle.current
                    customSelectedTags.forEach { custom ->
                        AssistChip(
                            onClick = { selectedTags.remove(custom) },
                            label = { Text(custom, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            border = if (assistStyle.isIOS) BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)) else null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            val tagStyle = LocalPlatformStyle.current
            Button(
                onClick = { onTagsSelected(selectedTags.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(if (tagStyle.isIOS) 14.dp else 28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation = if (tagStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                enabled = selectedTags.size >= MIN_TAGS
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            if (canSkip) {
                Spacer(modifier = Modifier.height(10.dp))
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
