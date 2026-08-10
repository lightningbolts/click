package compose.project.click.click.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.ui.components.ClickLogoPulse
import compose.project.click.click.ui.components.ClickSheetDefaults
import compose.project.click.click.ui.components.GlassSheetTokens
import compose.project.click.click.ui.components.sheetImePadding
import compose.project.click.click.ui.components.sheetPageBackground
import compose.project.click.click.ui.sheet.MapBeaconSheetRoot
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.clickBorderColor
import compose.project.click.click.ui.theme.clickCardSurface
import compose.project.click.click.ui.theme.clickTextFieldTextStyle
import compose.project.click.click.viewmodel.GlobalSearchViewModel
import compose.project.click.click.viewmodel.SearchResultCategory
import kotlinx.coroutines.delay

/**
 * In-context global search presented as a platform bottom sheet (replaces [GlobalSearchScreen] routing).
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
        // UIKit scroll-host — same dismiss path as which-pin / view-event.
        useUiKitScrollHost = true,
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
            .fillMaxWidth()
            .background(sheetPageBackground())
            .sheetImePadding()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = ClickSheetDefaults.ContentTopPaddingUnderGrabber,
                bottom = 8.dp,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = clickCardSurface(),
            border = BorderStroke(1.dp, clickBorderColor()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = { viewModel.search(it, userId) },
                    singleLine = true,
                    textStyle = clickTextFieldTextStyle().copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(PrimaryBlue),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search people, places, beacons…",
                                    style = clickTextFieldTextStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }
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
                .fillMaxWidth(),
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
    selectedContainerColor = PrimaryBlue,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = clickCardSurface(),
    labelColor = MaterialTheme.colorScheme.onSurface,
)
