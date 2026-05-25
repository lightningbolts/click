package compose.project.click.click.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.ui.components.ClickLogoPulse
import compose.project.click.click.ui.components.GlassAdaptiveBottomSheet
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.GlobalSearchViewModel
import compose.project.click.click.viewmodel.SearchResultCategory
import kotlinx.coroutines.delay

/**
 * In-context global search presented as a glass adaptive bottom sheet (replaces [GlobalSearchScreen] routing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchSheet(
    onDismissRequest: () -> Unit,
    userId: String,
    onNavigateToChat: (connectionId: String) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToBeacon: (beaconId: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: GlobalSearchViewModel = viewModel { GlobalSearchViewModel() },
) {
    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(sheetState) {
        delay(32)
        runCatching { sheetState.show() }
    }

    GlassAdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        adaptiveSheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        UnifiedSearchSheetContent(
            userId = userId,
            viewModel = viewModel,
            onNavigateToChat = onNavigateToChat,
            onNavigateToMap = onNavigateToMap,
            onNavigateToBeacon = onNavigateToBeacon,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedSearchSheetContent(
    userId: String,
    viewModel: GlobalSearchViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToBeacon: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
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
    val listBottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
    val allFiltersSelected = visibleCategories.size == SearchResultCategory.entries.size

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassSheetTokens.OledBlack)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                        "Search people, places, beacons, intents…",
                        color = GlassSheetTokens.OnOledMuted,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = GlassSheetTokens.OnOled,
                    unfocusedTextColor = GlassSheetTokens.OnOled,
                    cursorColor = PrimaryBlue,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = allFiltersSelected,
                onClick = { viewModel.selectAllFilters() },
                label = { Text("All") },
                colors = searchFilterChipColors(),
            )
            for (cat in SearchResultCategory.entries) {
                val selected = cat in visibleCategories
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleCategory(cat) },
                    label = { Text(categoryLabel(cat)) },
                    colors = searchFilterChipColors(),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.ime)
                .imePadding(),
        ) {
            when {
                isSearching -> {
                    ClickLogoPulse(
                        modifier = Modifier.align(Alignment.Center),
                        logoSize = 72.dp,
                    )
                }

                query.isBlank() -> {
                    EmptySearchHint(
                        modifier = Modifier.align(Alignment.Center),
                        icon = Icons.Default.Search,
                        body = "Search for people, cliques, beacons,\navailability intents, messages, or places",
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
                        body = "No results match the selected filters.\nTry another pill above.",
                        dimmed = false,
                    )
                }

                else -> {
                    UnifiedSearchResultsList(
                        results = visibleResults,
                        bottomPadding = listBottomPad,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToMap = onNavigateToMap,
                        onNavigateToBeacon = onNavigateToBeacon,
                        onNavigateToSettings = onNavigateToSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun searchFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PrimaryBlue.copy(alpha = 0.35f),
    selectedLabelColor = GlassSheetTokens.OnOled,
    containerColor = Color.White.copy(alpha = 0.06f),
    labelColor = GlassSheetTokens.OnOled.copy(alpha = 0.85f),
)
