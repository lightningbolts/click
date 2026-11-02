@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.api.KlipyClient // pragma: allowlist secret
import compose.project.click.click.data.models.ChatGif // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Registers the platform's animated GIF / WebP decoders (Coil's default decoders show one frame). */
internal expect fun ComponentRegistry.Builder.addAnimatedImageDecoders()

/** An image loader that animates GIF / WebP; shares Coil's memory and disk caches setup. */
@Composable
internal fun rememberGifImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    return remember(context) {
        ImageLoader
            .Builder(context)
            .components { addAnimatedImageDecoders() }
            .build()
    }
}

/**
 * A GIF message: KLIPY media loaded straight from its URL, in a box sized from the sender's
 * `metadata.gif` dimensions so the timeline doesn't jump when it loads. Mirrors iOS
 * `GifMessageView` (click-ios `Click/Features/Chat/GifViews.swift`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GifMessageCard(
    gif: ChatGif,
    onLongPress: () -> Unit,
    enableContextMenu: Boolean,
    modifier: Modifier = Modifier,
) {
    val (widthDp, heightDp) = remember(gif) { gif.bubbleSizeDp() }
    val loader = rememberGifImageLoader()
    val context = LocalPlatformContext.current
    var attempt by remember(gif.url) { mutableIntStateOf(0) }
    var failed by remember(gif.url) { mutableStateOf(false) }
    var loaded by remember(gif.url) { mutableStateOf(false) }
    val request =
        remember(gif.url, attempt) {
            ImageRequest
                .Builder(context)
                .data(gif.url)
                .build()
        }
    Box(
        modifier =
            modifier
                .size(widthDp.dp, heightDp.dp)
                .clip(RoundedCornerShape(ChatBubbleTokens.cornerMain))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .combinedClickable(
                    enabled = enableContextMenu || failed,
                    onClick = {
                        if (failed) {
                            failed = false
                            attempt++
                        }
                    },
                    onLongClick = if (enableContextMenu) onLongPress else null,
                ).semantics { contentDescription = "GIF" },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            imageLoader = loader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        loaded = true
                        failed = false
                    }
                    is AsyncImagePainter.State.Error -> failed = true
                    else -> Unit
                }
            },
        )
        when {
            failed ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Couldn't load GIF. Tap to retry.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            !loaded -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * KLIPY GIF search. A blank query shows trending; results keep KLIPY's order (a KLIPY integration
 * requirement), and the search prompt reads "Search KLIPY" (required attribution). Mirrors iOS
 * `GifPickerSheet`.
 */
@Composable
internal fun GifPickerSheet(
    userId: String,
    onPick: (ChatGif) -> Unit,
    onDismiss: () -> Unit,
    client: KlipyClient = remember { KlipyClient() },
) {
    val customerId = remember(userId) { KlipyClient.customerId(userId) }
    val region = remember { Locale.current.region }
    var state by remember { mutableStateOf(GifPickerState()) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val loader = rememberGifImageLoader()
    val gridState = rememberLazyStaggeredGridState()

    suspend fun load(reset: Boolean) {
        val requested = state.query
        val page = state.nextPage(reset)
        state = state.loading()
        client
            .gifs(query = requested, page = page, customerId = customerId, region = region)
            .onSuccess { state = state.loaded(requested, it, reset) }
            .onFailure { e -> state = state.failed(requested, e.message ?: GifPickerState.LOAD_ERROR, reset) }
    }

    LaunchedEffect(state.query, reloadToken) {
        // Debounce typing; the first (trending) load starts at once.
        if (state.query.isNotEmpty()) delay(GifPickerState.SEARCH_DEBOUNCE_MS)
        load(reset = true)
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && state.canLoadMore()) load(reset = false)
    }

    ClickFormBottomSheet(onDismissRequest = onDismiss, fillBody = true) {
        ClickSheetChrome(
            title = "GIFs",
            modifier = Modifier.fillMaxWidth().padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { state = state.copy(query = it) },
                placeholder = { Text(GifPickerState.SEARCH_PROMPT) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.fillMaxWidth().height(420.dp).padding(top = 10.dp)) {
                val error = state.error
                when {
                    error != null && state.items.isEmpty() ->
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No GIFs", style = MaterialTheme.typography.titleMedium)
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = { reloadToken++ }) { Text("Try Again") }
                        }
                    state.items.isEmpty() && !state.isLoading ->
                        Text(
                            if (state.query.isBlank()) "No GIFs" else "No results for “${state.query.trim()}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    else ->
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalItemSpacing = 6.dp,
                            contentPadding = PaddingValues(bottom = 8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                GifPickerCell(
                                    item = item,
                                    loader = loader,
                                    onClick = {
                                        PlatformHapticsPolicy.lightImpact()
                                        val query = state.query
                                        scope.launch { client.triggerShare(item.slug, customerId, query) }
                                        onPick(item.toChatGif())
                                    },
                                )
                            }
                        }
                }
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Text(
                GifPickerState.ATTRIBUTION,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GifPickerCell(
    item: KlipyClient.Item,
    loader: ImageLoader,
    onClick: () -> Unit,
) {
    val ratio =
        item.preview.width.toFloat() /
            item.preview.height
                .coerceAtLeast(1)
                .toFloat()
    AsyncImage(
        model = item.preview.url,
        contentDescription = item.title,
        imageLoader = loader,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio.coerceIn(0.3f, 3f))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(onClick = onClick),
    )
}
