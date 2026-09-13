@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDropdownMenu // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickLogoPulse // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSearchField // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalSheetOnDismissRequest // pragma: allowlist secret
import compose.project.click.click.ui.components.ProvideSheetSwipeDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberSheetScrollAtTop // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetImePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetPageBackground // pragma: allowlist secret
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot // pragma: allowlist secret
import compose.project.click.click.viewmodel.GlobalSearchViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.SearchChatOpenTarget // pragma: allowlist secret
import compose.project.click.click.viewmodel.SearchResultCategory // pragma: allowlist secret
import kotlinx.coroutines.delay

/**
 * In-context global search presented as a platform bottom sheet (replaces [GlobalSearchScreen] routing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchSheet(
    onDismissRequest: () -> Unit,
    userId: String,
    onNavigateToChat: (SearchChatOpenTarget) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToBeacon: (beaconId: String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: GlobalSearchViewModel = viewModel { GlobalSearchViewModel() },
) {
    val sheetColor = GlassSheetTokens.OledBlack()
    val onSheet = GlassSheetTokens.OnOled()
    MapBeaconSheetRoot(
        visible = true,
        onDismissRequest = onDismissRequest,
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = Color.Black.copy(alpha = ClickSheetDefaults.ScrimAlpha),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        appColorScheme = MaterialTheme.colorScheme,
        appTypography = MaterialTheme.typography,
        expandable = true,
        // UIKit dismiss + fill viewport so results occupy the sheet (no fixed empty band).
        useUiKitScrollHost = true,
        uiKitFillViewport = true,
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
    onNavigateToChat: (SearchChatOpenTarget) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToBeacon: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val visibleCategories by viewModel.visibleCategories.collectAsState()

    val visibleResults =
        remember(results, visibleCategories) {
            results.visible(visibleCategories)
        }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scrollAtTop = rememberSheetScrollAtTop(listState)
    val listBottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
    val pinnedCategories =
        listOf(
            SearchResultCategory.Active,
            SearchResultCategory.Cliques,
            SearchResultCategory.Nearby,
        )
    val overflowCategories =
        listOf(
            SearchResultCategory.Archived,
            SearchResultCategory.Beacons,
            SearchResultCategory.Intents,
        )
    var moreExpanded by remember { mutableStateOf(false) }
    val overflowSelected = overflowCategories.any { it in visibleCategories }
    val allFiltersSelected = visibleCategories.size == SearchResultCategory.entries.size

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    ProvideSheetSwipeDismiss(
        onDismissRequest = LocalSheetOnDismissRequest.current,
        scrollAtTop = scrollAtTop,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(sheetPageBackground())
                    .sheetImePadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = ClickSheetDefaults.ContentTopPaddingUnderGrabber,
                        bottom = 8.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickSearchField(
                value = query,
                onValueChange = { viewModel.search(it, userId) },
                placeholder = "Search people, places, beacons…",
                onSearch = { focusManager.clearFocus() },
                focusRequester = focusRequester,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClickChip(
                    label = "All",
                    selected = allFiltersSelected,
                    onClick = { viewModel.selectAllFilters() },
                    compact = true,
                )
                for (cat in pinnedCategories) {
                    ClickChip(
                        label = categoryLabel(cat),
                        selected = cat in visibleCategories,
                        onClick = { viewModel.toggleCategory(cat) },
                        compact = true,
                    )
                }
                Box {
                    ClickChip(
                        label = "More",
                        selected = overflowSelected,
                        onClick = { moreExpanded = true },
                        compact = true,
                    )
                    ClickDropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        items =
                            overflowCategories.map { cat ->
                                val selected = cat in visibleCategories
                                ClickMenuItem(
                                    label = if (selected) "${categoryLabel(cat)} · on" else categoryLabel(cat),
                                    onClick = { viewModel.toggleCategory(cat) },
                                )
                            },
                    )
                }
            }

            // Fill remaining viewport — no fixed 520dp empty band under short result lists.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                when {
                    isSearching && results.isEmpty -> {
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
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = listBottomPad),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            item(key = "header") { SearchSectionHeader(label = "Results") }
                            items(
                                items = visibleResults,
                                key = { searchResultStableKey(it) },
                                contentType = { "search_result" },
                            ) { row ->
                                SearchResultRow(
                                    result = row,
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
        }
    }
}
