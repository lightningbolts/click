package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.DeepBlue
import compose.project.click.click.ui.theme.GradientTextEnd
import compose.project.click.click.ui.theme.GradientTextStart
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.SurfaceDark
import compose.project.click.click.viewmodel.GlobalSearchViewModel
import compose.project.click.click.viewmodel.SearchResult
import compose.project.click.click.viewmodel.SearchResultCategory
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator

/**
 * Full-screen global search — unified results across connections, archives, cliques,
 * interests, memory context, intents, messages, and locations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    userId: String,
    onNavigateToChat: (connectionId: String) -> Unit,
    onNavigateToMap: () -> Unit,
    viewModel: GlobalSearchViewModel = viewModel { GlobalSearchViewModel() },
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val visibleCategories by viewModel.visibleCategories.collectAsState()

    val visibleResults = remember(results, visibleCategories) {
        results.visible(visibleCategories)
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Surface(
                color = SurfaceDark,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.08f),
                        ) {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                value = query,
                                onValueChange = { viewModel.search(it, userId) },
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        "Search people, places, interests, intents…",
                                        color = Color.White.copy(alpha = 0.4f),
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = PrimaryBlue,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (cat in SearchResultCategory.entries) {
                            val selected = cat in visibleCategories
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleCategory(cat) },
                                label = { Text(categoryLabel(cat)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue.copy(alpha = 0.35f),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    labelColor = Color.White.copy(alpha = 0.85f),
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                isSearching -> {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryBlue,
                    )
                }

                query.isBlank() -> {
                    EmptySearchHint(
                        modifier = Modifier.align(Alignment.Center),
                        icon = Icons.Default.Search,
                        body = "Search for people, cliques, interests,\nintents, messages, or places you've visited",
                    )
                }

                results.isEmpty -> {
                    EmptySearchHint(
                        modifier = Modifier.align(Alignment.Center),
                        icon = Icons.Default.SearchOff,
                        body = "No results for \"$query\"",
                        dimmed = false,
                    )
                }

                visibleResults.isEmpty() -> {
                    EmptySearchHint(
                        modifier = Modifier.align(Alignment.Center),
                        icon = Icons.Default.SearchOff,
                        body = "No results match the selected filters.\nTry enabling another tab above.",
                        dimmed = false,
                    )
                }

                else -> {
                    UnifiedSearchResultsList(
                        results = visibleResults,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToMap = onNavigateToMap,
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(cat: SearchResultCategory): String =
    when (cat) {
        SearchResultCategory.Active -> "Active"
        SearchResultCategory.Archived -> "Archived"
        SearchResultCategory.Cliques -> "Cliques"
        SearchResultCategory.Nearby -> "Nearby"
    }

@Composable
private fun EmptySearchHint(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    body: String,
    dimmed: Boolean = true,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White.copy(if (dimmed) 0.2f else 0.35f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(if (dimmed) 0.4f else 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun UnifiedSearchResultsList(
    results: List<SearchResult>,
    onNavigateToChat: (String) -> Unit,
    onNavigateToMap: () -> Unit,
) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(GradientTextStart, GradientTextEnd),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchSectionHeader(label = "Results", brush = gradientBrush)
        }
        items(results, key = { searchResultStableKey(it) }) { row ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark.copy(alpha = 0.6f),
            ) {
                SearchResultRow(
                    result = row,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToMap = onNavigateToMap,
                )
            }
        }
    }
}

private fun searchResultStableKey(r: SearchResult): String =
    when (r) {
        is SearchResult.ActiveConnection -> "a:${r.details.connection.id}"
        is SearchResult.ArchivedConnection -> "ar:${r.details.connection.id}"
        is SearchResult.Clique -> "g:${r.details.connection.id}"
        is SearchResult.IntentMatch -> "i:${r.details.connection.id}:${r.intentLabel}"
        is SearchResult.InterestMatch -> "t:${r.details.connection.id}:${r.matchedTags.joinToString()}"
        is SearchResult.MemoryContextMatch -> "m:${r.details.connection.id}"
        is SearchResult.MessageHit -> "msg:${r.result.message.id}"
        is SearchResult.LocationBucket -> "loc:${r.result.location}"
    }

@Composable
private fun SearchSectionHeader(label: String, brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                brush = brush,
            ),
        )
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    onNavigateToChat: (String) -> Unit,
    onNavigateToMap: () -> Unit,
) {
    val archivedLook = when (result) {
        is SearchResult.ArchivedConnection -> true
        is SearchResult.IntentMatch -> result.isArchivedChannel
        is SearchResult.InterestMatch -> result.isArchivedChannel
        is SearchResult.MemoryContextMatch -> result.isArchivedChannel
        else -> false
    }
    val alpha = if (archivedLook) 0.7f else 1f
    Column(Modifier.alpha(alpha)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    when (result) {
                        is SearchResult.LocationBucket -> onNavigateToMap()
                        is SearchResult.MessageHit -> onNavigateToChat(result.result.connectionId)
                        is SearchResult.ActiveConnection -> onNavigateToChat(result.details.connection.id)
                        is SearchResult.ArchivedConnection -> onNavigateToChat(result.details.connection.id)
                        is SearchResult.Clique -> onNavigateToChat(result.details.connection.id)
                        is SearchResult.IntentMatch -> onNavigateToChat(result.details.connection.id)
                        is SearchResult.InterestMatch -> onNavigateToChat(result.details.connection.id)
                        is SearchResult.MemoryContextMatch -> onNavigateToChat(result.details.connection.id)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (result) {
                is SearchResult.MessageHit -> MessageLeadingIcon()
                is SearchResult.LocationBucket -> LocationLeadingIcon()
                is SearchResult.ActiveConnection -> PersonLeadingAvatar(result.details)
                is SearchResult.ArchivedConnection -> PersonLeadingAvatar(result.details)
                is SearchResult.Clique -> PersonLeadingAvatar(result.details)
                is SearchResult.IntentMatch -> PersonLeadingAvatar(result.details)
                is SearchResult.InterestMatch -> PersonLeadingAvatar(result.details)
                is SearchResult.MemoryContextMatch -> PersonLeadingAvatar(result.details)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BadgeRow(result)
                Spacer(Modifier.height(4.dp))
                TitleAndSubtitle(result)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BadgeRow(result: SearchResult) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (result) {
            is SearchResult.ArchivedConnection -> TinyBadge("Archived")
            is SearchResult.Clique -> TinyBadge("Clique")
            is SearchResult.IntentMatch -> TinyBadge("Intent")
            is SearchResult.InterestMatch -> TinyBadge("Interest")
            is SearchResult.MemoryContextMatch -> TinyBadge("Context")
            is SearchResult.MessageHit -> TinyBadge("Message")
            is SearchResult.LocationBucket -> TinyBadge("Place")
            is SearchResult.ActiveConnection -> {}
        }
    }
}

@Composable
private fun TinyBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.1f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TitleAndSubtitle(result: SearchResult) {
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
    )
    val subtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = Color.White.copy(alpha = 0.55f),
    )
    when (result) {
        is SearchResult.ActiveConnection -> {
            Text(result.details.otherUser.name ?: "Unknown", style = titleStyle)
            result.subtitle?.let { Text(it, style = subtitleStyle) }
        }
        is SearchResult.ArchivedConnection -> {
            Text(result.details.otherUser.name ?: "Unknown", style = titleStyle)
            result.subtitle?.let { Text(it, style = subtitleStyle) }
        }
        is SearchResult.Clique -> {
            Text(result.details.groupClique?.name ?: "Clique", style = titleStyle)
            Text("Group chat", style = subtitleStyle)
        }
        is SearchResult.IntentMatch -> {
            Text(result.details.otherUser.name ?: "Unknown", style = titleStyle)
            Text(
                text = "Looking for ${result.intentLabel}" +
                    (result.intentTimeframe?.let { " · $it" } ?: ""),
                style = subtitleStyle,
            )
        }
        is SearchResult.InterestMatch -> {
            Text(result.details.otherUser.name ?: "Unknown", style = titleStyle)
            Text("Shared: ${result.matchedTags.joinToString(", ")}", style = subtitleStyle)
        }
        is SearchResult.MemoryContextMatch -> {
            Text(result.details.otherUser.name ?: "Unknown", style = titleStyle)
            Text(result.matchLabel, style = subtitleStyle)
        }
        is SearchResult.MessageHit -> {
            Text(result.result.chatName, style = titleStyle)
            Text(
                text = result.result.message.content,
                style = subtitleStyle,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = formatSearchTime(result.result.message.timeCreated),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
        is SearchResult.LocationBucket -> {
            Text(result.result.location, style = titleStyle)
            val countLabel = if (result.result.connectionCount == 1) {
                "1 connection"
            } else {
                "${result.result.connectionCount} connections"
            }
            Text(countLabel, style = subtitleStyle)
        }
    }
}

@Composable
private fun PersonLeadingAvatar(details: ChatWithDetails) {
    val initials = details.otherUser.name
        ?.split(" ")
        ?.take(2)
        ?.mapNotNull { it.firstOrNull()?.uppercase() }
        ?.joinToString("") ?: "?"
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MessageLeadingIcon() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryBlue.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            tint = LightBlue,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LocationLeadingIcon() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeepBlue.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = LightBlue,
            modifier = Modifier.size(22.dp),
        )
    }
}

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
