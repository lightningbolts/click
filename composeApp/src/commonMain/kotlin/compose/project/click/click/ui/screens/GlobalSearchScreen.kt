package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.ui.theme.*
import compose.project.click.click.viewmodel.GlobalSearchViewModel
import compose.project.click.click.viewmodel.GlobalSearchResults
import compose.project.click.click.viewmodel.LocationSearchResult
import compose.project.click.click.viewmodel.MessageSearchResult
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Full-screen global search — unified results for People, Messages, and Locations.
 * Displayed as a route (no bottom nav bar) when the nav bar Search button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    userId: String,
    onNavigateToChat: (connectionId: String) -> Unit,
    onNavigateToMap: () -> Unit,
    onBack: () -> Unit,
    viewModel: GlobalSearchViewModel = viewModel { GlobalSearchViewModel() }
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Auto-focus the search field when the screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Clean up search state when leaving
    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            // ── Search Bar Header ───────────────────────────────────────────
            Surface(
                color = SurfaceDark,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = { viewModel.clear(); onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Rounded search text field container
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        // Search text field
                        TextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        value = query,
                        onValueChange = { viewModel.search(it) },
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search people, messages, places…",
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PrimaryBlue,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                    } // end Surface wrapper around TextField
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Loading indicator
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryBlue
                    )
                }

                // Empty state when no query entered
                query.isBlank() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search for people, messages,\nor locations you've visited",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // No results
                results.isEmpty -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No results for \"$query\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                // Show unified results list
                else -> {
                    SearchResultsList(
                        results = results,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToMap = onNavigateToMap
                    )
                }
            }
        }
    }
}

// ── Results List ──────────────────────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    results: GlobalSearchResults,
    onNavigateToChat: (String) -> Unit,
    onNavigateToMap: () -> Unit
) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(GradientTextStart, GradientTextEnd)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 24.dp)
    ) {
        // ── People section ─────────────────────────────────────────────────────
        if (results.people.isNotEmpty()) {
            item {
                SearchSectionHeader(label = "People", brush = gradientBrush)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark.copy(alpha = 0.6f)
                ) {
                    Column {
                        results.people.forEachIndexed { index, chatDetails ->
                            PersonSearchRow(
                                chatDetails = chatDetails,
                                onClick = { onNavigateToChat(chatDetails.connection.id) }
                            )
                            if (index < results.people.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Messages section ───────────────────────────────────────────────────
        if (results.messages.isNotEmpty()) {
            item {
                SearchSectionHeader(label = "Messages", brush = gradientBrush)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark.copy(alpha = 0.6f)
                ) {
                    Column {
                        results.messages.forEachIndexed { index, msgResult ->
                            MessageSearchRow(
                                result = msgResult,
                                onClick = { onNavigateToChat(msgResult.connectionId) }
                            )
                            if (index < results.messages.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Locations section ──────────────────────────────────────────────────
        if (results.locations.isNotEmpty()) {
            item {
                SearchSectionHeader(label = "Locations", brush = gradientBrush)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark.copy(alpha = 0.6f)
                ) {
                    Column {
                        results.locations.forEachIndexed { index, locResult ->
                            LocationSearchRow(
                                result = locResult,
                                onClick = { onNavigateToMap() }
                            )
                            if (index < results.locations.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Section Header ─────────────────────────────────────────────────────────────

@Composable
private fun SearchSectionHeader(label: String, brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                brush = brush
            )
        )
    }
}

// ── Person Row ─────────────────────────────────────────────────────────────────

@Composable
private fun PersonSearchRow(
    chatDetails: ChatWithDetails,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initials (gradient)
        val initials = chatDetails.otherUser.name
            ?.split(" ")
            ?.take(2)
            ?.mapNotNull { it.firstOrNull()?.uppercase() }
            ?.joinToString("") ?: "?"
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatDetails.otherUser.name ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            chatDetails.connection.semantic_location?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Message Row ─────────────────────────────────────────────────────────────────

@Composable
private fun MessageSearchRow(
    result: MessageSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Message icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PrimaryBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = LightBlue,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Chat name
            Text(
                text = result.chatName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            // Message snippet (truncated)
            Text(
                text = result.message.content,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            // Timestamp
            Text(
                text = formatSearchTime(result.message.timeCreated),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Location Row ─────────────────────────────────────────────────────────────────

@Composable
private fun LocationSearchRow(
    result: LocationSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Location pin icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DeepBlue.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = LightBlue,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.location,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            val countLabel = if (result.connectionCount == 1) "1 connection" else "${result.connectionCount} connections"
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatSearchTime(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    val hour = dateTime.hour
    val minute = dateTime.minute.toString().padStart(2, '0')
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    return "$displayHour:$minute $amPm"
}
